# Supabase

Cambios a la base de datos (migraciones) y Edge Functions del proyecto `kjbmwwbzdsvunzndeusf`.

Esta carpeta está en `software/supabase/`. Si se usa la CLI de Supabase, los comandos se ejecutan desde `software/` (por ejemplo `cd software && supabase functions deploy crear-pago-reserva`), porque la CLI busca la carpeta `supabase/` en el directorio actual.

| Carpeta | Contenido |
|---|---|
| `migrations/` | Scripts SQL que modifican la base de datos, en el orden en que se aplican |
| `functions/` | Edge Functions de pago (cálculo del monto y webhook de Stripe) |

## Migraciones

| Archivo | Contenido | Issue | Estado |
|---|---|---|---|
| `20261018_logica_negocio.sql` | Restricción contra reservas traslapadas, validaciones al reservar y limpieza de reservas pendientes con `pg_cron` | 8.3 | Aplicada el 19/10/2026 |
| `20261106_rls_etapa1.sql` | RLS etapa 1: cada usuario solo modifica sus reservas; los cajones solo los modifica el gateway; vista `horarios_ocupados` | 8.2 | Aplicada el 06/11/2026 |
| `20261106_rls_etapa2.sql` | RLS etapa 2: cada usuario solo ve sus reservas; la app ya no puede confirmar reservas | 8.2 | Pendiente |
| `20261018_pagos_desglose.sql` | Llave primaria y desglose de pagos (subtotal, comisión, descuento) | 10.1, 12.2 | Pendiente |
| `20261018_notificaciones.sql` | Tablas de dispositivos y notificaciones, avisos programados | 11.1, 11.2 | Pendiente |

### Cómo aplicar una migración

1. En el panel de Supabase abrir **SQL Editor → New query**.
2. Copiar el contenido completo del archivo `.sql`, pegarlo y presionar **Run**.
3. Actualizar la columna **Estado** de esta tabla con la fecha.

Cada migración se aplica **una sola vez**. Si se vuelve a ejecutar, marca error porque los objetos ya existen.

### Requisitos antes de aplicar

| Migración | Requisito |
|---|---|
| `rls_etapa1` | El gateway de la Raspberry debe usar la llave secreta (`sb_secret_...`) en su `.env` |
| `rls_etapa2` | La app consulta la disponibilidad desde la vista `horarios_ocupados`, y el webhook de Stripe ya confirma las reservas |
| `pagos_desglose` | Desplegar las Edge Functions de `functions/` |
| `notificaciones` | Proyecto de Firebase creado y Edge Function `enviar-notificacion` desplegada |

## Edge Functions

| Función | Qué hace |
|---|---|
| `crear-pago-reserva` | Calcula el monto en el servidor y crea el pago en Stripe |
| `stripe-webhook` | Recibe la confirmación de Stripe, confirma o extiende la reserva y registra el pago |
| `_shared/calculo.ts` | Fórmula de la comisión (4 % reserva, 5 % extensión) |

Secretos necesarios (se configuran en **Edge Functions → Secrets**, nunca en el repositorio):

```bash
supabase secrets set STRIPE_SECRET_KEY=sk_test_... STRIPE_WEBHOOK_SECRET=whsec_...
```

`SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` ya están disponibles dentro de las funciones.
