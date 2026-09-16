## Lista preliminar de componentes

| **Componente**                       | **Función dentro del proyecto**                                       | **Cantidad aproximada** |
| ------------------------------------ | --------------------------------------------------------------------- | ----------------------- |
| Sensor ultrasónico HC-SR04           | Detectar la ocupación de los espacios de estacionamiento              | 3 - 5                   |
| Microcontrolador Arduino             | Procesar datos de los sensores y enviarlos al sistema                 | 1 – 2                   |
| Raspberry Pi                         | Actuar como gateway y gestionar la comunicación con el backend        | 1                       |
| LEDs (verde, rojo, naranja)          | Indicar visualmente el estado del espacio (libre, ocupado, reservado) | 6 – 10                  |
| Protoboard                           | Facilitar conexiones temporales para pruebas                          | 1 – 2                   |
| Cables jumper                        | Conectar componentes electrónicos                                     | 10 – 20                 |
| Dispositivo móvil Android            | Ejecutar la aplicación del sistema                                    | 1                       |
| Plataforma Supabase                  | Backend, base de datos y autenticación                                | (servicio)              |
| Stripe                               | Simulación de pago digital                                            | (servicio)              |
| Firebase Cloud Messaging             | Envío de notificaciones push                                          | (servicio)              |
| MQTT Broker                          | Comunicación en tiempo real entre dispositivos                        | 1                       |
| API REST                             | Comunicación entre app y backend                                      | N/A                     |
| Lenguajes (Java, Python, TypeScript) | Desarrollo del sistema completo                                       | N/A                     |
| GitHub                               | Control de versiones del proyecto                                     | (plataforma)            |
| Trello                               | Gestión de tareas del equipo                                          | (plataforma)            |
| Discord                              | Comunicación del equipo                                               | (plataforma)            |

Los componentes seleccionados fueron elegidos considerando su disponibilidad, costo accesible y compatibilidad con los requerimientos del proyecto. Los sensores ultrasónicos HC-SR04 son una opción económica y confiable para detectar la ocupación de espacios, siendo ampliamente utilizados en proyectos de este tipo. Los microcontroladores Arduino permiten una integración sencilla con los sensores, funcionando en conjunto para la adquisición y procesamiento de datos.

La Raspberry Pi se seleccionó como gateway debido a su capacidad para gestionar la comunicación entre el hardware y el sistema backend, además de su flexibilidad y bajo costo. Los LEDs se incluyen como apoyo visual para representar el estado de los espacios, lo cual es útil durante las pruebas del prototipo para comprobar el correcto funcionamiento de la reserva.

En cuanto al software, el uso de Supabase permite gestionar la base de datos, autenticación y lógica del sistema sin requerir infraestructura compleja. Firebase Cloud Messaging se eligió para el envío de notificaciones debido a su facilidad de integración y su disponibilidad gratuita. El protocolo MQTT es adecuado para la transmisión de datos en tiempo real en entornos IoT, mientras que la arquitectura API REST permite la comunicación entre los distintos componentes del sistema.

Las herramientas como GitHub, Trello y Discord se utilizan para la organización del equipo, el control de versiones y la comunicación, asegurando un desarrollo estructurado. En general, la selección de componentes responde a la necesidad de construir un prototipo funcional, viable y alineado con los recursos disponibles del equipo.

## Estimación de costos

### Hardware

| **Componente**         | **Cantidad** | **Costo unitario (MXN)** | **Total**  |
| ---------------------- | ------------ | ------------------------ | ---------- |
| Sensor HC-SR04         | 4            | $30                      | $120       |
| Arduino                | 1            | $120                     | $120       |
| Raspberry Pi           | 1            | $1,200                   | $1,200     |
| LEDs                   | 6            | $2                       | $12        |
| Protoboard             | 1            | $120                     | $120       |
| Cables                 | 1 pack       | $40                      | $40        |
| **Total** **Hardware** |              |                          | **$2,120** |

### Software

| **Recurso**        | **Costo** |
| ------------------ | --------- |
| Supabase           | Gratis    |
| Firebase           | Gratis    |
| Figma              | Gratis    |
| GitHub             | Gratis    |
| **Total Software** | **$0**    |

**Total del proyecto:** $2120.00 pesos mexicanos