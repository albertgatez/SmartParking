# Evidencia — Políticas RLS (etapas 1 y 2)

**Issue:** 8.2 Conexión API–Supabase
**Fechas:** etapa 1 el 06/11/2026; etapa 2 el 17/11/2026 (ver la sección "Etapa 2" al final)
**Scripts aplicados:** `software/supabase/migrations/20261106_rls_etapa1.sql`, `20261117_rls_preparacion_etapa2.sql` y `20261106_rls_etapa2.sql`

# Etapa 1

## Qué se cambió

| Tabla | Operación | Antes | Después |
|---|---|---|---|
| `Reserva` | INSERT | Cualquiera, incluso sin sesión | Solo usuarios con sesión, a su nombre y sin confirmar |
| `Reserva` | UPDATE | Cualquiera, cualquier reserva | Solo sus propias reservas |
| `Reserva` | DELETE | Cualquiera, cualquier reserva | Solo sus reservas pendientes |
| `Reserva` | SELECT | Todos | Sin cambio (la app lo necesita para calcular horarios; se cierra en la etapa 2) |
| `Cajon` | INSERT y UPDATE | Cualquiera | Nadie desde la app; solo el gateway con la llave secreta |
| — | Vista `horarios_ocupados` | No existía | Muestra cajón y horario de las reservas, sin datos del usuario |

## Preparación del gateway

Antes de aplicar las políticas se creó la llave secreta `raspberry-gateway` y se puso en el `.env` de la Raspberry, porque con las nuevas políticas solo una llave secreta puede actualizar `Cajon`:

```
$ grep -c '^SUPABASE_KEY=sb_secret_' ~/smartparking/.env
1
```

## Prueba

Bloque SQL que simula a un usuario con sesión (`role = authenticated`) que intenta:

1. Modificar la reserva más reciente, que pertenece a **otro** usuario.
2. Modificar el cajón 1.
3. Crear una reserva a nombre de otro usuario.
4. Modificar su **propia** reserva (como el dueño).

Al final lanza un error a propósito para mostrar el resultado y deshacer todos los cambios.

```sql
DO $$
DECLARE
  r_id bigint; dueno uuid; otro uuid;
  n_ajena int; n_propia int; n_cajon int;
  insertar_ajeno text := 'permitido';
BEGIN
  SELECT id, id_usuario INTO r_id, dueno FROM "Reserva" ORDER BY id DESC LIMIT 1;
  SELECT id INTO otro FROM "Usuario" WHERE id <> dueno LIMIT 1;

  PERFORM set_config('request.jwt.claims', json_build_object('sub', otro, 'role', 'authenticated')::text, true);
  SET LOCAL ROLE authenticated;

  UPDATE "Reserva" SET token_estacionamiento = token_estacionamiento WHERE id = r_id;
  GET DIAGNOSTICS n_ajena = ROW_COUNT;

  UPDATE "Cajon" SET estado_cajon = estado_cajon WHERE id = 1;
  GET DIAGNOSTICS n_cajon = ROW_COUNT;

  BEGIN
    INSERT INTO "Reserva" (id_usuario, id_cajon, tiempo_inicio, tiempo_fin, confirmada)
    VALUES (dueno, 2, now() + interval '5 hours', now() + interval '6 hours', false);
  EXCEPTION WHEN others THEN
    insertar_ajeno := 'rechazado (' || SQLSTATE || ')';
  END;

  PERFORM set_config('request.jwt.claims', json_build_object('sub', dueno, 'role', 'authenticated')::text, true);
  UPDATE "Reserva" SET token_estacionamiento = token_estacionamiento WHERE id = r_id;
  GET DIAGNOSTICS n_propia = ROW_COUNT;

  RAISE EXCEPTION 'PRUEBA RLS -> reserva ajena: % fila(s) | cajon: % fila(s) | reservar a nombre de otro: % | reserva propia: % fila(s)',
    n_ajena, n_cajon, insertar_ajeno, n_propia;
END $$;
```

### Antes de aplicar las políticas

```
ERROR: P0001: PRUEBA RLS -> reserva ajena: 1 fila(s) | cajon: 1 fila(s) | reservar a nombre de otro: permitido | reserva propia: 1 fila(s)
```

### Después de aplicar las políticas

```
ERROR: P0001: PRUEBA RLS -> reserva ajena: 0 fila(s) | cajon: 0 fila(s) | reservar a nombre de otro: rechazado (42501) | reserva propia: 1 fila(s)
```

(`P0001` es el error que lanza la prueba a propósito para mostrar el resultado.)

## Resultados

| Prueba | Antes | Después | Resultado |
|---|---|---|---|
| Modificar la reserva de otro usuario | 1 fila (permitido) | 0 filas | Bloqueado |
| Modificar un cajón con la llave de la app | 1 fila (permitido) | 0 filas | Bloqueado |
| Crear una reserva a nombre de otro usuario | Permitido | Rechazado, código `42501` (violación de política) | Bloqueado |
| Modificar su propia reserva | 1 fila | 1 fila | Permitido |

## El gateway sigue funcionando

Log del gateway después de aplicar las políticas (22:49):

```
22:51:35,712 [INFO] MQTT smartparking/cajon/3/estado -> ocupado
22:51:36,011 [INFO] Supabase: cajon 3 = ocupado
22:51:36,165 [INFO] MQTT smartparking/cajon/2/estado -> ocupado
22:51:36,399 [INFO] Supabase: cajon 2 = ocupado
```

