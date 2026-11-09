# SmartParking

## Descripción

SmartParking es un sistema inteligente para la gestión de estacionamientos en centros comerciales. Permite localizar, reservar y pagar un cajón desde una aplicación móvil, mientras sensores de ocupación instalados en cada cajón informan en tiempo real si está libre, ocupado o reservado.

El sistema integra sensores, un microcontrolador, un gateway con broker MQTT, un backend en la nube y una aplicación Android, de modo que el estado de los cajones y las reservas se gestionan de forma centralizada.

## Objetivo general

Desarrollar un sistema inteligente que permita gestionar en tiempo real la disponibilidad de espacios de estacionamiento, facilitando su consulta, reserva y pago mediante una aplicación móvil, con una arquitectura escalable y adaptable a diferentes estacionamientos.

## Mentor

Luis Ernesto Ayala Hernández

## Project Leader

Alberto de Jesús Carlos Muro

## Integrantes del equipo

| Integrante | Rol |
|------------|-----|
| Alberto de Jesús Carlos Muro | Project Leader, interfaz de la aplicación y corrección de errores |
| Santiago De Alba | Desarrollador; hardware, gateway, MQTT y pagos |
| Fernando de Jesús Arreola Reyes | Desarrollador; base de datos, documentación técnica y notificaciones |

## Estado actual

**Etapa / Versión:** Cierre de Construcción – construction-handover-v1.0

**Estado general:**
La construcción está terminada y el sistema funciona de punta a punta. El proyecto pasa a la etapa de Pruebas.

- El **Arduino** detecta la ocupación de 3 cajones con sensores HC-SR04, con histéresis y un filtro de tiempo para que una persona que pasa no marque el cajón como ocupado. Muestra el estado con LEDs (libre, ocupado y reservado) y hace parpadear el LED rojo si un cajón reservado se ocupa antes de que se escanee el QR.
- El **gateway** en la Raspberry Pi 4 corre como servicio: recibe los estados por serial, los publica en el broker **MQTT** (Mosquitto, con autenticación, QoS 1 y mensajes retenidos) y los guarda en **Supabase**. También avisa al Arduino cuando un cajón se reserva, se libera o el usuario escanea su QR.
- La **base de datos** tiene políticas RLS por usuario, impide reservas traslapadas, elimina las reservas sin pagar después de 5 minutos y guarda el desglose de cada pago.
- La **aplicación Android** permite registrarse, iniciar sesión, consultar estacionamientos en el mapa, reservar un cajón por horas, ver el detalle de la reserva activa, escanear el QR al llegar, extender el tiempo, administrar tarjetas guardadas, consultar el historial de pagos con su desglose y exportarlo a CSV, y usar modo oscuro.
- Los **pagos** se procesan con **Stripe** (modo prueba). El monto se calcula en el servidor (4 % de comisión en reservas, 5 % en extensiones y cupón de descuento) y el webhook confirma la reserva y registra el pago.
- Las **notificaciones push** (Firebase Cloud Messaging) avisan de la reserva confirmada, la extensión, el tiempo por terminar, el vehículo detectado y el cajón vacío, y se guardan en la pestaña Avisos.

**Última actualización:** construction-handover-v1.0

### Avance por paquete de trabajo

| WBS | Actividad | Estado |
|---|---|---|
| 1.2 | Entrevistas, análisis y priorización | Terminado |
| 3.2 – 3.3 | Diagramas y módulos de la arquitectura | Terminado |
| 4.1 – 4.3 | Modelo ER, diseño de tablas y relaciones | Terminado |
| 5.1 – 5.3 | Programación del Arduino, sensores y LEDs | Terminado |
| 6.1 – 6.2 | Diseño de interfaz y navegación | Terminado |
| 7.1 – 7.3 | Registro, flujo de reservas y navegación general | Terminado |
| 8.1 – 8.3 | Endpoints, seguridad de la BD y lógica de negocio | Terminado (RLS etapa 1) |
| 9.1 – 9.3 | Broker MQTT, formato de mensajes y pruebas de conexión | Terminado |
| 10.1 | Configuración de Stripe y flujo de pago | Terminado |
| 10.2 | Pruebas de transacciones | En pruebas |
| 11.1 – 11.2 | Firebase Cloud Messaging y envío de notificaciones push | Terminado |
| 12.1 – 12.2 | Cálculo e integración de la comisión en el backend | Terminado |
| 13.1 – 13.2 | Notificaciones de eventos y vista detallada de la reserva | Terminado |
| 14.1 – 14.2 | Historial de pagos y exportación a CSV | Terminado |
| 15.1 – 16.2 | Montaje del prototipo y pruebas de detección del sensor | Terminado (57/57 aciertos) |

### Pendientes para la etapa de Pruebas

| Pendiente | Responsable sugerido |
|---|---|
| Completar las pruebas de transacciones (extensión, cupón inválido, tarjeta rechazada, reserva expirada) | Alberto y Fernando |
| Revocar la llave secreta default de Supabase que quedó en el repositorio anterior | Santiago |
| Aplicar la RLS etapa 2 (la app consulta `horarios_ocupados` y deja de confirmar reservas) | Fernando |
| Retirar la Edge Function `crear-pago-reserva` anterior cuando todos usen la versión actual de la app | Equipo |
| Validar en el servidor el QR del cajón y el folio de discapacidad | Equipo |

## Arquitectura

