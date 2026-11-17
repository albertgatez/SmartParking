# Conexión API–Supabase — SmartParking

La app se conecta directo a PostgreSQL a través de la API de Supabase (PostgREST). La protección de los datos depende de **Row Level Security (RLS)**: cada petición llega con el token del usuario y la BD decide qué filas puede ver o modificar con `auth.uid()`.

Los endpoints que usa la app están en [Conexión de endpoints](Conexion%20de%20endpoints.md).

## 1. Quién accede y con qué llave

| Quién | Llave | ¿Le aplica RLS? | Notas |
|---|---|---|---|
| App Android | Llave publicable (`anon`) + token del usuario (`Authorization: Bearer`) | Sí | `ApiClient` agrega los dos encabezados y renueva el token si expira |
| Edge Functions (`crear-pago-reserva-v2`, `metodos-pago`, `stripe-webhook`, `notificar-bienvenida`, `enviar-notificacion`) | `service_role` | No | Cada función valida a quién atiende: las de la app toman el usuario del JWT (`admin.auth.getUser`); `stripe-webhook` verifica la firma de Stripe; `enviar-notificacion` verifica el header `x-webhook-secret` |
| Gateway de la Raspberry Pi | `service_role` (en su `.env`) | No | Actualiza `Cajon.estado_cajon` y lee las reservas en curso |
| Triggers y tareas `pg_cron` | `SECURITY DEFINER` | No | Insertan avisos en `Notificacion` y limpian reservas pendientes vencidas |

La llave `service_role` nunca está en la app.

## 2. Políticas actuales (verificado el 17/11/2026)

RLS está **activado en las 7 tablas**. Políticas que existen en la BD:

| Tabla | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| `Usuario` | Solo su fila (`auth.uid() = id`) | Solo su fila; columnas `id`, `nombre`, `correo`, `telefono`, `discapacitado` | Solo su fila; columnas `nombre`, `correo`, `telefono`, `discapacitado`, `url_foto_perfil`, `fcm_token` | — |
| `Estacionamiento` | Todos | — | — | — |
| `Imagen_estacionamiento` | Todos | — | — | — |
| `Cajon` | Todos | — | — | — |
| `Reserva` | Solo las suyas | Solo a su nombre y sin confirmar; columnas `id_usuario`, `id_cajon`, `tiempo_inicio`, `tiempo_fin`, `confirmada` | Solo las suyas; solo la columna `token_estacionamiento` | Solo las suyas y sin confirmar |
| `Pagos` | Solo los suyos (`auth.uid() = usuario_id`) | — | — | — |
| `Notificacion` | Solo las suyas | — | Solo las suyas; solo la columna `oculta` | — |

"—" significa que no hay política para usuarios: solo el backend (`service_role`, triggers) puede hacerlo. Las columnas que se indican se limitan con permisos por columna (`GRANT INSERT/UPDATE (columnas)`).

Además, el trigger `trg_reserva_proteger_confirmada` rechaza cualquier cambio de `confirmada` hecho con el rol `authenticated`, como segunda barrera.

La disponibilidad de todos los usuarios se consulta con la vista **`horarios_ocupados`** (`id_cajon`, `tiempo_inicio`, `tiempo_fin`, `id`; sin datos del usuario). La vista es **solo de lectura** para `anon` y `authenticated`.

### Migraciones de seguridad

| Migración | Qué hizo | Estado |
|---|---|---|
| `20261106_rls_etapa1.sql` | Reservas: insertar, modificar y borrar solo las propias. Cajones: solo el gateway los modifica. Crea la vista `horarios_ocupados` | Aplicada el 06/11/2026 |
| `20261109_notificaciones_ocultar.sql` | Columna `oculta` y política para ocultar avisos propios | Aplicada |
| `20261117_rls_preparacion_etapa2.sql` | Vista `horarios_ocupados` solo de lectura y con la columna `id`; quita el permiso de ejecutar `obtener_cajones_con_reservas_hoy` | Aplicada el 17/11/2026 |
| `20261106_rls_etapa2.sql` | Reservas: cada usuario solo ve las suyas. Permisos por columna en `Reserva` y `Usuario` (riesgos 1 a 3 de la sección 5) | Aplicada el 17/11/2026 |

