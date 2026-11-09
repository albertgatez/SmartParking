# Definición de relaciones - SmartParking

Relaciones entre las tablas del esquema `public` en Supabase. Lo que se documenta aquí está **verificado en la base de datos**, no solo en el diagrama entidad-relación.

Fecha de verificación: 16/10/2026 (tablas originales). La tabla `Notificacion` se agregó el 06/11/2026; sus llaves foráneas están tomadas de la migración `20261106_notificaciones_eventos.sql` y los nombres de restricción son los que PostgreSQL asigna por defecto. Conviene volver a ejecutar la consulta de la sección 1 para confirmarlos.

## 1. Llaves foráneas en la base de datos

| Tabla (hija) | Campo | Referencia (padre) | ON DELETE | ON UPDATE | Nombre de la restricción |
|---|---|---|---|---|---|
| `Cajon` | `id_estacionamiento` | `Estacionamiento.id` | CASCADE | CASCADE | `Cajon_id_estacionamiento_fkey` |
| `Imagen_estacionamiento` | `id_estacionamiento` | `Estacionamiento.id` | CASCADE | CASCADE | `Imagen_estacionamiento_id_estacionamiento_fkey` |
| `Reserva` | `id_cajon` | `Cajon.id` | CASCADE | CASCADE | `Reserva_id_cajon_fkey` |
| `Reserva` | `id_usuario` | `Usuario.id` | CASCADE | CASCADE | `Reserva_id_usuario_fkey` |
| `Pagos` | `usuario_id` | `Usuario.id` | NO ACTION | NO ACTION | `fk_usuario_pago` |
| `Notificacion` | `id_usuario` | `Usuario.id` | CASCADE | NO ACTION | `Notificacion_id_usuario_fkey` |
| `Notificacion` | `id_reserva` | `Reserva.id` | SET NULL | NO ACTION | `Notificacion_id_reserva_fkey` |

Consulta usada para obtenerlas:

```sql
SELECT conrelid::regclass AS tabla,
       conname            AS restriccion,
       pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE contype = 'f'
  AND connamespace = 'public'::regnamespace;
```

### Relaciones sin llave foránea

Estos campos hacen referencia a otra tabla, pero la BD no lo valida:

| Campo | Debería apuntar a | Tipo actual |
|---|---|---|
| `Pagos.id_reserva` | `Reserva.id` | `text` |
| `Pagos.id_estacionamiento` | `Estacionamiento.id` | `text` |
| `Pagos.id_usuario` | `Usuario.id` | `varchar`  |

### Relaciones que se resuelven con triggers (no son llaves foráneas)

Algunas tablas no se relacionan por una FK, pero los triggers de notificaciones las conectan al insertar en `Notificacion`:

| Origen | Cómo se relaciona | Resultado |
|---|---|---|
| `Cajon` → `Reserva` | Al cambiar `Cajon.estado_cajon`, el trigger `cajon_estado_notificar` busca la reserva confirmada en curso de ese cajón (`id_cajon`, `tiempo_inicio <= now() < tiempo_fin`) | Inserta un aviso para el `id_usuario` de esa reserva, con su `id_reserva`. Si no hay reserva en curso, no se crea nada. |
| `Reserva` → `Usuario` | Los triggers y la tarea `recordatorios-fin-reserva` toman el `id_usuario` de la reserva | El aviso se guarda para el dueño de la reserva. |
| `Usuario.fcm_token` | La Edge Function `enviar-notificacion` lee el token del usuario del aviso | Se manda el push al teléfono del usuario; si no hay token, el aviso queda solo como historial (`push_enviada = false`). |

## 2. Comportamiento al borrar (ON DELETE)

| Si se borra… | Qué pasa | Motivo |
|---|---|---|
| Un **estacionamiento** | Se borran sus cajones, sus imágenes y, en cadena, las reservas de esos cajones. Los avisos de esas reservas se conservan y su `id_reserva` queda en `null`. | Un cajón o una foto no tienen sentido sin su estacionamiento. |
| Un **cajón** | Se borran todas sus reservas. Sus avisos se conservan con `id_reserva` en `null`. | Una reserva no puede existir sin el cajón reservado. |
| Un **usuario** | Se borran sus reservas y sus avisos (`Notificacion`). Si el usuario tiene pagos, **no se puede borrar** (Pagos usa NO ACTION). | Los pagos son un registro contable y se deben conservar; los avisos no tienen sentido sin su destinatario. |
| Una **reserva** | Sus pagos se conservan. Sus avisos se conservan y `Notificacion.id_reserva` pasa a `null`. | `Pagos` no tiene FK a `Reserva`, así que el historial de pagos no se pierde; un aviso sigue siendo útil como historial aunque la reserva ya no exista (por ejemplo, una reserva pendiente que expiró y se eliminó). |
| Un **aviso** | No afecta a ninguna otra tabla. | `Notificacion` no es padre de ninguna tabla. |

Si se cambia el `id` de un estacionamiento, cajón o usuario, el cambio se propaga a las tablas hijas (ON UPDATE CASCADE). En la práctica no pasa porque los `id` son autogenerados. `Notificacion` usa ON UPDATE NO ACTION: cambiar el `id` de un usuario o una reserva con avisos asociados sería rechazado.


## 3. Verificación de registros huérfanos

Un registro huérfano es una fila hija cuyo padre ya no existe.

```sql
-- Reservas cuyo cajón no existe
SELECT count(*) FROM "Reserva" r
WHERE NOT EXISTS (SELECT 1 FROM "Cajon" c WHERE c.id = r.id_cajon);

-- Reservas cuyo usuario no existe
SELECT count(*) FROM "Reserva" r
WHERE NOT EXISTS (SELECT 1 FROM "Usuario" u WHERE u.id = r.id_usuario);

-- Cajones sin estacionamiento (o con uno que no existe)
SELECT count(*) FROM "Cajon" c
WHERE c.id_estacionamiento IS NULL
   OR NOT EXISTS (SELECT 1 FROM "Estacionamiento" e WHERE e.id = c.id_estacionamiento);

-- Imágenes cuyo estacionamiento no existe
SELECT count(*) FROM "Imagen_estacionamiento" i
WHERE NOT EXISTS (SELECT 1 FROM "Estacionamiento" e WHERE e.id = i.id_estacionamiento);

-- Pagos cuyo usuario no existe
SELECT count(*) FROM "Pagos" p
WHERE p.usuario_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM "Usuario" u WHERE u.id = p.usuario_id);

-- Pagos cuya reserva no existe
SELECT count(*) FROM "Pagos" p
WHERE NOT EXISTS (SELECT 1 FROM "Reserva" r WHERE r.id::text = p.id_reserva);

-- Pagos con un estacionamiento que no existe
SELECT count(*) FROM "Pagos" p
WHERE p.id_estacionamiento IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM "Estacionamiento" e WHERE e.id::text = p.id_estacionamiento);

-- Avisos cuyo usuario no existe
SELECT count(*) FROM "Notificacion" n
WHERE NOT EXISTS (SELECT 1 FROM "Usuario" u WHERE u.id = n.id_usuario);

-- Avisos con una reserva que no existe
-- (id_reserva NULL es normal: la reserva se borró y la FK lo dejó en NULL)
SELECT count(*) FROM "Notificacion" n
WHERE n.id_reserva IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM "Reserva" r WHERE r.id = n.id_reserva);
```

Las dos consultas de `Notificacion` deben dar `0`, porque la BD valida ambas llaves foráneas.