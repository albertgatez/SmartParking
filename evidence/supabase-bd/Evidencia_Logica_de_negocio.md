# Evidencia — Lógica de negocio en la base de datos

**Issue:** 8.3 Desarrollo de lógica de negocio
**Fecha:** 19/10/2026
**Script aplicado:** `software/supabase/migrations/20261018_logica_negocio.sql`

## Qué se agregó

| Elemento | Tipo | Regla |
|---|---|---|
| `reserva_sin_traslape` | Restricción `EXCLUDE` | Un cajón no puede tener dos reservas que se crucen en horario |
| `reserva_horario_valido` | Restricción `CHECK` | La hora de fin debe ser posterior a la de inicio |
| `trg_reserva_validar_insert` | Trigger | Una reserva activa por usuario; cajones de discapacidad solo para usuarios con discapacidad; expiración de 5 min fijada por el servidor |
| `limpiar-reservas-pendientes` | Tarea de `pg_cron`, cada minuto | Elimina las reservas pendientes de pago cuya expiración ya pasó |

## Preparación: reservas que ya se cruzaban

Al aplicar el script por primera vez, la restricción no se pudo crear porque ya existían reservas traslapadas:

```
ERROR: 23P01: could not create exclusion constraint "reserva_sin_traslape"
DETAIL: Key (id_cajon, tstzrange(tiempo_inicio, tiempo_fin))=(2, ["2025-12-02 04:00:00+00","2025-12-02 06:00:00+00"))
conflicts with key (id_cajon, tstzrange(tiempo_inicio, tiempo_fin))=(2, ["2025-12-02 04:00:00+00","2025-12-02 06:00:00+00")).
```

Se resolvió así:

1. Se eliminaron las reservas pendientes ya vencidas: `DELETE FROM "Reserva" WHERE confirmada = false AND expiracion < now();`
2. Quedó un solo cruce: las reservas 173 y 174 (mismo usuario, cajón 2, mismo horario, ambas pagadas), un duplicado de las pruebas de diciembre de 2025. Se eliminó la 174 y se conservó la 173. Su pago permanece en `Pagos` como historial.
3. Se volvió a ejecutar el script sin errores.

## Verificación

Consulta de solo lectura después de aplicar el script:

| Elemento | Estado |
|---|---|
| Restricciones `reserva_sin_traslape` y `reserva_horario_valido` | Creadas |
| Trigger `trg_reserva_validar_insert` | Creado |
| Tarea `limpiar-reservas-pendientes` (`* * * * *`) | Activa |

## Prueba: doble reserva del mismo cajón y horario

```sql
INSERT INTO "Reserva" (id_usuario, id_cajon, tiempo_inicio, tiempo_fin, confirmada)
SELECT u.id, 2, now() + interval '1 hour', now() + interval '2 hours', false
FROM "Usuario" u
WHERE NOT EXISTS (SELECT 1 FROM "Reserva" r WHERE r.id_usuario = u.id AND r.tiempo_fin > now())
ORDER BY u.id LIMIT 1;
-- (la misma inserción otra vez, que toma a otro usuario)
```

Resultado:

```
ERROR: 23P01: conflicting key value violates exclusion constraint "reserva_sin_traslape"
DETAIL: Key (id_cajon, tstzrange(tiempo_inicio, tiempo_fin))=(2, ["2026-10-19 07:11:19.996692+00","2026-10-19 08:11:19.996692+00"))
conflicts with existing key (id_cajon, tstzrange(tiempo_inicio, tiempo_fin))=(2, ["2026-10-19 07:11:19.996692+00","2026-10-19 08:11:19.996692+00")).
```

La base de datos rechaza la segunda reserva aunque la app no la haya validado. La app muestra "Ese horario acaba de ser reservado por otra persona" cuando recibe este error (HTTP 409).

## Criterios de terminado

| Criterio | Estado |
|---|---|
| La base de datos impide reservas traslapadas | Cumplido |
| Las reservas pendientes se eliminan a los 5 minutos desde el servidor | Cumplido (`pg_cron` cada minuto) |
| Al extender se validan las horas siguientes | Cumplido (la misma restricción rechaza una extensión que se cruce) |
| El estado del cajón se actualiza según sensores y reservas | Cumplido (gateway y Arduino) |
| Prueba de doble reserva rechazada | Cumplido |
| Reglas documentadas | Cumplido: `docs/technical/` y este documento |