Los archivos están en `software/supabase/migrations/`.

## 3. Operaciones CRUD

| Tabla | Create | Read | Update | Delete |
|---|---|---|---|---|
| `Usuario` | App al registrarse | App (su perfil) | App (perfil, foto, `fcm_token`); `notificar-bienvenida` (`bienvenida_enviada`); funciones de pago (`customer_id`) | — |
| `Estacionamiento` | Panel de Supabase | App (todos) | Panel | Panel |
| `Imagen_estacionamiento` | Panel | App (todas) | Panel | Panel |
| `Cajon` | Panel | App (todos) | Gateway (`estado_cajon`) | Panel |
| `Reserva` | App (pendiente) | App (la suya; la disponibilidad de todos por `horarios_ocupados`) | App (`token_estacionamiento` al escanear el QR); `stripe-webhook` (`confirmada`, `tiempo_fin` de la extensión) | App (expira); `pg_cron` (pendientes vencidas) |
| `Pagos` | `stripe-webhook` | App (los suyos) | — | — |
| `Notificacion` | Triggers y `pg_cron` | App (las suyas, `oculta = false`) | App (`oculta`); `enviar-notificacion` (`push_enviada`) | — |

## 4. Prueba de aislamiento entre usuarios

Con dos cuentas de prueba (A y B), usando el token de A:

| # | Petición con el token de A | Resultado esperado | Hoy |
|---|---|---|---|
| 1 | `GET /rest/v1/Reserva` | Solo las reservas de A | Cumple |
| 2 | `GET /rest/v1/Reserva?id=eq.<reserva de B>` | `[]` | Cumple |
| 3 | `PATCH /rest/v1/Reserva?id=eq.<reserva de B>` con `{ "confirmada": true }` | No modifica nada (0 filas) | Cumple |
| 4 | `DELETE /rest/v1/Reserva?id=eq.<reserva de B>` | No borra nada | Cumple |
| 5 | `POST /rest/v1/Reserva` con `id_usuario` de B | 403 (viola la política) | Cumple |
| 6 | `PATCH /rest/v1/Cajon?id=eq.1` con `{ "estado_cajon": 0 }` | No modifica nada | Cumple |
| 7 | `GET /rest/v1/Pagos` | Solo los pagos de A | Cumple |
| 8 | Sin token: `POST /rest/v1/Reserva` | 401 / 403 | Cumple |
| 9 | `GET /rest/v1/Notificacion` | Solo los avisos de A | Cumple |
| 10 | `PATCH /rest/v1/Notificacion?id=eq.<aviso de B>` con `{ "oculta": true }` | `[]` (0 filas) | Cumple |
| 11 | `PATCH /rest/v1/Notificacion?id=eq.<aviso de A>` con `{ "titulo": "x" }` | 401/403 (sin permiso sobre la columna) | Cumple |
| 12 | `PATCH /rest/v1/Reserva?id=eq.<reserva de A>` con `{ "confirmada": true }` o `{ "tiempo_fin": … }` | 401/403 (sin permiso sobre la columna) | Cumple |
| 13 | `PATCH /rest/v1/Reserva?id=eq.<reserva de A>` con `{ "token_estacionamiento": "…" }` | Se modifica (1 fila) | Cumple |
| 14 | `PATCH /rest/v1/Usuario?id=eq.<A>` con `{ "customer_id": "cus_…" }` | 401/403 (sin permiso sobre la columna) | Cumple |
| 15 | Sin token: `DELETE /rest/v1/horarios_ocupados?id_cajon=eq.1` | 401/403 (la vista es solo de lectura) | Cumple |
| 16 | `GET /rest/v1/horarios_ocupados?id_cajon=eq.1` | Horarios de todas las reservas del cajón, sin `id_usuario` | Cumple |

