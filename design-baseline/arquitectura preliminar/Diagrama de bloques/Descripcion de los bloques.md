## Descripción de los bloques y relaciones

### - Adquisición de datos 

Se encarga de obtener la información del entorno físico, principalmente de la detección de los vehículos y del estado de los espacios vacantes. Utiliza sensores ultrasónicos HC-SR04 que miden la ocupación de los espacios, cuyos datos son procesados por un microcontrolador Arduino y enviados a una Raspberry Pi que actúa como broker MQTT. Finalmente, la información es transmitida al bloque de Procesamiento mediante dicho protocolo. 

### - Procesamiento de datos

Se encarga de recibir, interpretar y estructurar la información proveniente de los sensores, sincronizándola con la base de datos y la lógica de negocio. Incluye un cliente MQTT que recibe los datos del broker, un API REST (Supabase) que gestiona la comunicación con la base de datos, una lógica de negocio que valida disponibilidad, gestiona reservas y pagos, y un módulo de control de errores que monitorea el sistema. Administra la comunicación bidireccional con los bloques de Adquisición, Almacenamiento e Interfaz de usuario, además de mantener un flujo hacia comunicación externa. 

### - Almacenamiento de datos

Administra la persistencia de los datos del sistema, garantizando su integridad, seguridad y respaldo. Está compuesto por una base de datos PostgreSQL gestionada mediante Supabase, donde se almacenan entidades como usuarios, espacios, reservas y pagos. Incluye organización en tablas y mecanismos de copia de seguridad automatizada. Mantiene comunicación bidireccional con el bloque de procesamiento de datos.

### - Interfaz de Usuario

Permite la interacción directa con el usuario final a través de una aplicación móvil. Incluye una pantalla principal que muestra los espacios disponibles, un módulo de reservas para gestionar apartados, un módulo de pagos que procesa transacciones y un sistema de notificaciones para informar eventos en tiempo real. Gestiona la comunicación bidireccional con la lógica de negocio y recibe información de forma unidireccional desde comunicación externa.

### - Comunicación externa

Gestiona la interacción con servicios externos necesarios para el funcionamiento del sistema. Incluye servicios de pago (Stripe) para procesar transacciones seguras, la API de Google Maps para visualizar la ubicación del estacionamiento y sistemas de notificaciones (Firebase Cloud Messaging) para el envío de alertas push. Establece comunicación bidireccional con la lógica de negocio y mantiene un flujo unidireccional hacia la interfaz de usuario y recibe un flujo de la lógica de negocio. 

## Definición de módulos

Para desarrollar la arquitectura de forma estructurada, los cinco bloques se dividen en módulos aislados. Cada módulo tiene una responsabilidad única así como entradas y salidas definidas, de modo que pueda desarrollarse y probarse por separado antes de integrarse con los demás.

| Módulo | Bloque al que pertenece |
|---|---|
| Firmware de sensores | Adquisición de datos |
| Gateway MQTT | Adquisición y Procesamiento de datos |
| Backend y lógica de negocio | Procesamiento de datos |
| Base de datos | Almacenamiento de datos |
| Aplicación móvil | Interfaz de usuario |
| Pagos | Comunicación externa / Procesamiento |
| Notificaciones | Comunicación externa / Interfaz |
| Control de errores y monitoreo | Procesamiento de datos |

### Firmware de sensores

- **Responsabilidad:** medir la distancia en cada cajón con los sensores HC-SR04, determinar si el cajón está libre u ocupado y encender el LED que corresponde a su estado (verde: libre, rojo: ocupado, amarillo: reservado, azul: cajón para personas con discapacidad libre).
- **Entradas:**
  - Lecturas de distancia de los sensores ultrasónicos.
  - Orden de estado "reservado" recibida desde la Raspberry Pi (para el LED amarillo).
- **Salidas:**
  - Estado de cada cajón (`libre` / `ocupado`) enviado a la Raspberry Pi.
  - Señal a los LEDs indicadores.
