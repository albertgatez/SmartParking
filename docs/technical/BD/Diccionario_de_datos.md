# Diccionario de datos - SmartParking

Base de datos en **Supabase (PostgreSQL)**, esquema `public`. Todas las fechas `timestamptz` se guardan en **UTC** (México = UTC−6).

**Abreviaturas:** 
- PK = llave primaria 
- FK = llave foránea 
- UQ = valor único
- Nulo = acepta valores vacíos.

## Resumen de tablas

| Tabla | Descripción | PK | FK |
|---|---|---|---|
| `Usuario` | Personas registradas en la app | `id` | - |
| `Estacionamiento` | Centros comerciales con estacionamiento | `id` | - |
| `Imagen_estacionamiento` | Fotos de cada estacionamiento | `id` | `id_estacionamiento` → `Estacionamiento.id` |
| `Cajon` | Lugares de estacionamiento y su estado en tiempo real | `id` | `id_estacionamiento` → `Estacionamiento.id` |
| `Reserva` | Reservas de un cajón hechas por un usuario | `id` | `id_usuario` → `Usuario.id`, `id_cajon` → `Cajon.id` |
| `Pagos` | Pagos realizados con Stripe | - | `usuario_id` → `Usuario.id` |
| `Notificacion` | Avisos de cada usuario: historial para la pestaña Avisos y bandeja de salida de las notificaciones push | `id` | `id_usuario` → `Usuario.id`, `id_reserva` → `Reserva.id` |

---

## Usuario

Datos de cada persona registrada en la aplicación.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `uuid` | No | `gen_random_uuid()` | PK | Identificador del usuario. Coincide con el id de la cuenta en Supabase Auth. |
| `nombre` | `text` | No | - |- | Nombre completo del usuario. |
| `correo` | `text` | No | - | UQ | Correo electrónico con el que inicia sesión. No se puede repetir. |
| `contrasena` | `text` | Sí | - | -| Campo heredado; la contraseña la administra Supabase Auth. |
| `telefono` | `text` | Sí | - |- | Número de teléfono de contacto. |
| `customer_id` | `varchar` | Sí | - | UQ | Id del cliente en Stripe, para asociar sus pagos. |
| `url_foto_perfil` | `text` | Sí | - |- | URL de la foto de perfil en Supabase Storage. |
| `discapacitado` | `boolean` | No | `false` | -| Indica si el usuario puede reservar cajones para personas con discapacidad. |
| `fcm_token` | `text` | Sí | - | -| Token de Firebase Cloud Messaging del teléfono con la sesión abierta; el servidor lo usa para mandarle notificaciones push. Se guarda al iniciar sesión y se borra (`null`) al cerrar sesión o cuando FCM informa que ya no es válido. |
| `bienvenida_enviada` | `boolean` | No | `false` | -| `true` cuando ya se mandó la notificación de bienvenida, para no repetirla. Los usuarios que ya existían al agregar la columna se marcaron como `true`. |

## Estacionamiento

Cada centro comercial que ofrece cajones en la app.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` (identity) | No | Autoincremental | PK | Identificador del estacionamiento. |
| `nombre` | `text` | No | - |- | Nombre del centro comercial / estacionamiento. |
| `precio_hora` | `real` | No | - | -| Tarifa por hora en pesos mexicanos. |
| `latitud` | `double precision` | No | - | -| Latitud para mostrarlo en el mapa. |
| `longitud` | `double precision` | No | - |- | Longitud para mostrarlo en el mapa. |
| `url_imagen_cajones` | `text` | Sí | - | - | URL de la imagen con el plano o distribución de los cajones. |

## Imagen_estacionamiento

Galería de fotos de cada estacionamiento (un estacionamiento puede tener varias).

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` (identity) | No | Autoincremental | PK | Identificador de la imagen. |
| `id_estacionamiento` | `bigint` | No | - | FK → `Estacionamiento.id` | Estacionamiento al que pertenece la foto. |
| `url` | `text` | No | - | UQ | URL de la imagen. No se puede repetir. |

## Cajon

