# Conexión de endpoints

La app no tiene un servidor propio: se comunica directamente con **Supabase**, que expone la base de datos como API REST (PostgREST), la autenticación (GoTrue), el almacenamiento de archivos y las Edge Functions.

**URL base:** `https://kjbmwwbzdsvunzndeusf.supabase.co`

## Encabezados

Todas las peticiones llevan:

| Encabezado | Valor | Quién lo agrega |
|---|---|---|
| `apikey` | Llave publicable (anon) del proyecto | `ApiClient` |
| `Authorization` | `Bearer <access_token del usuario>` | `ApiClient` (si hay sesión) |
| `Content-Type` | `application/json` | Cada petición con cuerpo |
| `Prefer` | `return=representation` o `return=minimal` | Inserciones y actualizaciones |

`ApiClient` es el único cliente HTTP de la app. Si el token expiró (respuesta 401), lo renueva con el `refresh_token` y repite la petición.

## Autenticación (`/auth/v1`)

| # | Método | Endpoint | Cuerpo / parámetros | Respuesta | Usado en |
|---|---|---|---|---|---|
| A1 | POST | `/auth/v1/signup` | `{ email, password, data: { nombre } }` | `{ user, access_token?, refresh_token? }` | `Registro` |
| A2 | POST | `/auth/v1/token?grant_type=password` | `{ email, password }` | `{ access_token, refresh_token, expires_in, user }` · 400 si son incorrectas | `InicioSesion`, `EditarContrasena` (verifica la actual) |
| A3 | POST | `/auth/v1/token?grant_type=refresh_token` | `{ refresh_token }` | Tokens nuevos | `SessionManager` |
| A4 | PUT | `/auth/v1/user` | `{ password }` o `{ email }` | Usuario actualizado | `EditarContrasena`, `EditarDatosPersonales` |

## Base de datos (`/rest/v1`)

### Usuario

| # | Método | Endpoint | Parámetros / cuerpo | Respuesta | Usado en |
|---|---|---|---|---|---|
| U1 | POST | `/rest/v1/Usuario` | `{ id, nombre, correo, telefono, discapacitado }` | 201 | Registro |
| U2 | GET | `/rest/v1/Usuario` | `id=eq.<uuid>`, `select=nombre,url_foto_perfil` | `[ { nombre, url_foto_perfil } ]` | Login, Perfil |
| U3 | GET | `/rest/v1/Usuario` | `id=eq.<uuid>`, `select=discapacitado` | `[ { discapacitado } ]` | Selección de cajón |
| U4 | PATCH | `/rest/v1/Usuario` | `id=eq.<uuid>` · `{ nombre, telefono, url_foto_perfil }` | 204 | Editar perfil / datos |

### Estacionamiento e imágenes

| # | Método | Endpoint | Parámetros | Respuesta | Usado en |
|---|---|---|---|---|---|
| E1 | GET | `/rest/v1/Estacionamiento` | `select=id,nombre,precio_hora,latitud,longitud` | Lista de estacionamientos | Inicio, Mapa |
| E2 | GET | `/rest/v1/Estacionamiento` | `id=eq.<id>`, `select=url_imagen_cajones` | `[ { url_imagen_cajones } ]` | Selección de cajón |
| E3 | GET | `/rest/v1/Imagen_estacionamiento` | `id_estacionamiento=eq.<id>`, `select=url` | Lista de URLs | Ficha del Mapa |

### Cajón

| # | Método | Endpoint | Parámetros | Respuesta | Usado en |
|---|---|---|---|---|---|
| C1 | GET | `/rest/v1/Cajon` | `id_estacionamiento=eq.<id>`, `order=seccion,numero_cajon` | Lista de cajones con `estado_cajon` | Selección de cajón |
| C2 | PATCH | `/rest/v1/Cajon` | `id=eq.<id>` · `{ estado_cajon: 0 \| 1 \| 2 }` | 204 | **Gateway** de la Raspberry Pi |

### Reserva

| # | Método | Endpoint | Parámetros / cuerpo | Respuesta | Usado en |
|---|---|---|---|---|---|
| R1 | GET | `/rest/v1/Reserva` | `id_cajon=in.(…)`, `tiempo_inicio=lt.<fin del día>`, `tiempo_fin=gt.<inicio del día>` | Reservas de hoy | Selección de cajón |
| R2 | GET | `/rest/v1/Reserva` | `id_cajon=eq.<id>`, `tiempo_inicio=lt.<fin>`, `tiempo_fin=gt.<inicio>`, `id=neq.<id>` | `[]` si está libre | Antes de reservar y de extender |
| R3 | POST | `/rest/v1/Reserva` | `{ id_usuario, id_cajon, tiempo_inicio, tiempo_fin, confirmada: false }` | Reserva creada | Crear reserva pendiente |
| R4 | GET | `/rest/v1/Reserva` | `id_usuario=eq.<uuid>`, `tiempo_fin=gte.<ahora>`, `select=*,Cajon(*,Estacionamiento(nombre,precio_hora))` | Reserva activa del usuario | Sincronizar al abrir la app |
| R5 | PATCH | `/rest/v1/Reserva` | `id=eq.<id>` · `{ confirmada: true }` | 204 | Tras el pago |
| R6 | PATCH | `/rest/v1/Reserva` | `id=eq.<id>` · `{ token_estacionamiento }` | 204 | Escanear QR |
| R7 | PATCH | `/rest/v1/Reserva` | `id=eq.<id>` · `{ tiempo_fin }` | 204 | Extensión de tiempo |
| R8 | DELETE | `/rest/v1/Reserva` | `id=eq.<id>` | 204 | Expira la reserva pendiente |
| R9 | GET | `/rest/v1/Reserva` | `confirmada=is.true`, `tiempo_inicio=lte.<ahora>`, `tiempo_fin=gt.<ahora>` | Reservas en curso | **Gateway** |

### Pagos

| # | Método | Endpoint | Parámetros | Respuesta | Usado en |
|---|---|---|---|---|---|
| P1 | GET | `/rest/v1/Pagos` | `usuario_id=eq.<uuid>`, `order=fecha_pago.desc` | Historial de pagos | Historial |

Los pagos no los inserta la app: los registra el backend (webhook de Stripe).

## Almacenamiento (`/storage/v1`)

| # | Método | Endpoint | Cuerpo | Respuesta | Usado en |
|---|---|---|---|---|---|
| S1 | POST | `/storage/v1/object/<bucket>/<archivo>` | Imagen | 200 | Subir foto de perfil |
| S2 | GET | `/storage/v1/object/public/<bucket>/<archivo>` | — | Imagen | Mostrar foto de perfil |

## Edge Functions (`/functions/v1`)

| # | Método | Endpoint | Cuerpo | Respuesta | Usado en |
|---|---|---|---|---|---|
| F1 | POST | `/functions/v1/crear-pago-reserva` | `{ monto, id_reserva, id_usuario, id_estacionamiento }` | `{ clientSecret, customerId }` | Carrito y extensión |
| F2 | POST | `/functions/v1/stripe-webhook` | Evento de Stripe (firmado) | 200 | **Stripe** (no la app) |


