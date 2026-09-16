Este prototipo de hardware está compuesto por una placa Arduino que actúa como controlador principal que va conectada a nuestra raspberry PI, conectada a varios sensores ultrasónicos ubicados en la parte frontal, los cuales se encargan de medir la distancia para detectar la presencia o ausencia de un vehículo en cada espacio.

Encima de cada sensor se encuentran LEDs de diferentes colores que funcionan como indicadores visuales del estado de cada cajón: ocupado - rojo, en reserva - amarillo, disponible - verde (azul en caso discapacitado). El Arduino procesa las señales recibidas de los sensores y activa los LEDs correspondientes según la condición detectada.

## Análisis de los resultados y validación del diseño

Analizando el maquetado y siguiendo el flujo de los datos en el funcionamiento correcto del sistema validamos varios aspectos, comenzando con la interfaz gráfica, aunque se utiliza en su mayoría azul y blanco se manejan contrastes que resaltan las funcionalidades, íconos e interacciones.

Como posibles ajustes sería modificar la barra de navegación, se diseñó principalmente para que se pudiera acceder a todas las pantallas en la aplicación, no se llega aprovechar del todo esto, ya que funcionalidades y pantallas específicas no se muestran si no se tiene una reserva activa. Otro ajuste puede ser el mapeado fidedigno de los cajones de estacionamiento, de momento se tiene un mapa sencillo.
