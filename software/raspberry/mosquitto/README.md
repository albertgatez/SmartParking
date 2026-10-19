# Broker MQTT (Mosquitto)

Configuración del broker que corre en la Raspberry Pi. El gateway publica aquí el estado de los cajones.

| Archivo | Destino en la Raspberry | Contenido |
|---|---|---|
| `smartparking.conf` | `/etc/mosquitto/conf.d/smartparking.conf` | Puerto 1883, sin acceso anónimo, persistencia y logs |
| `acl` | `/etc/mosquitto/acl` | Permisos por usuario |

El archivo de contraseñas (`/etc/mosquitto/passwd`) **no está en el repositorio**: se genera en cada Raspberry.

## Instalación

```bash
sudo apt update
sudo apt install -y mosquitto mosquitto-clients
sudo systemctl enable --now mosquitto

# Usuarios (pide la contraseña de cada uno; -c solo en el primero)
sudo mosquitto_passwd -c /etc/mosquitto/passwd gateway
sudo mosquitto_passwd /etc/mosquitto/passwd monitor

# Configuración (desde esta carpeta)
sudo cp smartparking.conf /etc/mosquitto/conf.d/
sudo cp acl /etc/mosquitto/acl
sudo chown mosquitto: /etc/mosquitto/passwd /etc/mosquitto/acl
sudo chmod 600 /etc/mosquitto/passwd
sudo systemctl restart mosquitto
systemctl status mosquitto      # debe decir "active (running)"
```

Después, poner el usuario y la contraseña de `gateway` en el `.env` del gateway (`MQTT_USUARIO` y `MQTT_PASSWORD`).

## Usuarios

| Usuario | Permiso | Uso |
|---|---|---|
| `gateway` | Lectura y escritura en `smartparking/#` | El gateway publica los estados |
| `monitor` | Solo lectura en `smartparking/#` | Monitoreo y pruebas |

## Tópicos

| Tópico | Mensaje | Retenido | QoS |
|---|---|---|---|
| `smartparking/cajon/<id>/estado` | `libre`, `ocupado` o `reservado` | Sí | 1 |
| `smartparking/gateway/estado` | `online` u `offline` | Sí | 1 |

## Verificación

```bash
# Sin usuario: debe rechazarse
mosquitto_sub -h localhost -t 'smartparking/#'
# → Connection Refused: not authorised.

# Con usuario: muestra los mensajes
mosquitto_sub -h localhost -u monitor -P '<contraseña>' -t 'smartparking/#' -v
```

## Problemas comunes

| Síntoma | Solución |
|---|---|
| Mosquitto no arranca tras copiar la configuración | `sudo journalctl -u mosquitto -n 30`; suele ser un error de permisos en `passwd` o `acl` |
| `not authorised` con el usuario correcto | Revisar la contraseña o recrearla con `mosquitto_passwd` (sin `-c`) |
| El gateway dice "Desconectado del broker" | Revisar `MQTT_USUARIO` y `MQTT_PASSWORD` en el `.env` del gateway |
| Se perdió el usuario `gateway` | Se usó `-c` al crear `monitor`; volver a crear ambos |