- **Comunicación:** con el gateway MQTT (Raspberry Pi) por **puerto serial (USB/UART)**, en ambos sentidos.

### Gateway MQTT

- **Responsabilidad:** servir de puente entre el hardware y la nube. La Raspberry Pi lee los estados que manda el Arduino, los publica en el broker MQTT y un cliente MQTT (middleware) los reenvía al backend. Si se pierde la conexión a Internet, guarda los estados localmente y los reenvía al reconectar (RNF-01 de fiabilidad).
- **Entradas:**
  - Estados de los cajones recibidos por serial desde el firmware de sensores.
  - Cambios de estado provenientes del backend (por ejemplo, un cajón que pasa a "reservado").
- **Salidas:**
  - Mensajes MQTT con el estado de cada cajón.
  - Actualizaciones enviadas al backend por HTTPS.
  - Órdenes de LED hacia el firmware de sensores.
- **Comunicación:**
  - Con el firmware de sensores por **serial**.
  - Internamente entre el broker y el cliente por **MQTT**.
  - Con el backend por **HTTPS (API REST de Supabase)**.

### Backend y lógica de negocio

- **Responsabilidad:** aplicar las reglas del sistema:
  - Validar la disponibilidad de los cajones.
  - Crear, confirmar, extender y liberar reservas.
  - Calcular montos y comisión.
  - Actualizar el estado de los cajones.
  - Decidir cuándo enviar una notificación.

  Es el único módulo que modifica datos sensibles; la aplicación solo le hace solicitudes.
- **Entradas:**
  - Solicitudes de la app (consultar estacionamientos, reservar, pagar, extender, escanear QR).
  - Estados de los cajones desde el gateway MQTT.
  - Confirmaciones de pago desde el módulo de pagos.
- **Salidas:**
  - Respuestas a la app (disponibilidad, datos de la reserva, montos).
  - Operaciones de lectura y escritura en la base de datos.
  - Solicitudes de cobro al módulo de pagos y de envío de notificaciones al módulo de notificaciones.
- **Comunicación:**
  - Con la aplicación móvil y el gateway MQTT por **HTTPS (API REST / Edge Functions de Supabase)** con autenticación **JWT**.
  - Con la base de datos por **SQL (PostgreSQL)**.
  - Con los módulos de pagos y notificaciones por **HTTPS**.

### Base de datos

- **Responsabilidad:** almacenar de forma persistente e íntegra la información del sistema:
  - Usuarios.
  - Estacionamientos.
  - Cajones.
  - Reservas.
  - Pagos.
  - Imágenes de los estacionamientos.

  Controla el acceso con políticas de seguridad por usuario (RLS), impide reservas traslapadas en un mismo cajón y mantiene copias de seguridad automatizadas.
- **Entradas:** consultas e inserciones provenientes del backend.
- **Salidas:** registros solicitados por el backend.
- **Comunicación:** solo con el backend, por **SQL / API REST de Supabase**.

### Aplicación móvil

- **Responsabilidad:** ser la interfaz del usuario en Android 10 o superior:
  - Registro e inicio de sesión.
  - Mapa y lista de estacionamientos.
  - Selección de cajón y horario.
  - Pago.
  - Escaneo del código QR del boleto.
  - Extensión de tiempo.
  - Historial de pagos.
  - Perfil.
- **Entradas:**
  - Acciones del usuario.
  - Respuestas del backend.
  - Notificaciones del módulo de notificaciones.
  - Mapa de Google Maps.
  - Interfaz de pago del módulo de pagos.
- **Salidas:**
  - Solicitudes al backend: reservas, extensiones, token del QR, datos del perfil.
  - Información mostrada al usuario.
- **Comunicación:**
  - Con el backend por **HTTPS** usando el token **JWT** del usuario.
  - Con Google Maps y Stripe mediante sus **SDK para Android**.
  - Con el módulo de notificaciones mediante el **SDK de Firebase**.

### Pagos

