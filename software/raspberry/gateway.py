#!/usr/bin/env python3
"""
SmartParking - Gateway de la Raspberry Pi

Puente entre el Arduino, el broker MQTT y Supabase:

  Arduino --serial--> gateway --MQTT--> broker (smartparking/cajon/<id>/estado)
                         |
                         +--HTTPS--> Supabase: actualiza Cajon.estado_cajon
                         |
  Arduino <--R/L-------- +<--HTTPS-- Supabase: consulta reservas confirmadas vigentes

- Lee del Arduino las lineas "Sensor <n>: <estado>" (0 libre, 1 ocupado, 2 reservado, 3 error).
- Publica cada cambio en MQTT (mensaje retenido) y actualiza estado_cajon en la tabla Cajon.
- Cada pocos segundos revisa la tabla Reserva y manda R<n> / L<n> al Arduino
  cuando un cajon empieza o termina una reserva, y C<n> cuando la persona que
  reservo ya escaneo su QR (asi el LED rojo queda fijo en lugar de parpadear).
- Si se pierde el serial, el broker o Internet, sigue funcionando y se reconecta solo;
  los estados que no se pudieron guardar se reintentan al volver la conexion.

Configuracion: variables de entorno o archivo .env junto a este script (ver .env.example).
"""

import logging
import os
import re
import time
from datetime import datetime, timezone

import requests
import serial
import paho.mqtt.client as mqtt


# ======================================================================
# CONFIGURACION
# ======================================================================
def cargar_env(ruta):
    """Carga un archivo .env sencillo (CLAVE=valor) sin dependencias extra."""
    if not os.path.exists(ruta):
        return
    with open(ruta, encoding="utf-8") as f:
        for linea in f:
            linea = linea.strip()
            if not linea or linea.startswith("#") or "=" not in linea:
                continue
            clave, valor = linea.split("=", 1)
            os.environ.setdefault(clave.strip(), valor.strip().strip('"').strip("'"))


cargar_env(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env"))

SERIAL_PUERTO = os.getenv("SERIAL_PUERTO", "/dev/ttyACM0")
SERIAL_BAUDIOS = int(os.getenv("SERIAL_BAUDIOS", "9600"))

MQTT_BROKER = os.getenv("MQTT_BROKER", "localhost")
MQTT_PUERTO = int(os.getenv("MQTT_PUERTO", "1883"))
MQTT_USUARIO = os.getenv("MQTT_USUARIO", "")
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "")
MQTT_PREFIJO = os.getenv("MQTT_PREFIJO", "smartparking")

SUPABASE_URL = os.getenv("SUPABASE_URL", "https://kjbmwwbzdsvunzndeusf.supabase.co").rstrip("/")
SUPABASE_KEY = os.getenv("SUPABASE_KEY", "")

# Sensor del Arduino -> id del cajon en la tabla Cajon
MAPA_SENSOR_CAJON = {1: 1, 2: 2, 3: 3}

INTERVALO_RESERVAS_S = float(os.getenv("INTERVALO_RESERVAS_S", "5"))   # consulta de reservas
INTERVALO_REINTENTO_S = float(os.getenv("INTERVALO_REINTENTO_S", "5"))  # reintento de envios fallidos
TIMEOUT_HTTP_S = 5

# Codigos que manda el Arduino -> texto para MQTT y logs
ESTADOS_ARDUINO = {0: "libre", 1: "ocupado", 2: "reservado", 3: "error"}
# Cajon.estado_cajon es smallint en la BD: 0 libre, 1 ocupado, 2 reservado (mismos codigos del Arduino).
# El 3 (error) no se guarda en la BD.
CODIGO_BD = {"libre": 0, "ocupado": 1, "reservado": 2}

PATRON_LINEA = re.compile(r"Sensor\s+(\d+)\s*:\s*(\d)")

