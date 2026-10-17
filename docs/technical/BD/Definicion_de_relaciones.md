# Definición de relaciones — SmartParking

Relaciones entre las tablas del esquema `public` en Supabase. Lo que se documenta aquí está **verificado en la base de datos**, no solo en el diagrama entidad-relación.

Fecha de verificación: 16/10/2026.

## 1. Llaves foráneas en la base de datos

| Tabla (hija) | Campo | Referencia (padre) | ON DELETE | ON UPDATE | Nombre de la restricción |
|---|---|---|---|---|---|
| `Cajon` | `id_estacionamiento` | `Estacionamiento.id` | CASCADE | CASCADE | `Cajon_id_estacionamiento_fkey` |
| `Imagen_estacionamiento` | `id_estacionamiento` | `Estacionamiento.id` | CASCADE | CASCADE | `Imagen_estacionamiento_id_estacionamiento_fkey` |
| `Reserva` | `id_cajon` | `Cajon.id` | CASCADE | CASCADE | `Reserva_id_cajon_fkey` |
| `Reserva` | `id_usuario` | `Usuario.id` | CASCADE | CASCADE | `Reserva_id_usuario_fkey` |
| `Pagos` | `usuario_id` | `Usuario.id` | NO ACTION | NO ACTION | `fk_usuario_pago` |

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
| `Pagos.id_usuario` | `Usuario.id` | `varchar` (duplica a `usuario_id`) |

## 2. Comportamiento al borrar (ON DELETE)

| Si se borra… | Qué pasa | Motivo |
|---|---|---|
| Un **estacionamiento** | Se borran sus cajones, sus imágenes y, en cadena, las reservas de esos cajones. | Un cajón o una foto no tienen sentido sin su estacionamiento. |
| Un **cajón** | Se borran todas sus reservas. | Una reserva no puede existir sin el cajón reservado. |
| Un **usuario** | Se borran sus reservas. Si el usuario tiene pagos, **no se puede borrar** (Pagos usa NO ACTION). | Los pagos son un registro contable y se deben conservar. |
| Una **reserva** | Sus pagos se conservan. | `Pagos` no tiene FK a `Reserva`, así que el historial de pagos no se pierde. |

Si se cambia el `id` de un estacionamiento, cajón o usuario, el cambio se propaga a las tablas hijas (ON UPDATE CASCADE). En la práctica no pasa porque los `id` son autogenerados.

**Recomendación:** en un sistema en producción conviene cambiar `Reserva → Cajon` a `ON DELETE RESTRICT`, para que no se pierda el historial de reservas si alguien borra un cajón por error. Para dar de baja un cajón sería mejor marcarlo como inactivo.

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
```