La columna "Hoy" se dedujo de las políticas y permisos de la BD, revisados después de aplicar la etapa 2 (17/11/2026). Conviene repetir la prueba con dos cuentas reales desde la app o con `curl` y guardar la evidencia.

## 5. Riesgos detectados

Riesgos encontrados en la revisión del 17/11/2026. **Todos quedaron cerrados** con las migraciones de ese día (verificado en la BD después de aplicarlas).

| # | Riesgo | Ejemplo | Solución aplicada |
|---|---|---|---|
| 1 | Confirmar o extender una reserva propia sin pagar | `PATCH /rest/v1/Reserva?id=eq.<suya>` con `{ "confirmada": true }` o un `tiempo_fin` mayor | Etapa 2: permiso de UPDATE solo sobre `token_estacionamiento`, y el trigger `trg_reserva_proteger_confirmada` |
| 2 | Cambiar el `customer_id` propio por el de otro usuario | Con un `cus_...` ajeno, `metodos-pago` listaría y podría eliminar las tarjetas de ese cliente | Etapa 2: permiso de UPDATE solo sobre `nombre`, `correo`, `telefono`, `discapacitado`, `url_foto_perfil` y `fcm_token` |
| 3 | Marcar `bienvenida_enviada` o cambiar `contrasena` | Columnas que la app no usa | Igual que el riesgo 2 |
| 4 | Ver reservas de otros usuarios | `GET /rest/v1/Reserva` regresaba `id_usuario` y `token_estacionamiento` de todos | Etapa 2: política `reserva_select_propias` |
| 5 | Borrar o modificar reservas ajenas sin sesión a través de la vista | `horarios_ocupados` se ejecuta con los permisos de su dueño y tenía INSERT/UPDATE/DELETE para `anon` y `authenticated`; `DELETE /rest/v1/horarios_ocupados?id_cajon=eq.1` borraba reservas saltándose el RLS | Preparación: la vista quedó solo de lectura |
| 6 | Función sin uso ejecutable sin sesión | `obtener_cajones_con_reservas_hoy` es `SECURITY DEFINER` y cualquiera podía llamarla (además apunta a la columna inexistente `Reserva.estado`) | Preparación: se le quitó el permiso de ejecución |

Pendientes menores:

- **`Usuario.contrasena`:** las contraseñas las maneja Supabase Auth y nadie usa esa columna, pero 1 de los 21 usuarios la tiene llena. Conviene vaciarla y eliminarla (líneas opcionales al final de `20261106_rls_etapa2.sql`).

## 6. Cómo se aplicó la etapa 2

Completada el 17/11/2026. Se deja el procedimiento como referencia.

La app quedó lista antes de aplicar las migraciones:

- Las consultas de disponibilidad (R1 y R2 en [Conexión de endpoints](Conexion%20de%20endpoints.md)) leen la vista `horarios_ocupados`.
- `ReservaRepository.confirmarReservaActual()` ya no hace `PATCH confirmada`: solo actualiza la reserva guardada en el teléfono. La BD la confirma `stripe-webhook`.

Pasos, **en este orden**:

1. **BD:** aplicar `20261117_rls_preparacion_etapa2.sql`. Cierra los riesgos 5 y 6 y no afecta a la app actual.
2. **App:** compilar e instalar la versión nueva en todos los teléfonos de prueba. Necesita el paso 1, porque pide la columna `id` de la vista.
3. **BD:** aplicar `20261106_rls_etapa2.sql`. Cierra los riesgos 1 a 4.
4. Repetir la prueba de la sección 4 con dos cuentas y probar el flujo completo: reservar, pagar, escanear el QR, extender y editar el perfil.

Las dos migraciones se aplican en **SQL Editor → New query** (ver `software/supabase/README.md`). Si algo falla después del paso 3, se puede volver a permitir la lectura de todas las reservas con `CREATE POLICY "Ver reservas" ON "Reserva" FOR SELECT USING (true);` mientras se corrige.