Cada lugar físico de estacionamiento. Su estado lo actualiza el gateway de la Raspberry Pi a partir de los sensores del Arduino.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` (identity) | No | Autoincremental | PK | Identificador del cajón. Coincide con el número de sensor del Arduino. |
| `numero_cajon` | `smallint` | No | - | - | Número visible del cajón dentro de su sección. |
| `discapacitado` | `boolean` | No | - | - | Indica si el cajón es exclusivo para personas con discapacidad. |
| `seccion` | `varchar` | No | - | - | Sección o zona del estacionamiento (por ejemplo, "A"). |
| `id_estacionamiento` | `bigint` | Sí | - | FK → `Estacionamiento.id` | Estacionamiento al que pertenece el cajón. |
| `estado_cajon` | `smallint` | No | - | - | Estado actual: `0` libre, `1` ocupado, `2` reservado. |

Cada cambio de `estado_cajon` ejecuta el trigger `cajon_estado_notificar`, que avisa al usuario con una reserva confirmada en curso en ese cajón (ver [Triggers y tareas programadas de notificaciones](#triggers-y-tareas-programadas-de-notificaciones)).

## Reserva

Reserva de un cajón por un usuario en un horario. Se crea como pendiente y se confirma cuando se completa el pago.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` (identity) | No | Autoincremental | PK | Identificador de la reserva. |
| `id_usuario` | `uuid` | No | - | FK → `Usuario.id` | Usuario que hizo la reserva. |
| `id_cajon` | `bigint` | No | - | FK → `Cajon.id` | Cajón reservado. |
| `tiempo_inicio` | `timestamptz` | No | - | | Fecha y hora de inicio de la reserva (UTC). |
| `tiempo_fin` | `timestamptz` | No | - | -| Fecha y hora de fin de la reserva (UTC). |
| `confirmada` | `boolean` | No | - |- | `true` cuando el pago se completó; `false` mientras está pendiente. |
| `token_estacionamiento` | `text` | Sí | - |- | Código del QR. Se llena cuando el usuario lo escanea al llegar; confirma que quien reservó ocupa el cajón. |
| `created_at` | `timestamptz` | No | `now()` en UTC |- | Fecha en que se creó la reserva. |
| `expiracion` | `timestamptz` | Sí | `now() + 5 min` | -| Límite para pagar una reserva pendiente; después se descarta. |

Los cambios de `confirmada` (pago recibido) y de `tiempo_fin` (extensión) ejecutan el trigger `reserva_notificar`, y la tarea `recordatorios-fin-reserva` avisa cuando faltan 15 minutos o menos para `tiempo_fin`.

## Pagos

Registro de cada pago hecho con Stripe para una reserva o una extensión de tiempo.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` | No | `nextval('Pagos_id_seq')` | | Identificador del pago (autoincremental). |
| `id_reserva` | `text` | No | - |- | Reserva que se pagó. |
| `id_usuario` | `varchar` | Sí | - |- | Usuario que pagó (como texto). |
| `id_estacionamiento` | `text` | Sí | - |- | Estacionamiento donde se hizo la reserva. |
| `monto_total` | `double precision` | No | - |- | Monto final cobrado en pesos, con descuentos aplicados. |
| `subtotal` | `double precision` | Sí | - |- | Monto antes de descuentos o cupones. |
| `metadata` | `jsonb` | Sí | - |- | Datos adicionales del pago en Stripe (id del pago, cupón, etc.). |
| `fecha_pago` | `timestamptz` | No | `now()` |- | Fecha y hora del pago (UTC). |
| `usuario_id` | `uuid` | Sí | `auth.uid()` | FK → `Usuario.id` | Usuario autenticado que hizo el pago; se llena solo con la sesión activa. |

## Notificacion

Avisos de cada usuario. Cumple dos funciones: es el **historial** que muestra la pestaña Avisos de la app y la **bandeja de salida** de las notificaciones push (cada fila nueva se manda a Firebase mediante la Edge Function `enviar-notificacion`). La app solo puede leer sus propios avisos; los inserta únicamente el servidor.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` (identity) | No | Autoincremental (`GENERATED ALWAYS`) | PK | Identificador del aviso. |
| `id_usuario` | `uuid` | No | - | FK → `Usuario.id` | Usuario que recibe el aviso. |
| `id_reserva` | `bigint` | Sí | - | FK → `Reserva.id` | Reserva que originó el aviso, si aplica. Queda en `null` si la reserva se borra. |
| `tipo` | `text` | No | - | - | Tipo de aviso (ver tabla siguiente). |
| `titulo` | `text` | No | - | - | Título de la notificación. |
| `mensaje` | `text` | No | - | - | Texto de la notificación. |
| `leida` | `boolean` | No | `false` | - | Reservado para marcar avisos como leídos; la app todavía no lo usa. |
| `push_enviada` | `boolean` | No | `false` | - | `true` cuando Firebase aceptó el mensaje. Sirve para diagnosticar envíos; `false` si el usuario no tenía `fcm_token` o el envío falló. |
| `created_at` | `timestamptz` | No | `now()` | - | Fecha y hora del aviso (UTC). |