El gateway usa la llave secreta, que no está sujeta a RLS, por lo que puede seguir actualizando `Cajon`.

## Criterios de terminado

| Criterio | Estado |
|---|---|
| Políticas RLS por usuario en `Reserva` | Cumplido |
| Solo el backend modifica `Cajon` | Cumplido |
| Prueba de que un usuario no puede modificar datos de otro | Cumplido |
| El gateway sigue actualizando los cajones | Cumplido |
| Políticas versionadas en el repositorio | Cumplido: `software/supabase/migrations/20261106_rls_etapa1.sql` |
| Ocultar las reservas de otros usuarios (lectura) | Cumplido en la etapa 2 (abajo) |

# Etapa 2

**Fecha:** 17/11/2026
**Scripts aplicados (en este orden):** `20261117_rls_preparacion_etapa2.sql` y `20261106_rls_etapa2.sql`, desde el SQL Editor del panel.

## Qué se cambió

| Elemento | Antes | Después |
|---|---|---|
| `Reserva`, SELECT | Todos veían todas las reservas, con `id_usuario` y `token_estacionamiento` | Cada usuario solo ve las suyas (`reserva_select_propias`) |
| `Reserva`, UPDATE | El dueño podía cambiar cualquier columna, incluidas `confirmada` y `tiempo_fin` (confirmar o extender sin pagar) | Solo la columna `token_estacionamiento`; además, el trigger `trg_reserva_proteger_confirmada` rechaza cambios a `confirmada` |
| `Reserva`, INSERT | Cualquier columna | Solo `id_usuario`, `id_cajon`, `tiempo_inicio`, `tiempo_fin` y `confirmada` |
| `Usuario`, UPDATE | El dueño podía cambiar cualquier columna, incluida `customer_id` | Solo `nombre`, `correo`, `telefono`, `discapacitado`, `url_foto_perfil` y `fcm_token` |
| Vista `horarios_ocupados` | Permitía INSERT, UPDATE y DELETE a `anon` y `authenticated`, saltándose el RLS de `Reserva` | Solo lectura; se agregó la columna `id` |
| `obtener_cajones_con_reservas_hoy` | Ejecutable sin sesión (`SECURITY DEFINER`) | Sin permiso de ejecución; después se eliminó |
| `Pagos`, SELECT | Política `usuarios_pueden_ver_sus_pagos` | Política `pagos_select_propios` (misma regla, solo `authenticated`) |

Antes de aplicar la etapa 2, la app se cambió para leer la disponibilidad de `horarios_ocupados` y para no confirmar reservas (las confirma `stripe-webhook`).

## Verificación en la base de datos

Consulta de solo lectura a `pg_policies`, `information_schema.column_privileges`, `role_table_grants` y `pg_trigger` después de aplicar los scripts:

| Revisión | Resultado |
|---|---|
| Políticas de `Reserva` | `reserva_select_propias` (SELECT), `reserva_insert_propia_pendiente`, `reserva_update_propias`, `reserva_delete_propias_pendientes`. Ya no existe `Ver reservas` |
| Columnas con UPDATE en `Reserva` para `authenticated` | `token_estacionamiento` |
| Columnas con INSERT en `Reserva` para `authenticated` | `confirmada`, `id_cajon`, `id_usuario`, `tiempo_fin`, `tiempo_inicio` |
| Columnas con UPDATE en `Usuario` para `authenticated` | `correo`, `discapacitado`, `fcm_token`, `nombre`, `telefono`, `url_foto_perfil` |
| Columnas con INSERT en `Usuario` para `authenticated` | `correo`, `discapacitado`, `id`, `nombre`, `telefono` |
| Permisos de `anon` y `authenticated` sobre `horarios_ocupados` | Solo `SELECT` |
| Definición de `horarios_ocupados` | `SELECT id_cajon, tiempo_inicio, tiempo_fin, id FROM "Reserva"` |
| Trigger `trg_reserva_proteger_confirmada` | Activo |
| Política de `Pagos` | `pagos_select_propios` |

## Prueba con la app

| # | Prueba | Resultado esperado | Resultado |
|---|---|---|---|
| 1 | Seleccionar cajón con una reserva de otra cuenta en ese horario | El horario aparece ocupado | Terminado |
| 2 | Reservar y pagar | La reserva queda confirmada (la confirma `stripe-webhook`) | Terminado |
| 3 | Escanear el QR | Se guarda `token_estacionamiento` | Terminado |
| 4 | Extender 1 hora | `tiempo_fin` aumenta 1 hora | Terminado |
| 5 | Editar perfil y foto | Se guardan los cambios | Terminado |

Resultados registrados por el equipo el 17/11/2026.

## Criterios de terminado

| Criterio | Estado |
|---|---|
| Cada usuario solo lee sus reservas | Cumplido (verificado en la BD) |
| La app no puede confirmar ni extender reservas sin pagar | Cumplido (verificado en la BD) |
| La app no puede cambiar `customer_id` | Cumplido (verificado en la BD) |
| Nadie puede escribir reservas a través de `horarios_ocupados` | Cumplido (verificado en la BD) |
| La app funciona con las políticas nuevas | Cumplido (prueba con la app) |
