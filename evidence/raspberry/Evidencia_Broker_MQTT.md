# Evidencia — Configuración del broker MQTT

**Issue:** 9.1 Configuración del broker MQTT
**Fecha:** 06/11/2026
**Equipo:** Raspberry Pi 4 (`Santiago08`)

## Entorno

| Elemento | Valor |
|---|---|
| Sistema operativo | Debian GNU/Linux 13 (trixie) |
| Arquitectura | aarch64 (64 bits) |
| Broker | Mosquitto 2.0.21 |
| Configuración | `raspberry/mosquitto/smartparking.conf` y `raspberry/mosquitto/acl` |

```
$ cat /etc/os-release
PRETTY_NAME="Debian GNU/Linux 13 (trixie)"
VERSION_ID="13"

$ uname -m
aarch64

$ mosquitto -h | head -1
mosquitto version 2.0.21
```

## 1. Mosquitto corre como servicio

```
$ sudo systemctl enable --now mosquitto
$ systemctl status mosquitto --no-pager
● mosquitto.service - Mosquitto MQTT Broker
     Loaded: loaded (/usr/lib/systemd/system/mosquitto.service; enabled; preset: enabled)
     Active: active (running) since Fri 2026-11-06 14:30:02 CST
   Main PID: 33367 (mosquitto)
     CGroup: /system.slice/mosquitto.service
             └─33367 /usr/sbin/mosquitto -c /etc/mosquitto/mosquitto.conf
```

**Resultado:** El servicio está activo (`active (running)`) y habilitado (`enabled`), por lo que arranca solo al encender la Raspberry.

## 2. Sin acceso anónimo

Un cliente sin usuario ni contraseña intenta suscribirse:

```
$ mosquitto_sub -h localhost -t 'smartparking/#' -C 1 -W 3
Connection error: Connection Refused: not authorised.
```

**Resultado:** El broker rechaza la conexión.

## 3. Acceso con usuario y contraseña

Se crearon dos usuarios con `mosquitto_passwd` (contraseñas guardadas con hash en `/etc/mosquitto/passwd`, fuera del repositorio):

| Usuario | Permiso (ACL) | Uso |
|---|---|---|
| `gateway` | Lectura y escritura en `smartparking/#` | El gateway publica el estado de los cajones |
| `monitor` | Solo lectura en `smartparking/#` | Monitoreo y pruebas |

Publicación con `gateway` y recepción con `monitor`:

```
$ mosquitto_sub -h localhost -u monitor -P '<contraseña>' -t 'smartparking/#' -v -C 1 -W 10 & \
  sleep 1; mosquitto_pub -h localhost -u gateway -P '<contraseña>' -t smartparking/cajon/1/estado -m libre; wait
smartparking/cajon/1/estado libre
```

**Resultado:** El mensaje publicado por `gateway` llega al suscriptor `monitor`.

## 4. Seguridad

- Las contraseñas no están en el repositorio; el gateway las lee de su `.env`, que está en `.gitignore`.
- Las contraseñas usadas durante la instalación se cambiaron al terminar las pruebas.
- `/etc/mosquitto/passwd` y `/etc/mosquitto/acl` tienen permisos `600` y pertenecen al usuario `mosquitto`.

## Ajuste realizado

El `mosquitto.conf` principal de Debian ya define `persistence` y `persistence_location`. Se quitaron esas líneas de `smartparking.conf` para no duplicarlas, porque Mosquitto no arranca con valores repetidos.

## Criterios de terminado

| Criterio | Estado |
|---|---|
| Mosquitto instalado y corriendo como servicio en la Raspberry Pi | Terminado |
| Sin acceso anónimo (usuario y contraseña) | Terminado |
| Tópicos definidos y documentados | Terminado `docs/technical/MQRR/Broker_MQTT.md` |
| Configuración en el repositorio sin contraseñas | Terminado `raspberry/mosquitto/` |