logging.basicConfig(
    level=os.getenv("LOG_NIVEL", "INFO"),
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("gateway")


# ======================================================================
# MQTT
# ======================================================================
def crear_cliente_mqtt():
    try:  # paho-mqtt 2.x
        cliente = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="smartparking-gateway")
    except AttributeError:  # paho-mqtt 1.x
        cliente = mqtt.Client(client_id="smartparking-gateway")

    if MQTT_USUARIO:
        cliente.username_pw_set(MQTT_USUARIO, MQTT_PASSWORD)

    tema_gateway = f"{MQTT_PREFIJO}/gateway/estado"
    # Si el gateway se desconecta de golpe, el broker publica "offline"
    cliente.will_set(tema_gateway, "offline", qos=1, retain=True)

    def al_conectar(c, userdata, flags, reason_code, properties=None):
        log.info("Conectado al broker MQTT %s:%s", MQTT_BROKER, MQTT_PUERTO)
        c.publish(tema_gateway, "online", qos=1, retain=True)

    def al_desconectar(c, userdata, *args):
        log.warning("Desconectado del broker MQTT; se reintentara automaticamente")

    cliente.on_connect = al_conectar
    cliente.on_disconnect = al_desconectar
    cliente.reconnect_delay_set(min_delay=1, max_delay=30)
    cliente.connect_async(MQTT_BROKER, MQTT_PUERTO, keepalive=60)
    cliente.loop_start()  # hilo que mantiene la conexion y reconecta
    return cliente


def publicar_estado(cliente, id_cajon, estado_texto):
    tema = f"{MQTT_PREFIJO}/cajon/{id_cajon}/estado"
    cliente.publish(tema, estado_texto, qos=1, retain=True)
    log.info("MQTT %s -> %s", tema, estado_texto)


# ======================================================================
# SUPABASE (API REST)
# ======================================================================
def encabezados():
    return {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }


def actualizar_cajon(id_cajon, estado_texto):
    """Actualiza Cajon.estado_cajon (como numero). Devuelve True si se guardo."""
    try:
        r = requests.patch(
            f"{SUPABASE_URL}/rest/v1/Cajon",
            params={"id": f"eq.{id_cajon}"},
            json={"estado_cajon": CODIGO_BD[estado_texto]},
            headers=encabezados(),
            timeout=TIMEOUT_HTTP_S,
        )
        if r.ok:
            log.info("Supabase: cajon %s = %s", id_cajon, estado_texto)
            return True
        log.error("Supabase respondio %s al actualizar cajon %s: %s", r.status_code, id_cajon, r.text[:200])
    except requests.RequestException as e:
        log.warning("Sin conexion con Supabase (%s); se reintentara", e.__class__.__name__)
    return False


def cajones_con_reserva_vigente():
    """Reservas confirmadas que estan en curso ahora mismo.
    Devuelve dos conjuntos de ids de cajon: (con reserva vigente, con llegada confirmada),
    donde "llegada confirmada" significa que la persona ya escaneo el QR (token_estacionamiento).
    Devuelve (None, None) si no se pudo consultar."""
    ahora = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    ids = ",".join(str(c) for c in MAPA_SENSOR_CAJON.values())
    try:
        r = requests.get(
            f"{SUPABASE_URL}/rest/v1/Reserva",
            params={
                "select": "id_cajon,token_estacionamiento",
                "id_cajon": f"in.({ids})",
                "confirmada": "is.true",
                "tiempo_inicio": f"lte.{ahora}",
                "tiempo_fin": f"gt.{ahora}",
            },
            headers=encabezados(),
            timeout=TIMEOUT_HTTP_S,
        )
        if r.ok:
            filas = r.json()
            vigentes = {f["id_cajon"] for f in filas}
            con_llegada = {f["id_cajon"] for f in filas if f.get("token_estacionamiento")}
            return vigentes, con_llegada
        log.error("Supabase respondio %s al consultar reservas: %s", r.status_code, r.text[:200])
    except (requests.RequestException, ValueError) as e:
        log.warning("No se pudieron consultar las reservas (%s)", e.__class__.__name__)
    return None, None


# ======================================================================
# SERIAL (ARDUINO)
# ======================================================================
def abrir_serial():
    """Intenta abrir el puerto hasta lograrlo."""
    while True:
        try:
            puerto = serial.Serial(SERIAL_PUERTO, SERIAL_BAUDIOS, timeout=0.1)
            time.sleep(2)  # el Arduino se reinicia al abrir el puerto
            puerto.reset_input_buffer()
            log.info("Arduino conectado en %s", SERIAL_PUERTO)
            return puerto
        except serial.SerialException as e:
            log.warning("No se pudo abrir %s (%s); reintento en 3 s", SERIAL_PUERTO, e)
            time.sleep(3)