- **Responsabilidad:** procesar los cobros de reservas y extensiones con Stripe. El monto se calcula en el servidor y la reserva solo se confirma cuando Stripe notifica que el pago fue exitoso.
- **Entradas:**
  - Solicitud de cobro desde el backend (reserva y monto calculado).
  - Datos de la tarjeta o billetera digital capturados en la aplicación móvil a través del SDK de Stripe.
- **Salidas:**
  - Intención de pago para la app.
  - Confirmación o rechazo del pago hacia el backend (webhook).
  - Registro del pago en la base de datos a través del backend.
- **Comunicación:**
  - Con el backend por **HTTPS (API y webhooks de Stripe)**.
  - Con la aplicación móvil por el **SDK de Stripe (PaymentSheet)**.

### Notificaciones

- **Responsabilidad:** avisar al usuario cuando se confirma su reserva, cuando se acerca el fin del tiempo contratado y cuando cambia el estado de su cajón.
- **Entradas:**
  - Eventos generados por el backend.
  - Token del dispositivo registrado por la aplicación móvil.
- **Salidas:** notificaciones push en el teléfono del usuario.
- **Comunicación:**
  - Backend → Firebase Cloud Messaging por **HTTPS**.
  - FCM → aplicación móvil por **notificación push**.

### Control de errores y monitoreo

- **Responsabilidad:**
  - Registrar las actividades críticas (inicio de sesión, reserva, pago, cambio de estado) y los errores del backend.
  - Vigilar cada minuto la conexión con el broker MQTT y la base de datos.
  - Activar la reconexión automática cuando alguno falla.
- **Entradas:**
  - Eventos y errores del gateway MQTT y el backend.
  - Estado de las conexiones.
- **Salidas:**
  - Registros (logs) con historial de 30 días.
  - Alertas al equipo.
  - Reintentos de conexión.
- **Comunicación:** con el gateway MQTT y el backend por **HTTPS / registro interno de Supabase**.

## Relaciones entre módulos

| Origen | Destino | Protocolo | Información que se transmite |
|---|---|---|---|
| Firmware de sensores | Gateway MQTT | Serial (UART) | Estado de cada cajón |
| Gateway MQTT | Firmware de sensores | Serial (UART) | Orden de LED "reservado" |
| Gateway MQTT | Backend | MQTT → HTTPS | Cambios de estado de los cajones |
| Backend | Gateway MQTT | HTTPS / MQTT | Cajones reservados |
| Aplicación móvil | Backend | HTTPS + JWT | Solicitudes de reserva, pago, extensión y QR |
| Backend | Aplicación móvil | HTTPS | Disponibilidad, reservas y montos |
| Backend | Base de datos | SQL / API REST | Lectura y escritura de datos |
| Backend | Pagos | HTTPS | Creación de la intención de pago |
| Pagos | Backend | Webhook HTTPS | Confirmación del pago |
| Aplicación móvil | Pagos | SDK de Stripe | Datos del método de pago |
| Backend | Notificaciones | HTTPS | Eventos que generan aviso |
| Notificaciones | Aplicación móvil | Push (FCM) | Notificaciones al usuario |
| Gateway MQTT y backend | Control de errores | HTTPS / logs | Errores y estado de las conexiones |
## Conclusión 

Identificamos estos bloques en los que se muestra la interacción entre componentes de hardware y el flujo de información del software en el funcionamiento del sistema. Desde la adquisición de datos mediante sensores hasta su procesamiento, almacenamiento y la visualización en la interfaz de usuario, se establece un flujo adecuado de la información, asegurando que los datos sean actualizados en tiempo real con precisión. 

Al definir estos bloques nos facilita el mantenimiento, e identificar los errores para llegar a corregirlos sin afectar el funcionamiento del sistema. Nos permitió conocer más sobre los protocolos que tenemos seguir así como la secuencia de los datos en el proceso de la reserva, saber en dónde son necesarios servicios externos en aspectos importantes como la gestión de pagos.