### Valores de `tipo`

| `tipo` | Cuándo se crea | Origen |
|---|---|---|
| `reserva_confirmada` | `Reserva.confirmada` pasa de `false` a `true` (pago recibido) | Trigger `reserva_notificar` |
| `reserva_extendida` | Una reserva ya confirmada aumenta su `tiempo_fin` (pago de extensión) | Trigger `reserva_notificar` |
| `recordatorio_fin` | Faltan 15 minutos o menos para el `tiempo_fin` de una reserva confirmada en curso | Tarea `pg_cron` cada minuto |
| `cajon_reservado` | `Cajon.estado_cajon` pasa a `2` al empezar la reserva | Trigger `cajon_estado_notificar` |
| `cajon_ocupado` | `Cajon.estado_cajon` pasa a `1` con una reserva confirmada en curso | Trigger `cajon_estado_notificar` |
| `cajon_desocupado` | `Cajon.estado_cajon` pasa de `1` a `2`: el vehículo se fue y la reserva sigue vigente | Trigger `cajon_estado_notificar` |

La notificación de **bienvenida** no se guarda en esta tabla: la manda directamente la Edge Function `notificar-bienvenida` y se controla con `Usuario.bienvenida_enviada`.

### Índices

| Índice | Columnas | Para qué sirve |
|---|---|---|
| `notificacion_usuario_fecha_idx` | `(id_usuario, created_at DESC)` | Listar los avisos de un usuario, del más reciente al más antiguo (pestaña Avisos). |
| `notificacion_reserva_tipo_idx` | `(id_reserva, tipo)` | Saber si una reserva ya tiene un aviso de cierto tipo (recordatorio y antirrebote). |

### Seguridad (RLS)

| Operación | Política | Quién |
|---|---|---|
| SELECT | `notificacion_select_propias`: `auth.uid() = id_usuario` | Usuario autenticado, solo sus avisos |
| INSERT / UPDATE / DELETE | Ninguna | Solo los triggers (`SECURITY DEFINER`) y la llave `service_role` de las Edge Functions |

---

## Triggers y tareas programadas de notificaciones

Las funciones se ejecutan con `SECURITY DEFINER` y `search_path = public`, y atrapan sus propios errores (`EXCEPTION WHEN OTHERS`, solo dejan un `WARNING`): una falla al notificar nunca impide confirmar una reserva ni actualizar un cajón.

