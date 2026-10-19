**LABORATORIO ABIERTO: CONSTRUCCIÓN**

# **SPRINT II PROGRESS REVIEW**

Seguimiento, evaluación intermedia y adaptación

**Proyecto:** SmartParking — Sistema de reservas de estacionamiento en centros comerciales

**Fecha de revisión:** 18/10/2026

**Project Leader:** Alberto

**Mentor:** Luis Ernesto Ayala Hernandez

**Versión de referencia del Sprint I:** sprint-1-plan-baseline-v1.0

# **1\. Resumen ejecutivo del avance**

Al cierre del periodo, la fase de análisis y diseño está terminada (cuestionario a usuarios, arquitectura y base de datos) y el prototipo de hardware ya funciona de punta a punta: el Arduino detecta los 3 cajones con sensores HC-SR04, enciende los LEDs de estado y el gateway en Python actualiza el estado de cada cajón en Supabase y lo publica por MQTT.

Los flujos de registro, reserva y pago con Stripe (modo prueba) están documentados.

Diferencias respecto de la línea base del Sprint I: el backend (8.2, 8.3), la confirmación de pagos en el servidor (10.1, 12.2) y la configuración de Mosquitto en la Raspberry Pi (9.1) van atrasados; sus cambios ya están redactados como migraciones SQL y Edge Functions, pero falta aplicarlos y probarlos. Las notificaciones push (11.1, 11.2) no se han iniciado. En la revisión de la base de datos se encontraron políticas RLS abiertas, que pasan a ser prioridad para el Sprint III.

# **2\. Actividades e hitos**

