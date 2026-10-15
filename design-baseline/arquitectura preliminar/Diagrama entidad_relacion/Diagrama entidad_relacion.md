## Modelo entidad-relación

El modelo representa la información que necesita SmartParking para gestionar estacionamientos, cajones, reservas y pagos. Se compone de seis entidades:

| Entidad | Descripción | Llave primaria |
|---|---|---|
| **Usuarios** | Conductores registrados en la aplicación: nombre, correo, teléfono, foto de perfil y su identificador de cliente en Stripe (`customer_id`). | `id_usuario` |
| **Estacionamiento** | Cada centro comercial afiliado, con su nombre, precio por hora y ubicación (latitud y longitud) para mostrarlo en el mapa. | `id` |
| **Imagen_estacionamiento** | Fotografías del estacionamiento que se muestran en la aplicación. | `id_imagen` |
| **Cajon** | Cada espacio físico del estacionamiento, con su número, sección y si está destinado a personas con discapacidad. | `id_cajon` |
| **Reservas** | Apartado de un cajón por un usuario en un horario (`tiempo_inicio` – `tiempo_fin`). Guarda su estado, el token del boleto escaneado (QR) y las fechas de creación y actualización. | `id_reserva` |
| **Pagos** | Cobro asociado a una reserva: subtotal, monto total y fecha de pago. | `id_pago` |

### Relaciones

| Relación | Cardinalidad | Significado |
|---|---|---|
| Usuarios **realiza** Reservas | 1 : N | Un usuario puede hacer muchas reservas; cada reserva pertenece a un solo usuario. |
| Reservas **se asigna** a Cajon | N : 1 | Un cajón puede tener muchas reservas (en distintos horarios); cada reserva ocupa un solo cajón. |
| Estacionamiento **tiene** Cajon | 1 : N | Un estacionamiento contiene muchos cajones; cada cajón pertenece a un estacionamiento. |
| Estacionamiento **tiene** Imagen_estacionamiento | 1 : N | Un estacionamiento puede tener varias imágenes. |
| Reservas **genera** Pagos | 1 : 1 | Cada reserva confirmada genera un pago. |
| Estacionamiento **tiene** Pagos | 1 : N | Un estacionamiento recibe muchos pagos; se guarda para facilitar reportes por estacionamiento. |

### Flujo general

El **usuario** consulta un **estacionamiento**, elige un **cajón** y crea una **reserva** para un horario. Al pagar, la reserva **genera un pago** que queda ligado tanto a la reserva como al estacionamiento, lo que permite consultar el historial de pagos del usuario y los ingresos de cada estacionamiento.