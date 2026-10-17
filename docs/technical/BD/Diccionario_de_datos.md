# Diccionario de datos — SmartParking

Base de datos en **Supabase (PostgreSQL)**, esquema `public`. Todas las fechas `timestamptz` se guardan en **UTC** (México = UTC−6).

**Abreviaturas:** 
- PK = llave primaria 
- FK = llave foránea 
- UQ = valor único
- Nulo = acepta valores vacíos.

## Resumen de tablas

| Tabla | Descripción | PK | FK |
|---|---|---|---|
| `Usuario` | Personas registradas en la app | `id` | — |
| `Estacionamiento` | Centros comerciales con estacionamiento | `id` | — |
| `Imagen_estacionamiento` | Fotos de cada estacionamiento | `id` | `id_estacionamiento` → `Estacionamiento.id` |
| `Cajon` | Lugares de estacionamiento y su estado en tiempo real | `id` | `id_estacionamiento` → `Estacionamiento.id` |
| `Reserva` | Reservas de un cajón hechas por un usuario | `id` | `id_usuario` → `Usuario.id`, `id_cajon` → `Cajon.id` |
| `Pagos` | Pagos realizados con Stripe | — (ver observaciones) | `usuario_id` → `Usuario.id` |

---

## Usuario

Datos de cada persona registrada en la aplicación.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `uuid` | No | `gen_random_uuid()` | PK | Identificador del usuario. Coincide con el id de la cuenta en Supabase Auth. |
| `nombre` | `text` | No | — | | Nombre completo del usuario. |
| `correo` | `text` | No | — | UQ | Correo electrónico con el que inicia sesión. No se puede repetir. |
| `contrasena` | `text` | Sí | — | | Campo heredado; la contraseña la administra Supabase Auth. |
| `telefono` | `text` | Sí | — | | Número de teléfono de contacto. |
| `customer_id` | `varchar` | Sí | — | UQ | Id del cliente en Stripe, para asociar sus pagos. |
| `url_foto_perfil` | `text` | Sí | — | | URL de la foto de perfil en Supabase Storage. |
| `discapacitado` | `boolean` | No | `false` | | Indica si el usuario puede reservar cajones para personas con discapacidad. |

## Estacionamiento

Cada centro comercial que ofrece cajones en la app.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` (identity) | No | Autoincremental | PK | Identificador del estacionamiento. |
| `nombre` | `text` | No | — | | Nombre del centro comercial / estacionamiento. |
| `precio_hora` | `real` | No | — | | Tarifa por hora en pesos mexicanos. |
| `latitud` | `double precision` | No | — | | Latitud para mostrarlo en el mapa. |
| `longitud` | `double precision` | No | — | | Longitud para mostrarlo en el mapa. |
| `url_imagen_cajones` | `text` | Sí | — | | URL de la imagen con el plano o distribución de los cajones. |

## Imagen_estacionamiento

Galería de fotos de cada estacionamiento (un estacionamiento puede tener varias).

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` (identity) | No | Autoincremental | PK | Identificador de la imagen. |
| `id_estacionamiento` | `bigint` | No | — | FK → `Estacionamiento.id` | Estacionamiento al que pertenece la foto. |
| `url` | `text` | No | — | UQ | URL de la imagen. No se puede repetir. |

## Cajon