| WBS / Issue | Planeado | Realizado | Evidencia | Estado |
| :---- | :---- | :---- | :---- | :---- |
| **1.2 Entrevistas y priorización** | Cuestionario a usuarios y priorización de necesidades (27/08–02/09) | Cuestionario aplicado a 17 personas; reporte con 8 preguntas, gráficas y necesidades priorizadas | design-baseline/Reporte\_encuesta.md y gráficas | Terminado |
| **3.3 Módulos de la arquitectura** | Definir los módulos del sistema y sus interfaces | Descripción de los 8 bloques del sistema y sus conexiones | design-baseline/Descripcion de los bloques.md | Terminado |
| **4.1–4.3 Base de datos** | Modelo ER, diseño de tablas y relaciones (10/09–17/09) | Diccionario de datos de 6 tablas; llaves foráneas y ON DELETE verificados en la BD; 0 huérfanos en relaciones con FK | Diccionario\_de\_datos.md, Definicion\_de\_relaciones.md | Terminado  |
| **5.1–5.3 Arduino, sensores y LEDs** | Firmware, configuración de sensores y LEDs (21/09–29/09) | Sketch sin delay() para 3 HC-SR04, filtro de 3 lecturas e histéresis; LEDs con reservado por comando serial y parpadeo si no se escanea el QR | hardware/ (sketch y 3 documentos) | Terminado ( |
| **6.2 Navegación y priorización** | Diagrama de navegación y priorización (24/09) | Diagrama de navegación, lista priorizada con datos del cuestionario y decisión de pestañas sin reserva | Navegacion y priorizacion.md | Terminado |
| **7.1–7.2 Registro, login y reservas** | Supabase Auth y flujo de reserva (25/09 \- 30/09) | JWT con renovación automática; reserva pendiente de 5 min con revalidación de disponibilidad; 22 archivos corregidos en la fase 1 | Registro e inicio de sesion.md | Terminado (en verificación en dispositivo) |
| **7.3 Navegación general y trazabilidad** | Recorrido completo (01/10 \- 04/10) | Recorrido y puntos de trazabilidad documentados; código muerto eliminado; quedan 2 placeholders | Flujo general y trazabilidad.md | En progreso |
| **8.1–8.3 Backend Supabase** | Endpoints, RLS y lógica de negocio (28/09 \- 09/10) | Endpoints documentados; JWT en todas las peticiones; migraciones de RLS y anti-traslape redactadas, sin aplicar | Conexion de endpoints.md y Conexion API Supabase.md  | Terminado |
| **9.1–9.3 MQTT** | Broker, formato de mensajes y pruebas (28/09–05/10) | Gateway Python probado con el Arduino en PC; formato y QoS definidos; configuración de Mosquitto lista, sin instalar en la Raspberry | Broker MQTT.md y Formato de mensajes MQTT.md | Terminado |
| **10.1–10.2 Stripe** | Flujo de pago y pruebas de transacciones (12/10 \- 16/10) | Pago con PaymentSheet en modo prueba; Edge Functions de referencia para cálculo en servidor y webhook | Stripe y flujo de pago.md  | Terminado |
| **11.1–11.2 Notificaciones (FCM)** | Configurar FCM y enviar notificaciones (12/10–14/10) | Diseño documentado (tablas, eventos y triggers); sin implementar | 11.x (.md) | No iniciado |
| **12.1–12.2 Comisión** | Definir comisión e integrarla al backend (12/10–16/10) | 4 % en reservas y 5 % en extensiones, justificados; cálculo en servidor redactado | 12.1: Porcentaje de comision.md  | 12.1 Terminado / 12.2 En progreso |

# **3\. Actividades pendientes y bloqueos**

| Actividad | Motivo / bloqueo | Responsable | Acción siguiente |
| :---- | :---- | :---- | :---- |
| **Aplicar RLS y reglas de negocio (8.2, 8.3)** | Las políticas actuales permiten modificar reservas ajenas; las migraciones requieren revisión y antes hay que resolver la doble reserva 173/174 | Todo el equipo | Revisar las migraciones en equipo y aplicarlas en el orden de supabase/README.md |
| **Revocar la llave sb\_secret (8.1)** | La llave estuvo en server.js y sigue en el historial de Git | Alberto | Revocarla en Supabase → API Keys y generar una nueva |
| **Pago confirmado en el servidor (10.1, 12.2)** | La app calcula el monto y confirma la reserva; las Edge Functions actuales no están versionadas | Alberto y Fernando | Descargar las funciones actuales, compararlas con las de referencia y desplegar |
| **Notificaciones push (11.1, 11.2)** | Depende de crear el proyecto de Firebase y de aplicar las migraciones del backend | Santiago y Fernando | Crear el proyecto de Firebase e integrar el SDK en la app |

# **4\. Recursos**

| Recurso | Planeado | Utilizado / disponible | Desviación / impacto |
| :---- | :---- | :---- | :---- |
| **Arduino Uno, 3 HC-SR04, LEDs y protoboard** | Prototipo de 3 cajones | Disponible y funcionando | Sin desviación |
| **Raspberry Pi** | Gateway y broker MQTT | Disponible; el gateway se probó en una PC con Windows (COM6) | Retraso en 9.1 y 9.3; sin impacto en el código, que funciona igual en Linux |
| **Supabase (plan gratuito)** | BD, autenticación, Storage y Edge Functions | En uso | Sin desviación; pg\_cron debe activarse para la limpieza de reservas |
| **Stripe (modo prueba)** | Pagos de reservas y extensiones | En uso | Sin costo; falta configurar el webhook |
| **Firebase Cloud Messaging** | Notificaciones push | Proyecto no creado | Retrasa 11.1 y 11.2 |
| **Equipo (3 integrantes)** | Alberto, Santiago y Fernando según RACI | 3 integrantes activos | Carga concentrada en backend y pagos para el Sprint III |

# **5\. Riesgos e incidencias**

| Tipo | Descripción | Impacto | Respuesta / decisión | Evidencia |
| :---- | :---- | :---- | :---- | :---- |
| Riesgo materializado | Doble reserva del mismo cajón en horarios que se cruzan (reservas 173 y 174\) | Alto: dos usuarios pagan por el mismo lugar | Restricción EXCLUDE con tstzrange en la BD (migración 8.3); resolver el caso existente antes de aplicarla | Definicion\_de\_relaciones.md, Logica de negocio.md |
| Nuevo riesgo | Políticas RLS abiertas: cualquier persona puede leer, confirmar o borrar reservas y cambiar el estado de los cajones | Alto: integridad y privacidad de los datos | Políticas por usuario (auth.uid()) y escritura de Cajon solo con service\_role (migración 8.2) | Conexion API Supabase.md |
| Incidencia | Llave secreta de Supabase expuesta en server.js dentro del repositorio | Alto: acceso total a la BD | Se eliminó server.js; queda revocar la llave y generar una nueva | — |
| Incidencia | La app de la fase 1 se integró al repositorio sin una compilación previa en Android Studio | Medio: posibles errores de compilación | Compilar en una rama aparte y probar login, reserva y pago antes de hacer merge | Rama fase1-correcciones |
| Nuevo riesgo | Las Edge Functions desplegadas no están en el repositorio y no se pudieron descargar con las herramientas usadas | Medio: no hay respaldo ni historial del código de pagos | Descargarlas con la CLI de Supabase y versionarlas en supabase/functions/ | Conexion de endpoints.md |

# **6\. Cambios y decisiones**

| Cambio / decisión | Justificación | Impacto | Documentos actualizados |
| :---- | :---- | :---- | :---- |
| **Estados del cajón con códigos 0/1/2 en Arduino, MQTT y BD** | estado\_cajon es smallint; un solo código evita conversiones y errores | El gateway escribe números en la BD y muestra texto en MQTT y logs | hardware/README.md, 9.2 |
| **LED rojo parpadea solo si el cajón está reservado y no se escaneó el QR** | Distinguir una ocupación normal de alguien que ocupó un lugar reservado | Nuevo comando serial C\<n\> y consulta de token\_estacionamiento en el gateway | Control\_LEDs.md |
| **Cajón de discapacidad con LED azul físico, sin lógica extra** | Mantener el firmware simple; el tipo de cajón ya está en la BD | Mismo código para los 3 cajones | Control\_LEDs.md |
| **Las 5 pestañas de la app siempre visibles** | Navegación inferior fija según Material Design; cambia el contenido según la reserva | Resuelve el ajuste pendiente del modelado | Navegacion y priorizacion.md |
| **Monto y confirmación del pago en el servidor (Edge Function \+ webhook)** | Evitar que se altere el monto desde la app y confirmar aunque la app se cierre | Cambios en ReservaApiClient, Carrito e Inicio; nuevas columnas en Pagos | Conexion de endpoints.md y Conexion API Supabase.md  |
| **Migración a Flutter pospuesta hasta terminar la app Android** | iOS es una mejora adicional; primero se estabiliza la versión actual | Sin impacto en el Sprint III | — |

# **7\. Documentos vivos**

| Documento | Versión previa | Versión actual | Motivo de actualización |
| :---- | :---- | :---- | :---- |
| WBS | v1.0 (Sprint I) | v1.1 | Estado real por paquete; se agregan las tareas de migraciones y pruebas del backend |
| RACI | v1.0 (Sprint I) | v1.0 | Sin cambios en responsables |
| Risk Register | v1.0 (Sprint I) | v1.1 | Cambios agregados al la matriz de riesgos |
| Project Schedule | v1.0 (Sprint I) | v1.1 | Se reprograman 9.3, 11.x y 12.2 hacia el Sprint III |
| Project Charter (si aplica) | v1.0 | v1.0 | No aplica; el alcance no cambió |

# **8\. Ruta restante hacia Sprint III**

| Hito / actividad | Responsable | Fecha objetivo | Evidencia de cierre |
| :---- | :---- | :---- | :---- |
| **Revocar llave expuesta y versionar Edge Functions** | Alberto | 21/10/2026 | Llave revocada; supabase/functions/ en el repo |
| **Mosquitto en la Raspberry Pi y pruebas MQTT (9.1, 9.3)** | Fernando y Santiago | 27/10/2026 | Capturas de mosquitto\_sub y tabla de resultados |
| **Quitar placeholders y grabar el recorrido completo (7.3)** | Todo el equipo | 30/10/2026 | Video del recorrido |
| **Pago y comisión calculados en el servidor (10.1, 12.2)** | Alberto y Fernando | 31/10/2026 | Edge Functions desplegadas y webhook registrado |
| **Pruebas de transacciones (10.2)** | Alberto y Fernando | 03/11/2026 | Tabla de resultados y capturas de Stripe |
| **Notificaciones push (11.1, 11.2)** | Santiago y Fernando | 07/11/2026 | Capturas de las notificaciones recibidas |

# **9\. Evidencia GitHub**

* Issues y tablero reflejan el estado real.  
* Branches, commits y Pull Requests identifican contribuciones.  
* Los documentos formales coinciden con el historial del repositorio.  
* Los bloqueos y cambios relevantes están documentados.