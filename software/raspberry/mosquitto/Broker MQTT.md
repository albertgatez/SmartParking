# Configuración del broker MQTT

El broker **Mosquitto** corre en la Raspberry Pi. El gateway (`raspberry/gateway.py`) publica ahí el estado de cada cajón, y cualquier otro cliente (un panel, una pantalla en la entrada del estacionamiento) puede suscribirse para recibirlo en tiempo real.

```
Arduino ──serial──▶ gateway.py ──MQTT──▶ Mosquitto (Raspberry Pi) ──▶ clientes suscritos
                        │
                        └──HTTPS──▶ Supabase
```

## 1. Instalación

En la Raspberry Pi (Raspberry Pi OS / Debian 12):

```bash
sudo apt update
sudo apt install -y mosquitto mosquitto-clients
sudo systemctl enable --now mosquitto
systemctl status mosquitto      # debe decir "active (running)"
```

`enable` hace que Mosquitto arranque solo cada vez que se enciende la Raspberry.

## 2. Configuración

Archivo `raspberry/mosquitto/smartparking.conf` (incluido), que se copia a `/etc/mosquitto/conf.d/`:

```conf
listener 1883
allow_anonymous false
password_file /etc/mosquitto/passwd
acl_file /etc/mosquitto/acl

persistence true
persistence_location /var/lib/mosquitto/

log_dest syslog
log_type error
log_type warning
log_type notice
connection_messages true
```

| Parámetro | Valor | Motivo |
|---|---|---|
| `listener` | 1883 | Puerto estándar de MQTT sin TLS; solo se usa dentro de la red local |
| `allow_anonymous` | `false` | Todo cliente debe autenticarse con usuario y contraseña |
| `password_file` | `/etc/mosquitto/passwd` | Contraseñas cifradas (hash), generadas con `mosquitto_passwd` |
| `acl_file` | `/etc/mosquitto/acl` | Qué puede publicar o leer cada usuario |
| `persistence` | `true` | Los mensajes retenidos sobreviven a un reinicio del broker |
| `connection_messages` | `true` | Registra en el log cada conexión y desconexión |

## 3. Usuarios y permisos

```bash
sudo mosquitto_passwd -c /etc/mosquitto/passwd gateway   # pide la contraseña
sudo mosquitto_passwd    /etc/mosquitto/passwd monitor
sudo cp smartparking.conf /etc/mosquitto/conf.d/
sudo cp acl /etc/mosquitto/acl
sudo chown mosquitto: /etc/mosquitto/passwd /etc/mosquitto/acl
sudo chmod 600 /etc/mosquitto/passwd
sudo systemctl restart mosquitto
```

Archivo `raspberry/mosquitto/acl` (incluido):

| Usuario | Permiso | Tópicos | Uso |
|---|---|---|---|
| `gateway` | Lectura y escritura | `smartparking/#` | El gateway publica los estados |
| `monitor` | Solo lectura | `smartparking/#` | Clientes que muestran la disponibilidad |

Las contraseñas **no se suben al repositorio**. El gateway las lee de su `.env`:

```
MQTT_BROKER=localhost
MQTT_PUERTO=1883
MQTT_USUARIO=gateway
MQTT_PASSWORD=<contraseña>
```

## 4. Tópicos

| Tópico | Mensaje | Publica | Retenido |
|---|---|---|---|
| `smartparking/cajon/<id>/estado` | `libre`, `ocupado` o `reservado` | Gateway | Sí |
| `smartparking/gateway/estado` | `online` u `offline` | Gateway (y el broker si el gateway se cae) | Sí |

`<id>` es el `id` del cajón en la tabla `Cajon` (1, 2 o 3). Como cada cajón pertenece a un solo estacionamiento, el id ya lo identifica. Si en el futuro hay varios estacionamientos con su propia Raspberry, se puede cambiar `MQTT_PREFIJO` en el `.env` (por ejemplo `smartparking/est1`) sin modificar el código.

El formato de los mensajes se detalla en el issue 9.2.

## 5. Clientes

| Cliente | Dónde corre | Usuario | Rol |
|---|---|---|---|
| `smartparking-gateway` | Raspberry Pi | `gateway` | Publica los estados de los cajones |
| `mosquitto_sub` | Raspberry o PC | `monitor` | Pruebas y monitoreo |

## 6. Verificación

```bash
# Ver todos los mensajes (otra terminal)
mosquitto_sub -h localhost -u monitor -P '<contraseña>' -t 'smartparking/#' -v

# Un cliente sin usuario debe ser rechazado
mosquitto_sub -h localhost -t 'smartparking/#'
# → Connection error: Connection Refused: not authorised.
```
