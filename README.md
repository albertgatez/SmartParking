# SmartParking

## Descripción

SmartParking es un sistema inteligente para la gestión de estacionamientos en centros comerciales. El proyecto busca facilitar la localización y reserva de espacios disponibles mediante sensores de ocupación, una aplicación móvil y servicios digitales para la gestión de reservas y pagos.

El sistema contempla la comunicación entre sensores, microcontroladores y un servidor central, permitiendo consultar el estado de los espacios y gestionar las reservas de manera centralizada.

## Objetivo general

Desarrollar un sistema inteligente que permita gestionar en tiempo real la disponibilidad de espacios de estacionamiento, facilitando su consulta, reserva y pago mediante una aplicación móvil, con una arquitectura escalable y adaptable a diferentes estacionamientos.

## Mentor

Luis Ernesto Ayala Hernández

## Project Leader

Alberto de Jesús Carlos Muro

## Integrantes del equipo

| Integrante | Rol |
|------------|-----|
| Alberto | Project Leader y encargado de bugs |
| Santiago | Desarrollador y encargado de hardware |
| Fernando | Desarrollador y encargado de bases de datos |

## Estado actual

**Sprint / Versión:** Sprint II – Construcción

**Estado general:**
El proyecto se encuentra en la etapa de construcción. Ya funciona el recorrido principal del sistema:

- El **Arduino** detecta la ocupación de 3 cajones con sensores HC-SR04 y muestra su estado con LEDs (libre, ocupado y reservado).
- El **gateway** en la Raspberry Pi recibe los estados por serial, los publica en el broker **MQTT** (Mosquitto, con autenticación) y los guarda en **Supabase**. También avisa al Arduino cuando un cajón se reserva o cuando el usuario escanea su QR.
- La **aplicación Android** permite registrarse, iniciar sesión, consultar estacionamientos en el mapa, reservar un cajón por horas, pagar con **Stripe** (modo prueba), escanear el QR al llegar y extender el tiempo.
- La **base de datos** tiene políticas de seguridad por usuario (RLS) y reglas de negocio en el servidor: no permite reservas traslapadas y elimina las reservas sin pagar después de 5 minutos.

**Última actualización:** sprint-2-review-v1.0

### Avance por paquete de trabajo

| WBS | Actividad | Estado |
|---|---|---|
| 1.2 | Entrevistas, análisis y priorización | Terminado |
| 3.2 – 3.3 | Diagramas y módulos de la arquitectura | Terminado |
| 4.1 – 4.3 | Modelo ER, diseño de tablas y relaciones | Terminado |
| 5.1 – 5.3 | Programación del Arduino, sensores y LEDs | Terminado |
| 6.1 – 6.2 | Diseño de interfaz y navegación | Terminado |
| 7.1 – 7.3 | Registro, flujo de reservas y navegación general | Terminado |
| 8.1 – 8.3 | Endpoints, seguridad de la BD y lógica de negocio | Terminado |
| 9.1 – 9.2 | Broker MQTT y formato de mensajes | Terminado |
| 9.3 | Pruebas de conexión MQTT | Pendiente |
| 10.1 | Configuración de Stripe y flujo de pago | Terminado |
| 10.2 | Pruebas de transacciones | Pendiente |
| 11.1 | Configuración de Firebase Cloud Messaging | Pendiente |
| 11.2 | Envío de notificaciones push | Pendiente |
| 12.1 | Cálculo del porcentaje de comisión | Terminado |
| 12.2 | Integración de la comisión en el backend | Pendiente |

### Pendientes para el Sprint III

| Issue | Pendiente |
|---|---|
| 9.3 | Ejecutar el plan de pruebas MQTT en la Raspberry Pi (3 cajones sin pérdida, reconexión en menos de 30 s) y registrar los resultados |
| 10.2 | Probar pagos exitosos, rechazados, cancelados y expirados con las tarjetas de prueba de Stripe |
| 11.1 | Crear el proyecto de Firebase, integrar el SDK en la app y guardar el token de cada dispositivo |
| 11.2 | Enviar desde el backend las notificaciones de reserva confirmada, tiempo por terminar y cajón ocupado; mostrar el historial en la pestaña Avisos |
| 12.2 | Calcular subtotal, comisión, cupón y total en la Edge Function y guardar el desglose en `Pagos` |

## Arquitectura

