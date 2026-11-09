**LABORATORIO ABIERTO: CONSTRUCCIÓN**

# **CONSTRUCTION HANDOVER PACKAGE**

Consolidación y transición hacia Laboratorio Abierto: Pruebas

**Proyecto:**  SmartParking - Sistema de reservas de estacionamiento en centros comerciales

**Project Leader:**  Alberto de Jesús Carlos Muro

**Mentor:**  Luis Ernesto Ayala Hernández

**Fecha de cierre:**  08/11/2026

**Versión final:** construction-handover-v1.0

# **1\. Estado de la solución**

| Elemento / subsistema | Estado | Integración alcanzada | Evidencia |
| :---- | :---- | :---- | :---- |
| **Sensado (Arduino Uno, 3 HC-SR04, LEDs)** | Terminado | Detecta los 3 cajones con umbral de 10 cm, histéresis y filtro de tiempo (3 s para ocupar, 1.5 s para liberar); LEDs libre, ocupado y reservado; recibe los comandos R, C y L del gateway. | hardware/smartparking\_sensores/; docs/technical/Arduino/; evidence/hardware/ |
| **Gateway y broker MQTT (Raspberry Pi 4, Mosquitto)** | Terminado | Integrado con el Arduino (serial) y con Supabase. Corre como servicio, arranca solo, se reconecta y publica en MQTT con autenticación y QoS 1\. | software/raspberry/; docs/technical/MQTT/; evidence/raspberry/ |
| **Backend (Supabase: base de datos, Auth, Edge Functions)** | Terminado con pendientes menores | RLS etapa 1, reglas de negocio, desglose de pagos y notificaciones aplicados.  Etapa 2 de RLS pendiente. | software/supabase/; docs/technical/BD/; evidence/supabase-bd/ |
| **Aplicación Android (Java)** | Terminado; en pruebas | Registro, login, mapa, reserva por horas, pago con Stripe con monto calculado en el servidor, cupón, tarjetas guardadas, QR, extensión, historial y avisos. | src/android\_app/; docs/technical/Application/ |
| **Pagos (Stripe, modo prueba)** | Terminado; en pruebas | crear-pago-reserva-v2 calcula el cobro (4 % reserva, 5 % extensión, cupón PROMO10); stripe-webhook confirma, extiende y registra el pago con su desglose; metodos-pago administra tarjetas. | software/supabase/functions/ Stripe y flujo de pago.md; Metodos de pago.md |
| **Notificaciones push (Firebase Cloud Messaging)** | Terminado | Avisos de reserva confirmada, extensión, recordatorio 15 min antes del fin, vehículo detectado y cajón vacío; historial en la pestaña Avisos. | Notificaciones en Supabase.md; Notificaciones.md; migraciones 20261105 y 20261106 |
| **Historial de pagos y exportación**  | Terminado  | El historial muestra el estacionamiento, el tipo de pago y su desglose (subtotal, comisión, descuento y total). Desde Perfil → Historial se exporta un CSV en UTF-8 con BOM que se comparte con el menú de Android.  | docs/technical/Application/Exportacion del historial.md; ejemplo\_historial\_pagos.csv; src/android\_app/.../pagos/ExportadorPagos.java  |
| **Vista detallada de la reserva**  | Terminado  | En Home se muestran el estacionamiento, la sección y el cajón, el horario y el precio por hora de la reserva activa, junto con el QR y la opción de extender el tiempo.  | src/android\_app/.../ui/home/HomeFragment.java; Flujo de reservas.md  |

# **2\. Evidencia técnica**

Relacione los artefactos pertinentes: prototipo físico, software, firmware, planos, PCB, CAD, modelos, fotografías, video, resultados parciales o demostración funcional.

