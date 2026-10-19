# Lógica de negocio

Reglas que el sistema debe cumplir sobre disponibilidad, reservas, pagos y estado de los cajones. La idea es que las reglas importantes vivan en la **base de datos**, para que se cumplan aunque alguien use la API sin la app.

## 1. Reglas de negocio

| # | Regla | Dónde se aplica hoy | Dónde debe aplicarse |
|---|---|---|---|
| RNF-01 | Un cajón no puede tener dos reservas que se crucen en horario | App (revisa antes de reservar) | **BD**: restricción `EXCLUDE` |
| RNF-02 | Una reserva pendiente tiene 5 minutos para pagarse; si no, se elimina | App (solo si está abierta) | **BD**: `pg_cron` cada minuto |
| RNF-03 | Un usuario solo puede tener una reserva activa a la vez | App | **BD**: trigger antes de insertar |
| RNF-04 | Al extender, las horas extra deben estar libres | App (revisa antes de pagar) | BD: la cubre la restricción `EXCLUDE` |
| RNF-05 | Los cajones para discapacidad solo los reservan usuarios con `discapacitado = true` | App | BD: trigger antes de insertar |
| RNF-06 | La reserva se confirma solo cuando Stripe confirma el pago | App | Webhook de Stripe (issue 10.1) |
| RNF-07 | El estado del cajón refleja el sensor y las reservas | Gateway + Arduino | Gateway + Arduino |
| RNF-08 | Las reservas se hacen por bloques de una hora, del día actual | App | App |

## 2. Implementación en la base de datos


### RNF-01 y RNF-04: sin reservas traslapadas

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE "Reserva"
  ADD CONSTRAINT reserva_sin_traslape
  EXCLUDE USING gist (
    id_cajon WITH =,
    tstzrange(tiempo_inicio, tiempo_fin) WITH &&
  );
```

Si dos usuarios intentan reservar el mismo cajón y horario al mismo tiempo, el segundo `INSERT` falla con el error `23P01` (exclusion_violation) y la app muestra "Ese horario acaba de ser reservado". Lo mismo pasa al extender (`PATCH tiempo_fin`) si las horas siguientes ya están ocupadas.

La restricción incluye las reservas **pendientes**, para que nadie pueda apartar el horario mientras otro usuario está pagando.

### RNF-02: limpieza de reservas pendientes

```sql
CREATE EXTENSION IF NOT EXISTS pg_cron;

SELECT cron.schedule(
  'limpiar-reservas-pendientes',
  '* * * * *',
  $$ DELETE FROM "Reserva" WHERE confirmada = false AND expiracion < now() $$
);
```

Usa la columna `expiracion`, que ya tiene por defecto `now() + 5 minutos`.

### RN-03 y RN-05: validaciones al crear la reserva

Un trigger `BEFORE INSERT` en `Reserva` rechaza la reserva si:

- El usuario ya tiene otra reserva que no ha terminado.
- El cajón es para discapacidad y el usuario no tiene `discapacitado = true`.

## 3. Estado del cajón

El estado lo calculan el Arduino y el gateway:

| Situación | Estado | Quién lo decide |
|---|---|---|
| El sensor detecta un auto | `1` ocupado | Arduino |
| No hay auto y hay una reserva confirmada en curso | `2` reservado | Gateway (consulta `Reserva`) → comando `R` al Arduino |
| No hay auto ni reserva | `0` libre | Arduino |

El gateway escribe el resultado en `Cajon.estado_cajon` solo cuando cambia.

## 4. Pruebas

| # | Prueba | Cómo | Resultado esperado |
|---|---|---|---|
| 1 | Doble reserva simultánea | Dos `INSERT` del mismo cajón y horario desde el SQL Editor | El segundo falla con `23P01` |
| 2 | Reserva que se cruza parcialmente | Reserva 10:00–12:00 y otra 11:00–13:00 en el mismo cajón | La segunda falla |
| 3 | Reservas contiguas | 10:00–11:00 y 11:00–12:00 | Ambas se aceptan |
| 4 | Extensión sobre horario ocupado | `PATCH tiempo_fin` que invade otra reserva | Falla con `23P01` |
| 5 | Limpieza de pendientes | Insertar una pendiente con `expiracion = now() - interval '1 min'` y esperar 1 min | Se elimina sola |
| 6 | Dos reservas del mismo usuario | Insertar una segunda reserva activa | Falla por el trigger |
| 7 | Cajón de discapacidad | Reservar el cajón 1 con un usuario sin discapacidad | Falla por el trigger |

Prueba 1 en el SQL Editor:

```sql
INSERT INTO "Reserva" (id_usuario, id_cajon, tiempo_inicio, tiempo_fin, confirmada)
VALUES ('<uuid de prueba>', 2, now() + interval '1 hour', now() + interval '2 hours', false);

-- La misma petición otra vez: debe fallar
INSERT INTO "Reserva" (id_usuario, id_cajon, tiempo_inicio, tiempo_fin, confirmada)
VALUES ('<uuid de prueba>', 2, now() + interval '1 hour', now() + interval '2 hours', false);
-- ERROR: conflicting key value violates exclusion constraint "reserva_sin_traslape"
```

## 5. Cambio necesario en la app

Cuando el `INSERT` o el `PATCH` fallen por la restricción (HTTP 409 con código `23P01`), la app debe mostrar "Ese horario acaba de ser reservado. Elige otro." en lugar del error genérico.