| Componente | Tecnología |
|---|---|
| Sensado | Arduino Uno, 3 sensores HC-SR04, LEDs |
| Gateway | Raspberry Pi 4 (Debian 13), Python 3 (pyserial, paho-mqtt, requests), servicio systemd |
| Mensajería | Mosquitto 2.0 (MQTT con autenticación, QoS 1, mensajes retenidos) |
| Backend | Supabase: PostgreSQL, Auth, Edge Functions, pg_cron |
| Aplicación | Android (Java), OkHttp, Google Maps, ZXing |
| Pagos | Stripe (PaymentSheet y tarjetas guardadas, modo prueba) |
| Notificaciones | Firebase Cloud Messaging (API HTTP v1) |

## Estructura general del repositorio

- **design-baseline/:** Elementos técnicos del Laboratorio Abierto: Diseño (requerimientos, arquitectura, modelado de la aplicación y del prototipo, cuestionario, modelo de datos). Muestra el estado del proyecto antes de iniciar la construcción.

- **docs/technical/:** Documentación de lo construido:
  - `Application/`: endpoints, conexión con Supabase, lógica de negocio, flujos de reserva y navegación, pagos, métodos de pago, comisión, notificaciones y exportación del historial.
  - `Arduino/`: código, configuración de sensores y control de LEDs.
  - `BD/`: diccionario de datos y relaciones.
  - `MQTT/`: broker y formato de mensajes.

- **evidence/:** Resultados de las pruebas:
  - `hardware/`: fotografías del prototipo y pruebas de detección del sensor.
  - `raspberry/`: broker MQTT, conexión Arduino–Raspberry, pruebas de conexión MQTT y sincronización con el backend.
  - `supabase-bd/`: políticas RLS y lógica de negocio.

- **hardware/:** Firmware del Arduino (`smartparking_sensores/smartparking_sensores.ino`) y tabla de conexiones.

- **project-management/:** WBS, RACI, Risk Register, cronograma, Project Charter, metas SMART, stakeholders y revisión del Sprint II.

- **software/:**
  - `raspberry/`: gateway del Arduino, servicio systemd y configuración de Mosquitto.
  - `supabase/`: migraciones de la base de datos y Edge Functions.

- **src/android_app/:** Código fuente de la aplicación Android.

- **bom/, cad/, tests/:** Reservadas para la lista de materiales, diseños técnicos y planes de prueba de la siguiente etapa.

## Cómo consultar o reproducir el proyecto

> Las contraseñas y llaves **no están en el repositorio**. Cada integrante las configura en sus archivos locales (`.env`, `local.properties`, `google-services.json`) o en los secretos de Supabase.

### 1. Arduino

1. Armar el circuito según la tabla de conexiones de `hardware/README.md`.
2. Abrir `hardware/smartparking_sensores/smartparking_sensores.ino` en Arduino IDE.
3. Seleccionar **Arduino Uno** y el puerto, y presionar **Subir**.
4. En el **Monitor Serie** (9600 baudios) deben aparecer líneas como `Sensor 1: 0`.

### 2. Broker MQTT y gateway (Raspberry Pi)

1. Instalar y configurar Mosquitto siguiendo `software/raspberry/mosquitto/README.md`.
2. En la carpeta `software/raspberry/`:
   ```bash
   python3 -m venv venv
   venv/bin/pip install -r requirements.txt
   cp .env.example .env      # llenar SERIAL_PUERTO, SUPABASE_KEY, MQTT_USUARIO y MQTT_PASSWORD
   ```
3. Instalar el servicio siguiendo `software/raspberry/README.md` y revisar el log:
   ```bash
   journalctl -u smartparking-gateway -f
   ```
4. Ver los mensajes: `mosquitto_sub -h localhost -u monitor -P '<contraseña>' -t 'smartparking/#' -v`

### 3. Base de datos y Edge Functions (Supabase)

1. Aplicar las migraciones de `software/supabase/migrations/` en el orden indicado en `software/supabase/README.md`, desde el SQL Editor del panel.
2. Configurar los secretos `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `FIREBASE_SERVICE_ACCOUNT` y `FCM_WEBHOOK_SECRET` en **Edge Functions → Secrets**.
3. Desplegar las funciones de `software/supabase/functions/` desde el panel o con la CLI, ejecutada desde `software/`.

### 4. Aplicación Android

1. Abrir `src/android_app/` en Android Studio.
2. Agregar a `local.properties`:
   ```
   STRIPE_PUBLISHABLE_KEY=pk_test_...
   ```
3. Copiar el archivo `google-services.json` del proyecto de Firebase a `app/`.
4. Sincronizar Gradle y ejecutar en un dispositivo o emulador.
5. Para pagar en modo prueba, usar la tarjeta `4242 4242 4242 4242` con cualquier fecha futura y CVC.

## Versiones principales

**Design Baseline v1.0**

Línea base del diseño. Establece los elementos que se usaron como referencia durante la etapa de Construcción.

**sprint-1-plan-baseline-v1.0**

Actividades del Sprint I: actividades planificadas, objetivos, entregables y avance del periodo.

**sprint-2-review-v1.0**

Revisión del Sprint II: firmware del Arduino, gateway con broker MQTT, base de datos con políticas de seguridad y reglas de negocio, aplicación Android con registro, reservas y pagos, y la documentación de cada módulo.

**construction-handover-v1.0**

Cierre de la etapa de Construcción y entrega a Pruebas. Incluye el filtro de tiempo de los sensores, el gateway como servicio, la comisión calculada en el servidor, los métodos de pago, las notificaciones push, el historial con desglose y exportación a CSV, el modo oscuro, la evidencia de todas las pruebas y el Construction Handover Package.
