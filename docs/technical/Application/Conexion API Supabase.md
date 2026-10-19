# Conexión API–Supabase (seguridad de los datos)

La app se conecta directo a PostgreSQL a través de la API de Supabase. La protección de los datos depende de **Row Level Security (RLS)**: cada petición llega con el token del usuario y la BD decide qué filas puede ver o modificar con `auth.uid()`.

## 1. Estado actual (revisado el 18/10/2026)

RLS está **activado** en las 6 tablas, pero varias políticas permiten todo (`true`):

| Tabla | Operación | Política actual | Problema |
|---|---|---|---|
| `Usuario` | SELECT / INSERT / UPDATE | `auth.uid() = id` | Correcta |
| `Pagos` | SELECT | `auth.uid() = usuario_id` | Correcta |
| `Estacionamiento` | SELECT | `true` | Correcta (catálogo público) |
| `Imagen_estacionamiento` | SELECT | `true` | Correcta |
| `Cajon` | SELECT | `true` | Correcta |
| `Cajon` | UPDATE | `true` para todos | Cualquiera puede cambiar el estado de un cajón |
| `Cajon` | INSERT | `true` para autenticados | Cualquier usuario puede crear cajones |
| `Reserva` | SELECT | `true` para todos | Se ven las reservas de todos, incluido el `id_usuario` |
| `Reserva` | INSERT | `true` para anon y autenticados | Se puede reservar a nombre de otro usuario, o sin sesión |
| `Reserva` | UPDATE / DELETE | `true` para todos | Cualquiera puede confirmar, extender o borrar reservas ajenas |

## 2. Políticas propuestas


| Tabla | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| `Usuario` | Solo su fila | Solo su fila | Solo su fila | — |
| `Estacionamiento` | Todos | Solo backend | Solo backend | Solo backend |
| `Imagen_estacionamiento` | Todos | Solo backend | Solo backend | Solo backend |
| `Cajon` | Todos | Solo backend | Solo backend (gateway) | Solo backend |
| `Reserva` | Solo las suyas | Solo a su nombre y sin confirmar | Solo las suyas | Solo las suyas y sin confirmar |
| `Pagos` | Solo los suyos | Solo backend (webhook) | — | — |

"Solo backend" significa que no hay política para usuarios: solo la llave `service_role` (que se salta RLS) puede hacerlo. Esa llave la usan las Edge Functions y el gateway de la Raspberry, nunca la app.

### Disponibilidad sin exponer datos de otros usuarios

Si `Reserva` solo muestra las reservas propias, la app ya no podría saber qué horarios están ocupados. Para eso se crea la vista `horarios_ocupados`, que expone solo `id_cajon`, `tiempo_inicio` y `tiempo_fin`, sin el usuario. La app la consulta en lugar de leer `Reserva` directamente.

## 3. Operaciones CRUD

| Tabla | Create | Read | Update | Delete |
|---|---|---|---|---|
| `Usuario` | App al registrarse | App (su perfil) | App (editar perfil) | — |
| `Estacionamiento` | Panel de Supabase | App (todos) | Panel | Panel |
| `Imagen_estacionamiento` | Panel | App (todas) | Panel | Panel |
| `Cajon` | Panel | App (todos) | Gateway (`estado_cajon`) | Panel |
| `Reserva` | App (pendiente) | App (las suyas) + vista `horarios_ocupados` | App (QR, extensión), webhook (confirmar) | App (expira), `pg_cron` (limpieza) |
| `Pagos` | Webhook de Stripe | App (los suyos) | — | — |

## 4. Prueba de aislamiento entre usuarios

Después de aplicar las políticas, con dos cuentas de prueba (A y B) y el token de A:

| # | Petición con el token de A | Resultado esperado |
|---|---|---|
| 1 | `GET /rest/v1/Reserva` | Solo las reservas de A |
| 2 | `GET /rest/v1/Reserva?id=eq.<reserva de B>` | `[]` |
| 3 | `PATCH /rest/v1/Reserva?id=eq.<reserva de B>` con `{ "confirmada": true }` | No modifica nada (0 filas) |
| 4 | `DELETE /rest/v1/Reserva?id=eq.<reserva de B>` | No borra nada |
| 5 | `POST /rest/v1/Reserva` con `id_usuario` de B | 403 (viola la política) |
| 6 | `PATCH /rest/v1/Cajon?id=eq.1` con `{ "estado_cajon": 0 }` | No modifica nada |
| 7 | `GET /rest/v1/Pagos` | Solo los pagos de A |
| 8 | Sin token: `POST /rest/v1/Reserva` | 401 / 403 |

## 5. Cambios necesarios antes de aplicar

1. **Gateway:** debe usar la llave `service_role` en su `.env`, porque con las nuevas políticas la llave anónima ya no podrá actualizar `Cajon` ni leer todas las reservas.
2. **App:** cambiar las consultas de disponibilidad (R1 y R2) para que lean la vista `horarios_ocupados`.
3. **App:** quitar la confirmación de la reserva desde la app (R5); la hará el webhook de Stripe (issue 10.1).
