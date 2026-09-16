El éxito en el desarrollo de un sistema de software depende en gran medida de una correcta definición de sus requerimientos desde las etapas iniciales del proyecto. Establecer de forma clara y detallada los requisitos permite alinear las expectativas del cliente con el trabajo del equipo de desarrollo, reducir ambigüedades, prevenir errores costosos y asegurar que el producto final cumpla con las necesidades reales de los usuarios. En este contexto, el presente documento tiene como propósito definir y presentar de manera estructurada los requisitos y especificaciones, tanto funcionales como no funcionales, que deberá cumplir el software SmartParking al finalizar su construcción. La correcta documentación e identificación de estos requerimientos constituye la base para el diseño, desarrollo, validación y mantenimiento del sistema.

## Requisitos Funcionales  

**RF-01. Gestión de Disponibilidad en Tiempo Real**  
El sistema debe detectar y actualizar automáticamente el estado (libre, ocupado o reservado) de cada espacio de estacionamiento mediante sensores conectados a microcontroladores, enviando los datos al servidor central (por MQTT) para que la app los muestre al usuario.  

**RF-02. Escaneo de Código QR**  
El sistema permitirá al usuario escanear un código QR ubicado en el boleto al momento de la entrada y salida del estacionamiento para registrar hora y fecha de acceso o salida.  

**RF-03. Gestión de Reservas**  
El sistema debe permitir reservar un espacio disponible mediante la aplicación móvil y bloquear su disponibilidad para otros usuarios.  

**RF-04. Procesamiento de Pagos**  
El sistema procesará pagos mediante pasarelas como Stripe o MercadoPago, utilizando HTTPS y tokens seguros.  

**RF-05. Sistema de Notificaciones**  
El sistema envía notificaciones push al usuario cuando se confirma una reserva, se acerca al fin del tiempo contratado o cambia el estado de su espacio.  

**RF-06. Cálculo de Comisión por Reserva**  
El sistema permitirá aplicar una comisión del 2% al 5% sobre el costo del estacionamiento y mostrarla antes de confirmar el pago.

**RF-07. Historial de Pagos**  
El sistema permitirá consultar y exportar el historial de pagos realizados, incluyendo fecha, monto y duración del estacionamiento.  

**RF-08. Actualización de Estado del Espacio**  
El sistema actualizará automáticamente el estado del espacio tras el escaneo del QR o la detección del sensor (ocupado/libre/reservado).  

## Requisitos No Funcionale

### 1. Rendimiento  

- **RNF-01.** Soporte para 100 sensores MQTT simultáneos sin pérdida de paquetes.  
- **RNF-02.** El 95% de peticiones REST deben responder en < 4 segundos.  
- **RNF-03.** Latencia de actualización de estado de cajón en app: < 5 segundos.  
- **RNF-04.** La Raspberry Pi debe funcionar bajo 70% CPU y 80% RAM.  
- **RNF-05.** Frecuencia de envío de datos configurable (1-5 segundos).  

### 2. Seguridad 

- **RNF-01.** Cifrado HTTPS (TLS 1.3) en todas las comunicaciones.  
- **RNF-02.** Autenticación de usuarios vía token JWT.  
- **RNF-03.** Gestión de permisos: diferenciar usuarios (conductores) y administradores.  
- **RNF-04.** Registro (logs) de actividades críticas (login, reserva, pago, cambio de estado).  
- **RNF-05.** Validación de solicitudes de cambio de estado (firmas/tokens únicos) para integridad. 

### 3. Fiabilidad 

- **RNF-01.** Continuidad del servicio local sin Internet; reenvío de datos al reconectar.  
- **RNF-02.** Reinicio automático tras fallo crítico sin pérdida de estados.  
- **RNF-03.** Tasa máxima: 1 fallo por cada 500 operaciones críticas.  

### 4. Disponibilidad  

- **RNF-01.** Disponibilidad mínima del servicio: 98% mensual.  
- **RNF-02.** Monitoreo continuo (cada minuto) de conexión (broker y DB).  
- **RNF-03.** Recuperación y reconexión automática (MQTT/Supabase) en < 30 segundos tras caída.  

### 5. Mantenibilidad

- **RNF-01.** Mantenimiento preventivo mensual (firmware, limpieza DB, logs).
- **RNF-02.** Registro de errores del backend (logs rotativos, 30 días de historial).
- **RNF-03.** Escalabilidad modular: añadir sensores, usuarios o estacionamientos sin reestructurar arquitectura.
- **RNF-04.** Documentación de código y API (Swagger/Postman Collections).

### 6. Portabilidad  

- **RNF-01.** Compatibilidad con Raspberry Pi 3/4 y Raspberry Pi OS (Debian 12).  
- **RNF-02.** Desarrollo principal en Supabase (Auth, DB PostgreSQL, API REST, Edge Functions) y aplicación móvil en Java con SDK de Supabase.  
- **RNF-03.** Independencia del hardware: compatible con sensores ultrasónicos Trigger Echo (HC-SR04, JSN-SR04T).  
- **RNF-04.** Exportación de datos de Supabase a SQL o CSV para migración.  

## Requerimientos Técnicos (Hardware, Software, Herramientas)  

### Hardware y Sistemas Embebidos 

- **RT-H1 Sensores:** Sensores ultrasónicos (ej. HC-SR04, JSN-SR04T) para detección de ocupación.  
- **Microcontroladores RT-H2:** Uso de microcontroladores (ej. Arduino) para lectura de sensores y comunicación local.  
- **RT-H3 Gateway:** Raspberry Pi o similar para lectura serial, publicación MQTT, envío HTTPS y gestión de servicio local.  
- **RT-H4 Indicadores:** LEDs (verde/rojo/naranja) gestionados por servidor/base de datos.  
- **RT-H5 Escaneo:** Cámara del dispositivo móvil para escanear códigos QR.  

### Software y Plataformas  
- **RT-S1 Backend/BD:** Supabase para PostgreSQL, autenticación y notificaciones.  
- **Base de datos RT-S2:** PostgreSQL para estados, reservas, transacciones y registros.  
- **RT-S3 Broker MQTT:** Broker de mensajes para datos en tiempo real; soporta hasta 100 conexiones.  
- **RT-S4 Pagos:** Integración con Stripe o MercadoPago.  
- **RT-S5 Notificaciones Push:** Firebase Cloud Messaging (FCM) o alternativa compatible.  
- **Aplicación Móvil RT-S6:** Compatible con Android 10+.  
- **RT-S7 Protocolo Backend:** Comunicación de datos/API mediante HTTPS (TLS 1.3).  
- **RT-S8 Autenticación:** Mecanismo basado en tokens JWT.

### Requerimientos Críticos  

- **RF-01.** Gestión de Disponibilidad en Tiempo Real  
- **RF-03.** Gestión de Reservas  
- **RF-04.** Procesamiento de pagos seguro y registro  
- **RF-08.** Actualización de Estado del Espacio  

## Conclusión 

La definición de los requerimientos de nuestro proyecto nos permite que el desarrollo del sistema tenga una dirección clara desde estas primeras etapas de diseño. También nos garantiza que el sistema puede escalar sin perder estabilidad ni rendimiento. Al contemplar desde el inicio aspectos como la concurrencia de los sensores, los tiempos de respuesta y la compatibilidad con distintos dispositivos, construimos un punto de partida consistente para el desarrollo, identificando los requerimientos críticos para administrar las tareas que necesitan dárseles más prioridad.