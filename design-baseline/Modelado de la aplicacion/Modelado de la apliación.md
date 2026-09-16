## Descripción del modelo

### (P1): Inicio de sesión

Ingresar correo electrónico y contraseña en los campos de texto, presiona el botón “Continuar” para validar sus credenciales.

El sistema recibe las credenciales ingresadas, realiza la sanitización y validación contra la base de datos:
- Verifica que el correo y usuario exista.
- Comprueba que el hash calculado para la contraseña ingresada coincida con la del respectivo usuario.
- Si los datos son correctos, inicia la sesión y redirige al usuario a la pantalla principal.
- Si las credenciales no son válidas, muestra un mensaje de error indicando “Usuario o contraseña incorrectos”.
![[P1_Inicio de sesion.png]]
### (P2): Registro

Ingresa la información solicitada en los campos de texto.

El sistema valida que todos los campos requeridos estén completos y correctos (únicamente con carácteres permitidos).

Verifica que el correo no esté registrado previamente.

Comprueba que las contraseñas coincidan y cumplan los criterios de seguridad. A la contraseña se le aplica una función de encriptado para no guardarla como texto plano.

Si todo es correcto, almacena la información del nuevo usuario en la base de datos y genera su cuenta. Si hay errores, muestra mensajes de advertencia para que el usuario los corrija.

![[P2_Registro.png]]
### (P3): Home (inicio)

En caso de que no cuente con una reserva, el usuario presiona el botón “reservar” y lo lleva a la vista para iniciar la reserva. Si cuenta con una reserva puede extender el tiempo de estacionamiento, mediante botones para aumentar o reducir el tiempo añadido, pudiendo reiniciar o confirmar lo ingresado.

Interactúa con la barra de navegación inferior para desplazarse entre las distintas secciones:
- **Inicio:** Regresa a la pantalla principal. 
- **Reserva:** Accede al mapa y listado de estacionamientos disponibles. 
- **Notificaciones:** Consulta avisos o mensajes del sistema (reservas realizadas, cambios en la reserva). 
- **Perfil:** Visualiza y edita la información personal asociada a su cuenta.
![[P3_Home.png]]

### (P4): Inicio con Reserva

El usuario puede escanear el código QR que le dará el acceso y la salida del estacionamiento.

Podrá visualizar la descripción de su reserva, el tiempo de su reserva con la opción a extender la duración de ésta.
![[P4_Inicio con reserva.png]]
### (P5): Selección de lugar

Selecciona la hora y el cajón requeridos para la reserva.

Confirma la acción presionando el botón “Continuar”, procediendo al pago.

Puede regresar a la pantalla de reserva mediante el botón de navegación.

Muestra los campos disponibles para crear una nueva reserva (fecha, hora, cajón y tipo de cajón).

Una vez seleccionado los datos necesarios para la reserva, se almacenan como variables locales durante los 5 minutos de tiempo dado, se consulta la disponibilidad de los lugares del estacionamiento para evitar posibles errores de condiciones de carrera, marcando como ocupado (en estado pendiente) los lugares ya seleccionados por el usuario al dar clic en continuar; al usuario se le redirige a la pantalla de pagar junto con los datos de la reserva.

![[P5_Seleccion de lugar.png]]
### (P6): Pago

Consultar el resumen de la orden. Confirmar el pago para su realización.

El usuario selecciona pagar. La aplicación envía una solicitud al servidor con los datos de la transacción: el monto total, la descripción del servicio o reserva y la identificación del usuario.

El servidor recibe la solicitud y crea una intención de pago mediante la API de Stripe y se definen los parámetros principales

![[P6_Pago.png]]
### (P7): Perfil del usuario

Se puede visualizar una pequeña foto del usuario, con su nombre además la interfaz cuenta con diversos botones que se redireccionan a otras interfaces como, el método de pago, el historial y la edición de los datos.
![[P7_Perfil de usuario.png]]