def enviar_comando(puerto, comando):
    puerto.write(f"{comando}\n".encode())
    log.info("Arduino <- %s", comando)


# ======================================================================
# PROGRAMA PRINCIPAL
# ======================================================================
def main():
    if not SUPABASE_KEY:
        log.warning("SUPABASE_KEY no esta configurada: no se podra actualizar la base de datos")

    cliente = crear_cliente_mqtt()
    puerto = abrir_serial()

    cajon_a_sensor = {c: s for s, c in MAPA_SENSOR_CAJON.items()}
    ultimo_estado = {}            # id_cajon -> ultimo estado recibido del Arduino
    guardado_en_bd = {}           # id_cajon -> ultimo estado guardado con exito
    pendientes = {}               # id_cajon -> estado que falta guardar
    reservados_en_arduino = set() # cajones a los que ya se les mando R
    llegada_en_arduino = set()    # cajones a los que ya se les mando C (QR escaneado)
    ultima_consulta = 0.0
    proximo_intento_bd = 0.0

    while True:
        try:
            # ---- 1. Leer todo lo que haya mandado el Arduino ----
            while puerto.in_waiting:
                linea = puerto.readline().decode(errors="ignore").strip()
                coincidencia = PATRON_LINEA.search(linea)
                if not coincidencia:
                    continue
                sensor, codigo = int(coincidencia.group(1)), int(coincidencia.group(2))
                id_cajon = MAPA_SENSOR_CAJON.get(sensor)
                estado = ESTADOS_ARDUINO.get(codigo)
                if id_cajon is None or estado is None:
                    continue

                # El Arduino repite el estado cada pocos segundos: solo se actua si cambio
                if ultimo_estado.get(id_cajon) == estado:
                    continue
                ultimo_estado[id_cajon] = estado
                publicar_estado(cliente, id_cajon, estado)

                if estado == "error":
                    log.error("El sensor del cajon %s no responde", id_cajon)
                    continue  # la BD solo acepta libre / ocupado / reservado
                if guardado_en_bd.get(id_cajon) != estado:
                    pendientes[id_cajon] = estado

            ahora = time.monotonic()

            # ---- 2. Guardar en Supabase los cambios pendientes ----
            if pendientes and ahora >= proximo_intento_bd:
                for id_cajon, estado in list(pendientes.items()):
                    if actualizar_cajon(id_cajon, estado):
                        guardado_en_bd[id_cajon] = estado
                        pendientes.pop(id_cajon, None)
                    else:
                        proximo_intento_bd = ahora + INTERVALO_REINTENTO_S
                        break  # sin conexion: se reintenta mas tarde

            # ---- 3. Sincronizar reservas con los LEDs del Arduino ----
            if ahora - ultima_consulta >= INTERVALO_RESERVAS_S:
                ultima_consulta = ahora
                vigentes, con_llegada = cajones_con_reserva_vigente()
                if vigentes is not None:
                    # Reservas nuevas -> R (LED amarillo)
                    for id_cajon in vigentes - reservados_en_arduino:
                        enviar_comando(puerto, f"R{cajon_a_sensor[id_cajon]}")
                    # Reservas terminadas -> L
                    for id_cajon in reservados_en_arduino - vigentes:
                        enviar_comando(puerto, f"L{cajon_a_sensor[id_cajon]}")
                    # La persona que reservo ya escaneo su QR -> C (rojo fijo en vez de parpadeo)
                    for id_cajon in con_llegada - llegada_en_arduino:
                        enviar_comando(puerto, f"C{cajon_a_sensor[id_cajon]}")
                    reservados_en_arduino = set(vigentes)
                    llegada_en_arduino = set(con_llegada)

            time.sleep(0.05)

        except serial.SerialException as e:
            log.error("Se perdio la conexion con el Arduino (%s)", e)
            try:
                puerto.close()
            except Exception:
                pass
            puerto = abrir_serial()
            # El Arduino se reinicio: hay que volver a mandarle las reservas
            reservados_en_arduino = set()
            llegada_en_arduino = set()
            ultimo_estado.clear()
            ultima_consulta = 0.0

        except KeyboardInterrupt:
            log.info("Deteniendo gateway")
            cliente.publish(f"{MQTT_PREFIJO}/gateway/estado", "offline", qos=1, retain=True)
            cliente.loop_stop()
            puerto.close()
            break


if __name__ == "__main__":
    main()
