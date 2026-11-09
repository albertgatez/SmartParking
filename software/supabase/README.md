# Supabase

Cambios a la base de datos (migraciones) y Edge Functions del proyecto `kjbmwwbzdsvunzndeusf`.

Esta carpeta está en `software/supabase/`. Si se usa la CLI de Supabase, los comandos se ejecutan desde `software/` (por ejemplo `cd software && supabase functions deploy crear-pago-reserva-v2`), porque la CLI busca la carpeta `supabase/` en el directorio actual.

| Carpeta | Contenido |
|---|---|
| `migrations/` | Scripts SQL que modifican la base de datos, en el orden en que se aplicaron |
| `functions/` | Edge Functions (pagos con Stripe y notificaciones push) |

## Migraciones

| Archivo | Contenido | Issue | Estado |
|---|---|---|---|
| `20261018_logica_negocio.sql` | Restricción contra reservas traslapadas, validaciones al reservar y limpieza de reservas pendientes con `pg_cron` | 8.3 | Aplicada el 19/10/2026 |
| `20261105_notificaciones_token.sql` | Columnas `fcm_token` y `bienvenida_enviada` en `Usuario` | 11.1 | Aplicada |
| `20261106_notificaciones_eventos.sql` | Tabla `Notificacion`, triggers en `Reserva` y `Cajon`, recordatorio con `pg_cron` | 11.2, 13.1 | Aplicada |
| `20261106_notificaciones_envio.sql` | Trigger que envía cada aviso a la Edge Function `enviar-notificacion` | 11.2 | Aplicada |
| `20261109_notificaciones_ocultar.sql` | Columna `oculta` en `Notificacion` y política para que cada usuario oculte sus avisos (X o deslizar en Avisos). No se borran para que los recordatorios no se reenvíen | 11.2 | Pendiente |
| `20261106_rls_etapa1.sql` | RLS etapa 1: cada usuario solo modifica sus reservas; los cajones solo los modifica el gateway; vista `horarios_ocupados` | 8.2 | Aplicada el 06/11/2026 |
| `20261106_rls_etapa2.sql` | RLS etapa 2: cada usuario solo ve sus reservas; la app ya no puede confirmar reservas | 8.2 | Pendiente |
| `20261018_pagos_desglose.sql` | Llave primaria y desglose de pagos (subtotal, comisión, descuento) | 10.1, 12.2 | Aplicada (verificado el 07/11/2026: `Pagos` ya tiene `comision`, `descuento`, `tipo` y `stripe_payment_intent`) |

Las migraciones de notificaciones (`20261105_notificaciones_token.sql`, `20261106_notificaciones_eventos.sql` y `20261106_notificaciones_envio.sql`) están documentadas en `docs/technical/Application/Notificaciones en Supabase.md`. En el repositorio, el secreto del header `x-webhook-secret` va como `<secreto>`; el valor real solo está en la base de datos y en los secretos de las Edge Functions.

### Cómo aplicar una migración

1. En el panel de Supabase abrir **SQL Editor → New query**.
2. Copiar el contenido completo del archivo `.sql`, pegarlo y presionar **Run**.
3. Actualizar la columna **Estado** de esta tabla con la fecha.

Cada migración se aplica **una sola vez**. Si se vuelve a ejecutar, marca error porque los objetos ya existen.

### Requisitos de las migraciones pendientes

| Migración | Requisito |
|---|---|
| `rls_etapa2` | La app consulta la disponibilidad desde la vista `horarios_ocupados`, y deja de confirmar la reserva por su cuenta (el webhook de Stripe ya la confirma) |

## Edge Functions

| Función | Qué hace | Estado |
|---|---|---|
| `crear-pago-reserva-v2` | Calcula el monto en el servidor (comisión de 4 % en reservas y 5 % en extensiones, cupones), valida la reserva y crea el pago en Stripe. Con `solo_cotizar: true` solo regresa el desglose. Incluye la fórmula en el mismo archivo para desplegarse desde el panel. La usa la app | Desplegada la versión 1 (con verificación de JWT). **Falta volver a desplegarla** para que mande `cupon_descripcion` (la descripción del cupón que ve el usuario) |
| `stripe-webhook` | Recibe la confirmación de Stripe, confirma la reserva o extiende `tiempo_fin` y registra el pago con su desglose | Desplegada (versión de esta carpeta) |
| `crear-pago-reserva` | Versión anterior: recibe el monto calculado por la app | Desplegada, ya no la usa la app nueva. Se puede eliminar cuando todos actualicen la app |
| `notificar-bienvenida` | Envía el aviso de bienvenida al registrarse | Desplegada |
| `enviar-notificacion` | Envía a Firebase cada aviso insertado en `Notificacion` | Desplegada |
| `metodos-pago` | Lista, agrega (SetupIntent) y elimina las tarjetas guardadas del usuario en Stripe, marca la predeterminada (`predeterminar`) y da la llave temporal para mostrarlas al pagar. Ver `docs/technical/Application/Metodos de pago.md` | Desplegada la versión 1. **Falta volver a desplegarla** con la acción `predeterminar` |

## Secretos

Se configuran en **Edge Functions → Secrets**, nunca en el repositorio:

| Secreto | Lo usa |
|---|---|
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Funciones de pago |
| `FIREBASE_SERVICE_ACCOUNT`, `FCM_WEBHOOK_SECRET` | Funciones de notificaciones |

`SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` ya están disponibles dentro de las funciones.

La llave secreta que usa el gateway de la Raspberry (`raspberry-gateway`) solo está en su `.env`.
