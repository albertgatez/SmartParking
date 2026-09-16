## Introducción 

En el desarrollo de cualquier sistema tecnológico es necesario evaluar si la propuesta puede llevarse a cabo de manera realista. El análisis de factibilidad permite determinar si existen las condiciones técnicas, económicas y de recursos necesarias para desarrollar e implementar un proyecto. 

En este caso, se analiza la viabilidad del sistema SmartParking, una solución orientada a mejorar la gestión de estacionamientos mediante el uso de tecnología para facilitar la búsqueda de espacios disponibles, optimizar la administración y mejorar la experiencia de los usuarios. 

Para este análisis se toman como base los requerimientos funcionales y no funcionales definidos previamente. A partir de ellos se evalúan tres dimensiones principales: la factibilidad técnica, la factibilidad económica y la factibilidad de recursos. Este análisis permite identificar posibles limitaciones, riesgos y oportunidades antes de iniciar el desarrollo del sistema. 

## Análisis de factibilidad técnica

La factibilidad técnica se refiere a la disponibilidad y capacidad de los recursos tecnológicos necesarios para desarrollar e implementar el sistema. 

En el caso de SmartParking, el sistema requiere una combinación de hardware, software e infraestructura tecnológica que permita registrar la disponibilidad de espacios, gestionar los accesos y mostrar la información a los usuarios. 

En cuanto al hardware, el sistema puede operar utilizando sensores de ocupación de estacionamiento o dispositivos de control de acceso que permitan identificar vehículos. La idea es utilizar servidores o servicios en la nube para almacenar la información, procesar los datos en tiempo real y alta disponibilidad. Estos dispositivos son actualmente accesibles en el mercado y no representan una barrera tecnológica significativa. 

Respecto al software, el sistema puede desarrollarse utilizando tecnologías ampliamente conocidas como aplicaciones móviles, bases de datos relacionales y servicios en la nube, como ya se mencionó. Lenguajes de programación como Java, Python y TypeScript permiten construir la plataforma sin requerir herramientas altamente especializadas. 

En cuanto a la infraestructura, el proyecto requiere conexión a internet, servidores para el almacenamiento de datos y una interfaz gráfica que permita a los usuarios consultar la disponibilidad de espacios de manera sencilla. Estas tecnologías ya se encuentran disponibles y son utilizadas comúnmente en sistemas similares. 

Finalmente, el equipo de desarrollo cuenta con conocimientos suficientes en programación, bases de datos y desarrollo de software, lo que permite abordar el proyecto con herramientas y habilidades ya conocidas. Por lo tanto, desde el punto de vista técnico, el proyecto es viable. 

## Análisis de factibilidad económica 

Para el desarrollo del sistema SmartParking se deben considerar algunos costos relacionados principalmente con la adquisición de hardware, como sensores de detección, microcontroladores y una Raspberry Pi que funcionará como gateway para el envío de datos. También se contemplan los recursos necesarios para pruebas y desarrollo del sistema. 

En el caso de un proyecto académico, los costos de desarrollo de software se reducen considerablemente, ya que el sistema es desarrollado por nosotros. Asimismo, muchas de las herramientas utilizadas, como entornos de programación, servicios en la nube con planes gratuitos y plataformas de desarrollo, pueden obtenerse mediante licencias educativas o versiones gratuitas disponibles. 

Por otro lado, la inversión en hardware puede mantenerse relativamente baja utilizando componentes accesibles y ampliamente disponibles en el mercado, como sensores ultrasónicos y microcontroladores de bajo costo. 

Considerando estos factores, el proyecto puede desarrollarse utilizando recursos limitados y herramientas accesibles dentro del entorno académico. Esto permite concluir que el sistema es económicamente viable dentro de este contexto. 

## Análisis de factibilidad de recursos 

La factibilidad de recursos evalúa si se cuenta con el personal, el tiempo y los materiales necesarios para desarrollar el proyecto. 

En cuanto al recurso humano, el proyecto puede desarrollarse por un equipo multidisciplinario compuesto por programadores, diseñadores de interfaz y responsables de pruebas del sistema. Cada integrante del equipo puede encargarse de distintas áreas del desarrollo, lo que permite distribuir el trabajo de manera óptima. 

Respecto al tiempo, el desarrollo del sistema se maneja con ayuda de plataformas de gestión, asignando tareas para las etapas con fechas establecidas junto con el personal responsable de dicha tarea. Con esto mantenemos un orden y un avance continuo. 

En relación con los materiales y herramientas, el equipo puede utilizar computadoras personales, entornos de desarrollo, plataformas de control de versiones y servicios de almacenamiento en la nube. Estas herramientas son accesibles y en muchos casos gratuitas o disponibles mediante licencias educativas. 

Por lo tanto, los recursos necesarios para el desarrollo del proyecto se encuentran disponibles y pueden ser utilizados sin problema para cumplir con los objetivos planteados.
## Integración con los requerimientos previos 

### Requerimientos Funcionales 

Los requerimientos funcionales pueden enfrentar riesgos relacionados principalmente con la precisión del hardware y el comportamiento de los usuarios. La detección de espacios depende del correcto funcionamiento de los sensores, por lo que fallos, interferencias o mala instalación podrían generar información incorrecta sobre la disponibilidad. Asimismo, el sistema de reservas podría provocar conflictos si los usuarios no ocupan los espacios reservados en el tiempo previsto. También existe dependencia de servicios externos para los pagos, lo que podría afectar el funcionamiento si dichas plataformas presentan fallas o interrupciones. 

### Requerimientos No Funcionales

Los requerimientos no funcionales pueden verse limitados por factores técnicos como la capacidad del sistema, la estabilidad de la red y la seguridad, ya que la disponibilidad depende de la conectividad a internet y de la estabilidad de los servicios en la nube. Si el número de usuarios o sensores aumenta más de lo previsto, el rendimiento podría disminuir y provocar retrasos en la actualización de datos. Además, aunque se aplican medidas de seguridad como cifrado y autenticación, siempre existe el riesgo de vulnerabilidades o accesos no autorizados. 

### Requerimientos Técnicos (Hardware, Software, Herramientas)

Los requerimientos técnicos presentan limitaciones relacionadas con la infraestructura y la integración tecnológica. La instalación de sensores y dispositivos como microcontroladores o Raspberry Pi puede implicar costos iniciales. Asimismo, el correcto funcionamiento del sistema depende de la integración entre distintas tecnologías como MQTT, bases de datos, servicios en la nube y pasarelas de pago. Cualquier falla en alguno de estos componentes podría afectar temporalmente el funcionamiento general del sistema. 

## Conclusión 

El análisis de factibilidad realizado demuestra que el proyecto SmartParking es viable desde los puntos de vista técnico, económico y de recursos bajo cierto escenario. En el aspecto técnico, existen tecnologías accesibles que nos permiten desarrollar el sistema sin requerir infraestructura compleja. Desde la perspectiva económica, los costos iniciales pueden justificarse por los beneficios que el sistema puede generar en términos de eficiencia, organización y mejora en la experiencia del usuario. 

Por otro lado, los recursos humanos, las herramientas tecnológicas y el tiempo disponible permiten desarrollar el proyecto dentro de un marco realista. 

Aunque se identifican algunos riesgos relacionados con la conectividad y la inversión inicial en hardware, estos pueden mitigarse mediante una planificación adecuada y el uso de alternativas tecnológicas. 

En general, el proyecto presenta condiciones favorables, por lo que se considera una propuesta viable.