Cada lugar físico de estacionamiento. Su estado lo actualiza el gateway de la Raspberry Pi a partir de los sensores del Arduino.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` (identity) | No | Autoincremental | PK | Identificador del cajón. Coincide con el número de sensor del Arduino. |
| `numero_cajon` | `smallint` | No | — | | Número visible del cajón dentro de su sección. |
| `discapacitado` | `boolean` | No | — | | Indica si el cajón es exclusivo para personas con discapacidad. |
| `seccion` | `varchar` | No | — | | Sección o zona del estacionamiento (por ejemplo, "A"). |
| `id_estacionamiento` | `bigint` | Sí | — | FK → `Estacionamiento.id` | Estacionamiento al que pertenece el cajón. |
| `estado_cajon` | `smallint` | No | — | | Estado actual: `0` libre, `1` ocupado, `2` reservado. |

## Reserva

Reserva de un cajón por un usuario en un horario. Se crea como pendiente y se confirma cuando se completa el pago.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` (identity) | No | Autoincremental | PK | Identificador de la reserva. |
| `id_usuario` | `uuid` | No | — | FK → `Usuario.id` | Usuario que hizo la reserva. |
| `id_cajon` | `bigint` | No | — | FK → `Cajon.id` | Cajón reservado. |
| `tiempo_inicio` | `timestamptz` | No | — | | Fecha y hora de inicio de la reserva (UTC). |
| `tiempo_fin` | `timestamptz` | No | — | | Fecha y hora de fin de la reserva (UTC). |
| `confirmada` | `boolean` | No | — | | `true` cuando el pago se completó; `false` mientras está pendiente. |
| `token_estacionamiento` | `text` | Sí | — | | Código del QR. Se llena cuando el usuario lo escanea al llegar; confirma que quien reservó ocupa el cajón. |
| `created_at` | `timestamptz` | No | `now()` en UTC | | Fecha en que se creó la reserva. |
| `expiracion` | `timestamptz` | Sí | `now() + 5 min` | | Límite para pagar una reserva pendiente; después se descarta. |

## Pagos

Registro de cada pago hecho con Stripe para una reserva o una extensión de tiempo.

| Campo | Tipo | Nulo | Por defecto | Llave | Descripción |
|---|---|---|---|---|---|
| `id` | `bigint` | No | `nextval('Pagos_id_seq')` | | Identificador del pago (autoincremental). |
| `id_reserva` | `text` | No | — | | Reserva que se pagó. |
| `id_usuario` | `varchar` | Sí | — | | Usuario que pagó (como texto). |
| `id_estacionamiento` | `text` | Sí | — | | Estacionamiento donde se hizo la reserva. |
| `monto_total` | `double precision` | No | — | | Monto final cobrado en pesos, con descuentos aplicados. |
| `subtotal` | `double precision` | Sí | — | | Monto antes de descuentos o cupones. |
| `metadata` | `jsonb` | Sí | — | | Datos adicionales del pago en Stripe (id del pago, cupón, etc.). |
| `fecha_pago` | `timestamptz` | No | `now()` | | Fecha y hora del pago (UTC). |
| `usuario_id` | `uuid` | Sí | `auth.uid()` | FK → `Usuario.id` | Usuario autenticado que hizo el pago; se llena solo con la sesión activa. |

---

## Relaciones

| Relación | Cardinalidad | Significado |
|---|---|---|
| `Estacionamiento` → `Cajon` | 1 : N | Un estacionamiento tiene muchos cajones. |
| `Estacionamiento` → `Imagen_estacionamiento` | 1 : N | Un estacionamiento tiene varias fotos. |
| `Usuario` → `Reserva` | 1 : N | Un usuario puede hacer muchas reservas. |
| `Cajon` → `Reserva` | 1 : N | Un cajón puede reservarse muchas veces en distintos horarios. |
| `Usuario` → `Pagos` | 1 : N | Un usuario puede tener muchos pagos. |
| `Reserva` → `Pagos` | 1 : N (lógica) | Una reserva puede tener su pago inicial y pagos de extensión. No tiene FK en la BD. |

## Observaciones

Puntos del esquema actual que conviene corregir en una siguiente versión:

1. **`Pagos` no tiene llave primaria.** `id` es autoincremental pero no está declarado como `PRIMARY KEY`.
2. **`Pagos.id_reserva` e `id_estacionamiento` son `text`** y no tienen FK; deberían ser `bigint` con FK a `Reserva.id` y `Estacionamiento.id`.
3. **`Pagos` guarda al usuario dos veces:** `id_usuario` (texto) y `usuario_id` (uuid con FK). Basta con `usuario_id`.
4. **`Usuario.contrasena`** no debería existir: las contraseñas las guarda Supabase Auth de forma segura.
5. **`Cajon.id_estacionamiento` acepta nulos**; todo cajón debería pertenecer a un estacionamiento.
6. **`Reserva` no impide que dos reservas confirmadas del mismo cajón se traslapen** en horario; se puede agregar una restricción `EXCLUDE`.
