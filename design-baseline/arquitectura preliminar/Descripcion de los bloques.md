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

## Conclusión 

Identificamos estos bloques en los que se muestra la interacción entre componentes de hardware y el flujo de información del software en el funcionamiento del sistema. Desde la adquisición de datos mediante sensores hasta su procesamiento, almacenamiento y la visualización en la interfaz de usuario, se establece un flujo adecuado de la información, asegurando que los datos sean actualizados en tiempo real con precisión. 

Al definir estos bloques nos facilita el mantenimiento, e identificar los errores para llegar a corregirlos sin afectar el funcionamiento del sistema. Nos permitió conocer más sobre los protocolos que tenemos seguir así como la secuencia de los datos en el proceso de la reserva, saber en dónde son necesarios servicios externos en aspectos importantes como la gestión de pagos.