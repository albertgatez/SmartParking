# Gateway de la Raspberry Pi

Script que conecta el Arduino con el broker MQTT y con Supabase.

## Qué hace

1. Lee del Arduino (serial, 9600 baudios) las líneas `Sensor <n>: <estado>`.
2. Cuando un cajón cambia de estado:
   - Lo publica en MQTT en `smartparking/cajon/<id>/estado` (mensaje retenido, QoS 1).
   - Actualiza `estado_cajon` en la tabla `Cajon` de Supabase (`0` libre, `1` ocupado, `2` reservado).
3. Cada 5 s consulta en `Reserva` las reservas confirmadas que están en curso y manda al Arduino:
   - `R<n>` cuando empieza una reserva (LED amarillo).
   - `C<n>` cuando la persona que reservó ya escaneó su QR (`token_estacionamiento`). Si llega un auto a un cajón reservado sin ese escaneo, el LED rojo parpadea.
   - `L<n>` cuando termina la reserva.
4. Si se pierde la conexión con el Arduino, el broker o Internet, se reconecta solo y reintenta guardar los estados pendientes.

| Sensor del Arduino | Cajón (`Cajon.id`) |
|---|---|
| 1 | 1 |
| 2 | 2 |
| 3 | 3 |

| Código del Arduino | `estado_cajon` | Tópico MQTT |
|---|---|---|
| 0 | `0` | `libre` |
| 1 | `1` | `ocupado` |
| 2 | `2` | `reservado` |
| 3 | (no se guarda) | `error` |

El tópico `smartparking/gateway/estado` indica si el gateway está `online` u `offline`.

## Contenido de la carpeta

| Archivo | Para qué sirve |
|---|---|
| `gateway.py` | Puente Arduino ↔ MQTT ↔ Supabase |
| `requirements.txt` | Dependencias de Python |
| `.env.example` | Plantilla de configuración (copiar como `.env`) |
| `smartparking-gateway.service` | Servicio para que el gateway arranque solo |
| `mosquitto/` | Configuración del broker MQTT con autenticación (ver [mosquitto/README.md](mosquitto/README.md)) |

## Instalación

```bash
sudo apt install mosquitto mosquitto-clients python3-venv
mkdir -p ~/smartparking && cd ~/smartparking
# copiar aquí gateway.py, requirements.txt, .env.example y smartparking-gateway.service
python3 -m venv venv
venv/bin/pip install -r requirements.txt
cp .env.example .env      # llenar SUPABASE_KEY, MQTT_USUARIO y MQTT_PASSWORD
```

Probar manualmente:

```bash
venv/bin/python gateway.py
# en otra terminal, ver los mensajes MQTT:
mosquitto_sub -h localhost -u monitor -P '<contraseña>' -t 'smartparking/#' -v
```

Dejarlo como servicio (arranca solo al encender la Raspberry):

```bash
sudo cp smartparking-gateway.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now smartparking-gateway
journalctl -u smartparking-gateway -f
```

> Si el usuario de la Raspberry no es `pi`, cambia `User` y las rutas en el archivo `.service`.
> Si el puerto no es `/dev/ttyACM0`, revísalo con `ls /dev/tty*` y cámbialo en `.env`.

## Llave de Supabase

El gateway necesita **actualizar** `Cajon` y **leer** `Reserva`. Con RLS activado, la llave anónima no puede hacerlo, así que hay dos opciones:

- Usar la llave de servicio (`service_role`) **solo en el `.env` de la Raspberry**, nunca en el repositorio ni en la app.
- O crear políticas RLS específicas para el gateway.

El archivo `.env` está pensado para no subirse a GitHub.
