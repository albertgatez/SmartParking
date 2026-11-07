# Evidencia — Políticas RLS (etapa 1)

**Issue:** 8.2 Conexión API–Supabase
**Fecha:** 06/11/2026
**Script aplicado:** `software/supabase/migrations/20261106_rls_etapa1.sql`

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
| Ocultar las reservas de otros usuarios (lectura) | Pendiente: etapa 2 |