| Artefacto | Versión / ubicación | Descripción | Estado |
| :---- | :---- | :---- | :---- |
| **Prototipo físico (maqueta de 3 cajones)** | evidence/hardware/E1.jpeg a E4.jpeg | Fotografías del prototipo montado con sensores, LEDs, Arduino y Raspberry Pi. | Terminado |
| **Firmware del Arduino** | hardware/smartparking\_sensores/smartparking\_sensores.ino (main, 08/11/2026) | Lectura de sensores sin delay(), filtros de lecturas y de tiempo, control de LEDs y protocolo serial. | Terminado |
| **Gateway y configuración de Mosquitto** | software/raspberry/ (gateway.py, mosquitto/smartparking.conf, acl, smartparking-gateway.service) | Puente Arduino–MQTT–Supabase y broker con autenticación. | Terminado |
| **Migraciones de la base de datos** | software/supabase/migrations/ (8 scripts) | Lógica de negocio, RLS (etapas 1 y 2), desglose de pagos y notificaciones, con su estado en el README. | 7 aplicadas, 1 pendiente (RLS etapa 2). |
| **Edge Functions** | software/supabase/functions/ (crear-pago-reserva-v2, stripe-webhook, metodos-pago, notificar-bienvenida, enviar-notificacion) | Pagos con Stripe, tarjetas guardadas y envío de notificaciones. | Desplegadas |
| **Aplicación Android** | src/android\_app/ (main, merge PR \#68 del 08/11/2026) | Código fuente de la app con modo oscuro, métodos de pago y monto calculado en el servidor. | Terminado; en pruebas. |
| **Documentación técnica** | docs/technical/ (Application, Arduino, BD, MQTT) | 18 documentos: endpoints, RLS, lógica de negocio, flujos, pagos, comisión, notificaciones, sensores, LEDs y MQTT. | Terminado |
| **Evidencia de pruebas** | evidence/raspberry/, evidence/supabase-bd/, evidence/hardware/ | Broker MQTT, conexión Arduino–Raspberry, sincronización con backend, RLS antes/después, doble reserva rechazada y pruebas de detección del sensor (57/57 aciertos). | Terminado |
| **Diseño base** | design-baseline/ (requerimientos, arquitectura, ER, modelado de la app y del prototipo, cuestionario) | Línea base del Laboratorio Abierto: Diseño. | Terminado |
| **Gestión del proyecto** | project-management/ (WBS, RACI, Risk Register, cronograma, charter, Sprint II Progress Review) | Planeación y seguimiento del proyecto. | Vigente |

# **3\. Historial del proyecto**

| Aspecto | Resumen | Evidencia / referencia |
| :---- | :---- | :---- |
| Plan inicial | Sistema de reservas de estacionamiento en centros comerciales: app Android, backend en Supabase, sensores HC-SR04 en Arduino y gateway en Raspberry Pi con MQTT, con pagos en línea.  | design-baseline/; project-management/wbs/; project-management/schedule/ |
| Modificaciones principales | Gateway único en Python en lugar de dos scripts; estados 0/1/2 iguales en Arduino, MQTT y base de datos; RLS dividida en dos etapas para no romper la app; cálculo del cobro movido de la app al servidor (crear-pago-reserva-v2); pantalla de métodos de pago con tarjetas guardadas; modo oscuro en la app; filtro de tiempo en los sensores. | Sprint III;; software/supabase/README.md; evidence/hardware/ |
| Decisiones relevantes | Comisión de 4 % en reservas y 5 % en extensiones; el LED rojo parpadea solo si el cajón está reservado y no se escaneó el QR; LED azul físico en el cajón de discapacidad; 5 pestañas fijas en la app; la confirmación del pago la hace el webhook; nueva función de pago desplegada con otro nombre (v2) para conservar la anterior; migración a Flutter pospuesta. | Porcentaje De Comision.md; Control\_LEDs.md; Navegacion y priorizacion.md; Stripe y flujo de pago.md. |
| Riesgos e incidencias | Doble reserva real (173/174) corregida con una restricción EXCLUDE; políticas RLS abiertas cerradas con la etapa 1\. Edge Functions originales sin código fuente, por lo que se desplegaron versiones nuevas; stripe-webhook reemplazado sin respaldo, compensado con la migración de desglose de pagos. | evidence/supabase-bd/; Definicion\_de\_relaciones.md; software/supabase/README.md. |
| Cambios de responsabilidad | Sin cambios formales en el RACI. Alberto: liderazgo, interfaz y corrección de errores; Santiago: hardware, gateway, MQTT, Raspberry Pi y backend de pagos; Fernando: base de datos, documentación técnica y notificaciones push. | project-management/raci/ historial de commits. |
| Evolución de la solución | De un prototipo con lógica en el teléfono y base de datos abierta a un sistema integrado de punta a punta: sensor, Arduino, gateway, MQTT y Supabase con reglas en el servidor, pagos verificados por webhook y notificaciones push al usuario. | README.md; Merge PR \#64 a \#68 |

# 

# **4\. Trabajo pendiente**

| Actividad pendiente | Motivo | Prioridad | Responsable sugerido / siguiente acción |
| :---- | :---- | :---- | :---- |
| **Pruebas de transacciones completas**  | Falta probar extensión de tiempo, cupón inválido, tarjeta rechazada y reserva expirada con el flujo nuevo; solo hay 2 pagos registrados con el webhook nuevo, ambos de tipo reserva | Alta | Alberto y Fernando: ejecutar la tabla de casos de Pruebas de transacciones y revisar Pagos. |
| **RLS etapa 2** | Cada usuario todavía puede leer las reservas de otros, y la app aún marca confirmada \= true | Media | Fernando: usar la vista horarios\_ocupados en la app, quitar la confirmación desde la app y aplicar 20261106\_rls\_etapa2.sql. |
| **Retirar la función crear-pago-reserva anterior** | Sigue desplegada para versiones viejas de la app | Baja | Equipo: actualizar la app en todos los teléfonos y eliminarla. |

# **5\. Defectos y limitaciones conocidas**

| Elemento | Defecto / limitación | Impacto | Condición / recomendación |
| :---- | :---- | :---- | :---- |
| **Validación del QR** | Cualquier código escaneado cuenta como llegada; no se valida que pertenezca al cajón o al estacionamiento | Medio: otra persona podría confirmar una llegada falsa | Generar un QR por cajón y validarlo en el servidor |
| **Lectura de reservas (RLS)** | Un usuario con sesión puede consultar las reservas de otros usuarios | Medio: exposición de horarios e identificadores | Aplicar la etapa 2 de las RLS |
| **Escala del prototipo** | El umbral de 10 cm corresponde a la maqueta; en un estacionamiento real se necesitaría recalibrar | Bajo para Pruebas | Ajustar UMBRAL\_CM e instalación de los sensores en un despliegue real |
| **Folio de discapacidad** | Se valida contra un valor fijo en la app | Bajo | Validarlo en el servidor |
| **Pagos de versiones viejas de la app** | Con el webhook nuevo, los pagos hechos con la función anterior se guardan sin desglose (tipo, subtotal y comisión vacíos) | Bajo | Usar solo la versión actual de la app durante Pruebas |

# **6\. Preparación para Pruebas**

| Elemento | Estado | Evidencia | Listo para Pruebas |
| :---- | :---- | :---- | :---- |
| **Hardware y firmware** | Montado y probado en la maqueta | evidence/hardware/Evidencia\_Pruebas\_de\_deteccion\_del\_sensor.md (57/57 aciertos, 3.4 a 3.6 s) | Sí |
| **Gateway, MQTT y Raspberry Pi** | Funcionando como servicio con arranque automático | evidence/raspberry/ (4 documentos) | Sí |
| **Base de datos y seguridad** | RLS etapa 1 y reglas de negocio aplicadas y probadas | evidence/supabase-bd/ (RLS antes/después, doble reserva rechazada) | Sí |
| **Aplicación Android** | Compila en main; flujo principal probado en teléfono | docs/technical/Application/; capturas de notificaciones y modelado | Sí, con la tabla de pruebas de interfaz pendiente. |
| **Pagos con Stripe** | Reserva pagada y registrada con desglose; extensión y casos de error por probar | Tabla Pagos (stripe\_payment\_intent); Stripe y flujo de pago.md | Si |
| **Notificaciones push** | Funcionando en teléfono | Notificaciones en Supabase.md; captura de la pestaña Avisos | Sí |

# **7\. Configuración y reproducción**

**Versiones al cierre:** sprint-1-plan-baseline-v1.0, sprint-2-SmartParking-v1.1sprint-3-SmartParking-v2.0

**Hardware:** armar el circuito según hardware/README.md y cargar el sketch con Arduino IDE en un Arduino Uno. El Arduino se conecta por USB a la Raspberry Pi.

**Raspberry Pi:** instalar Mosquitto y crear los usuarios gateway y monitor siguiendo software/raspberry/mosquitto/README.md. En software/raspberry/ crear el entorno virtual, instalar requirements.txt, copiar .env.example a .env con el puerto /dev/serial/by-id/..., el usuario y la contraseña de MQTT y la llave secreta raspberry-gateway, e instalar smartparking-gateway.service. Verificar con journalctl \-u smartparking-gateway \-f y mosquitto\_sub.

**Supabase:** las migraciones se aplican una sola vez desde el SQL Editor en el orden del README de software/supabase/. Los secretos STRIPE\_SECRET\_KEY, STRIPE\_WEBHOOK\_SECRET, FIREBASE\_SERVICE\_ACCOUNT y FCM\_WEBHOOK\_SECRET se configuran en Edge Functions, Secrets. Las funciones se despliegan desde el panel o con la CLI ejecutada desde software/.

**Aplicación:** abrir src/android\_app/ en Android Studio, agregar STRIPE\_PUBLISHABLE\_KEY en local.properties y el archivo google-services.json del proyecto de Firebase, sincronizar Gradle y compilar. Para pagar se usa la tarjeta de prueba 4242 4242 4242 4242\.

*Ninguna contraseña ni llave está en el repositorio: viven en .env, local.properties, google-services.json y los secretos de Supabase, y se comparten por un medio privado.*

# **8\. Aceptación de transferencia**

| Rol | Nombre | Firma / fecha |
| :---- | :---- | :---- |
| Project Leader | Alberto de Jesús Carlos Muro |   |
| Technical Mentor | Luis Ernesto Ayala Hernández |   |
| Course Supervisor | Jairo Cain Sánchez Estrada |   |