| Elemento | Tabla | Tipo | Cuándo se ejecuta | Qué hace |
|---|---|---|---|---|
| `reserva_notificar` → `reserva_notificar_cambios()` | `Reserva` | Trigger `AFTER UPDATE OF confirmada, tiempo_fin` | Cambia `confirmada` o `tiempo_fin` | Inserta `reserva_confirmada` o `reserva_extendida` en `Notificacion`. |
| `cajon_estado_notificar` → `cajon_notificar_cambio_estado()` | `Cajon` | Trigger `AFTER UPDATE OF estado_cajon` | Cambia `estado_cajon` | Si hay una reserva confirmada en curso en el cajón, inserta `cajon_reservado`, `cajon_ocupado` o `cajon_desocupado`. No avisa el paso a `0` (libre) y evita repetir el mismo `tipo` para una reserva en menos de 1 minuto. |
| `recordatorios-fin-reserva` → `notificar_reservas_por_terminar()` | `Reserva` | Tarea de `pg_cron` | Cada minuto | Inserta `recordatorio_fin` para reservas confirmadas en curso con `tiempo_fin` en los próximos 15 minutos que aún no tienen ese aviso en la ventana actual. Si la reserva se extiende, el aviso se vuelve a generar. |
| `notificacion_enviar_push` → `supabase_functions.http_request` | `Notificacion` | Trigger `AFTER INSERT` | Cada aviso nuevo | Llama a la Edge Function `enviar-notificacion` (con `pg_net`, de forma asíncrona y autenticada con el header `x-webhook-secret`), que manda el push y marca `push_enviada = true`. |

Migraciones: `20261105_notificaciones_token.sql` (columnas de `Usuario`), `20261106_notificaciones_eventos.sql` (tabla, triggers y recordatorio) y `20261106_notificaciones_envio.sql` (trigger del push), en `software/supabase/migrations/`. El detalle de cada una está en `docs/technical/Application/Notificaciones en Supabase.md`.

---

## Relaciones

| Relación | Cardinalidad | Significado |
|---|---|---|
| `Estacionamiento` → `Cajon` | 1 : N | Un estacionamiento tiene muchos cajones. |
| `Estacionamiento` → `Imagen_estacionamiento` | 1 : N | Un estacionamiento tiene varias fotos. |
| `Usuario` → `Reserva` | 1 : N | Un usuario puede hacer muchas reservas. |
| `Cajon` → `Reserva` | 1 : N | Un cajón puede reservarse muchas veces en distintos horarios. |
| `Usuario` → `Pagos` | 1 : N | Un usuario puede tener muchos pagos. |
| `Reserva` → `Pagos` | 1 : N | Una reserva puede tener su pago inicial y pagos de extensión. No tiene FK en la BD. |
| `Usuario` → `Notificacion` | 1 : N | Un usuario recibe muchos avisos. |
| `Reserva` → `Notificacion` | 1 : N (opcional) | Una reserva puede originar varios avisos (confirmación, extensiones, recordatorio, cambios del cajón). `id_reserva` puede ser `null`. |

## Observaciones

Puntos del esquema actual que conviene corregir en una siguiente versión:

1. **`Pagos` no tiene llave primaria.** `id` es autoincremental pero no está declarado como `PRIMARY KEY`.
2. **`Pagos.id_reserva` e `id_estacionamiento` son `text`** y no tienen FK; deberían ser `bigint` con FK a `Reserva.id` y `Estacionamiento.id`.
3. **`Pagos` guarda al usuario dos veces:** `id_usuario` (texto) y `usuario_id` (uuid con FK). Basta con `usuario_id`.
4. **`Usuario.contrasena`** no debería existir: las contraseñas las guarda Supabase Auth de forma segura.
5. **`Cajon.id_estacionamiento` acepta nulos**; todo cajón debería pertenecer a un estacionamiento.
6. **`Reserva` no impide que dos reservas confirmadas del mismo cajón se traslapen** en horario; se puede agregar una restricción `EXCLUDE`.
7. **`Notificacion.tipo` es texto libre.** No hay un `CHECK` que limite los valores a los seis tipos definidos; un error de escritura en un trigger crearía un tipo nuevo sin aviso.
8. **`Notificacion.leida` no se usa.** Para marcar avisos como leídos falta una política `UPDATE` (solo sobre `leida`) y que la app la llame al abrir Avisos.
9. **`Notificacion` crece sin límite.** Si se agrega una limpieza, solo debe borrar avisos de más de un día: borrar un `recordatorio_fin` todavía vigente podría hacer que se vuelva a mandar.
10. **`Usuario.fcm_token` guarda un solo token por usuario.** Con sesión abierta en dos teléfonos, solo el último que guardó su token recibe las notificaciones push (el historial en Avisos sí se ve en ambos).
11. **El secreto del trigger `notificacion_enviar_push` está en su definición.** En producción conviene moverlo a Supabase Vault.