| Componente | Tecnología |
|---|---|
| Sensado | Arduino Uno, 3 sensores HC-SR04, LEDs |
| Gateway | Raspberry Pi, Python 3 (pyserial, paho-mqtt, requests) |
| Mensajería | Mosquitto (MQTT, QoS 1, mensajes retenidos) |
| Backend | Supabase: PostgreSQL, Auth, Storage, Edge Functions |
| Aplicación | Android (Java), OkHttp, Google Maps, ZXing |
| Pagos | Stripe (PaymentSheet, modo prueba) |

## Estructura general del repositorio

- **design-baseline/:** Elementos técnicos precedidos del Laboratorio Abierto: Diseño (requerimientos, arquitectura, modelado de la aplicación, encuesta, modelo de datos). Muestra el estado del proyecto antes de iniciar la construcción.

- **docs/:** Documentación de lo construido: endpoints, seguridad de la base de datos, lógica de negocio, flujos de la aplicación, MQTT, pagos y comisión.

>**PENDIENTES DE SUBIR**
>- **evidence/:** Capturas, videos y resultados de las pruebas (monitor serial, LEDs, mensajes MQTT, pagos de prueba)

- **hardware/:** Firmware del Arduino (`smartparking_sensores.ino`), conexiones, configuración de sensores y control de LEDs.

- **project-management/:** WBS, RACI, Risk Register, cronograma y Project Charter.

- **software/:** Código de los componentes del sistema:
  - Aplicación Android.
  - `raspberry/`: gateway del Arduino y configuración de Mosquitto.
  - `supabase/`: migraciones de la base de datos y Edge Functions.

- **src/:** Código fuente principal.

- **bom/:** Lista de materiales, componentes, servicios y plataformas.

- **cad/:** Diseños técnicos y diagrama del prototipo.

- **tests/:** Planes y resultados de pruebas.

## Cómo consultar o reproducir el proyecto

> Las contraseñas y llaves **no están en el repositorio**. Cada integrante las configura en sus archivos locales (`.env`, `local.properties`) o en los secretos de Supabase.

### 1. Arduino

1. Armar el circuito según la tabla de conexiones de `hardware/README.md`.
2. Abrir `hardware/smartparking_sensores/smartparking_sensores.ino` en Arduino IDE.
3. Seleccionar **Arduino Uno** y el puerto, y presionar **Subir**.
4. En el **Monitor Serie** (9600 baudios) deben aparecer líneas como `Sensor 1: 0`.

### 2. Broker MQTT y gateway (Raspberry Pi)

1. Instalar y configurar Mosquitto siguiendo `raspberry/mosquitto/README.md`.
2. En la carpeta `raspberry/`:
   ```bash
   python3 -m venv venv
   venv/bin/pip install -r requirements.txt
   cp .env.example .env      # llenar SUPABASE_KEY, MQTT_USUARIO y MQTT_PASSWORD
   venv/bin/python gateway.py
   ```
3. Ver los mensajes: `mosquitto_sub -h localhost -u monitor -P '<contraseña>' -t 'smartparking/#' -v`

### 3. Base de datos (Supabase)

Aplicar las migraciones de `supabase/migrations/` en el orden indicado en `supabase/README.md`, desde el SQL Editor del panel o con `supabase db push`.

### 4. Aplicación Android

1. Abrir el proyecto en Android Studio.
2. Agregar a `local.properties`:
   ```
   STRIPE_PUBLISHABLE_KEY=pk_test_...
   ```
3. Sincronizar Gradle y ejecutar en un dispositivo o emulador.
4. Para pagar en modo prueba, usar la tarjeta `4242 4242 4242 4242` con cualquier fecha futura y CVC.

## Versiones principales

**Design Baseline v1.0**

Esta versión corresponde a la línea base del diseño y establece los elementos que serán utilizados como referencia durante la etapa de Construcción.

**sprint-1-plan-baseline-v1.0**

Esta versión corresponde a todas las actividades realizadas para el Sprint I, estableciendo como referencia las actividades planificadas, los objetivos definidos, los entregables generados y el avance alcanzado durante este periodo de desarrollo.

**sprint-2-review-v1.0**

Esta versión corresponde a la revisión del Sprint II. Incluye el firmware del Arduino, el gateway con el broker MQTT, la base de datos con políticas de seguridad y reglas de negocio, la aplicación Android con registro, reservas y pagos, y la documentación de cada módulo. Quedan pendientes para el Sprint III las pruebas de MQTT y de transacciones, las notificaciones push y la integración de la comisión en el backend.