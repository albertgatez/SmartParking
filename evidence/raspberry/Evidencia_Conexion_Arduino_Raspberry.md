# Evidencia — Conexión Arduino–Raspberry Pi

**Issue:** 15.1 Conexión Arduino–Raspberry Pi
**Fecha:** 06/11/2026

## Conexión física

| Elemento | Detalle |
|---|---|
| Raspberry Pi | Raspberry Pi 4, Debian 13 (trixie), Python 3.13.5 |
| Arduino | Arduino Uno compatible con chip USB-serial CH340 (id `1a86`) |
| Conexión | Cable USB del Arduino a un puerto USB de la Raspberry |
| Comunicación | Serial a 9600 baudios, protocolo `Sensor <n>: <estado>` y comandos `R`, `C`, `L` |

## 1. Puerto serial fijo

La Raspberry reconoce el Arduino y le asigna una ruta que no cambia entre reinicios:

```
$ ls -l /dev/serial/by-id/
usb-1a86_USB_Serial-if00-port0 -> ../../ttyUSB0
```

En el `.env` del gateway se usa la ruta completa de `by-id` en lugar de `/dev/ttyUSB0`:

```
SERIAL_PUERTO=/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0
```

## 2. Permiso de acceso al puerto

El usuario que ejecuta el gateway pertenece al grupo `dialout`:

```
$ groups
santiago adm dialout cdrom sudo audio video plugdev ...
```

## 3. Instalación del gateway

```
$ cd ~/smartparking
$ python3 -m venv venv
$ venv/bin/pip install -r requirements.txt     # pyserial, paho-mqtt, requests
$ cp .env.example .env && chmod 600 .env
```

## 4. Gateway como servicio

Se instaló `smartparking-gateway.service` (usuario `santiago`, carpeta `/home/santiago/smartparking`):

```
$ systemctl status smartparking-gateway --no-pager
● smartparking-gateway.service - SmartParking - Gateway Arduino / MQTT / Supabase
     Loaded: loaded (/etc/systemd/system/smartparking-gateway.service; enabled; preset: enabled)
     Active: active (running) since Fri 2026-11-06 18:05:00 CST
   Main PID: 1758 (python)
     CGroup: /system.slice/smartparking-gateway.service
             └─1758 /home/santiago/smartparking/venv/bin/python /home/santiago/smartparking/gateway.py
```

## 5. Prueba de arranque automático

Se reinició la Raspberry sin ejecutar nada después:

```
$ uptime -s
2026-11-06 18:04:53
```

| Evento | Hora |
|---|---|
| Encendido de la Raspberry | 18:04:53 |
| Gateway activo | 18:05:00 |

**Resultado:** El gateway arrancó solo 7 segundos después del encendido, se conectó al Arduino y al broker, y publicó el estado de los 3 cajones:

```
$ mosquitto_sub -h localhost -u monitor -P '<contraseña>' -t 'smartparking/#' -v
smartparking/gateway/estado online
smartparking/cajon/1/estado libre
smartparking/cajon/2/estado libre
smartparking/cajon/3/estado libre
smartparking/cajon/1/estado ocupado
smartparking/cajon/1/estado libre
smartparking/cajon/2/estado ocupado
smartparking/cajon/2/estado libre
```

## 6. Reconexión del Arduino

Al desconectar y volver a conectar el USB del Arduino, el gateway detectó la desconexión, reabrió el puerto y volvió a publicar los estados sin reiniciar el servicio.

## Criterios de terminado

| Criterio | Estado |
|---|---|
| El gateway lee las líneas `Sensor N: E` de los 3 cajones | Terminado |
| Mosquitto corriendo como servicio, sin acceso anónimo | Terminado (ver Evidencia_Broker_MQTT.md) |
| El gateway corre como servicio y arranca solo | Temrinado |
| Se reconecta solo al desconectar y conectar el USB | Temrinado |
| Puerto serial estable entre reinicios (`/dev/serial/by-id/…`) | Temrinado |
| Conexiones y configuración documentadas | Terminado `raspberry/README.md` y este documento |
