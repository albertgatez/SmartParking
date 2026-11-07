-- =====================================================================
-- SmartParking - Notificaciones: tabla, triggers y recordatorio (issues 11.2 y 13.1)
-- Aplicada el 06/11/2026
-- =====================================================================

-- ---------- Tabla ----------
CREATE TABLE IF NOT EXISTS "Notificacion" (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario   uuid   NOT NULL REFERENCES "Usuario"(id) ON DELETE CASCADE,
  id_reserva   bigint REFERENCES "Reserva"(id) ON DELETE SET NULL,
  tipo         text   NOT NULL,
  titulo       text   NOT NULL,
  mensaje      text   NOT NULL,
  leida        boolean     NOT NULL DEFAULT false,
  push_enviada boolean     NOT NULL DEFAULT false,
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS notificacion_usuario_fecha_idx ON "Notificacion" (id_usuario, created_at DESC);
CREATE INDEX IF NOT EXISTS notificacion_reserva_tipo_idx  ON "Notificacion" (id_reserva, tipo);

-- Cada usuario solo ve sus avisos. Insertar solo los triggers (SECURITY DEFINER) y service_role.
ALTER TABLE "Notificacion" ENABLE ROW LEVEL SECURITY;
CREATE POLICY "notificacion_select_propias" ON "Notificacion"
  FOR SELECT TO authenticated
  USING (auth.uid() = id_usuario);

-- ---------- Pago y extensión de la reserva ----------
CREATE OR REPLACE FUNCTION public.reserva_notificar_cambios()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_est text;
  v_lugar text;
BEGIN
  SELECT COALESCE(e.nombre, 'tu estacionamiento'), c.seccion || c.numero_cajon::text
    INTO v_est, v_lugar
  FROM "Cajon" c
  LEFT JOIN "Estacionamiento" e ON e.id = c.id_estacionamiento
  WHERE c.id = NEW.id_cajon;

  IF OLD.confirmada = false AND NEW.confirmada = true THEN
    INSERT INTO "Notificacion" (id_usuario, id_reserva, tipo, titulo, mensaje)
    VALUES (
      NEW.id_usuario, NEW.id, 'reserva_confirmada',
      'Pago recibido, reserva confirmada',
      format('Tu reserva en %s, lugar %s, de %s a %s está confirmada.',
             v_est, v_lugar,
             to_char(NEW.tiempo_inicio AT TIME ZONE 'America/Mexico_City', 'HH24:MI'),
             to_char(NEW.tiempo_fin AT TIME ZONE 'America/Mexico_City', 'HH24:MI'))
    );

  ELSIF OLD.confirmada AND NEW.confirmada AND NEW.tiempo_fin > OLD.tiempo_fin THEN
    INSERT INTO "Notificacion" (id_usuario, id_reserva, tipo, titulo, mensaje)
    VALUES (
      NEW.id_usuario, NEW.id, 'reserva_extendida',
      'Tiempo extendido',
      format('Pago recibido. Tu reserva en %s (lugar %s) ahora termina a las %s.',
             v_est, v_lugar,
             to_char(NEW.tiempo_fin AT TIME ZONE 'America/Mexico_City', 'HH24:MI'))
    );
  END IF;

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RAISE WARNING 'reserva_notificar_cambios: %', SQLERRM;
  RETURN NEW;
END;
$function$;

CREATE TRIGGER reserva_notificar
  AFTER UPDATE OF confirmada, tiempo_fin ON "Reserva"
  FOR EACH ROW
  WHEN (OLD.confirmada IS DISTINCT FROM NEW.confirmada
        OR OLD.tiempo_fin IS DISTINCT FROM NEW.tiempo_fin)
  EXECUTE FUNCTION reserva_notificar_cambios();

-- ---------- Estado del cajón ----------
CREATE OR REPLACE FUNCTION public.cajon_notificar_cambio_estado()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_res record;
  v_lugar text;
  v_fin text;
  v_tipo text;
  v_titulo text;
  v_mensaje text;
BEGIN
  -- "Libre" no se avisa: con una reserva en curso el cajón vuelve a "reservado", no a "libre"
  IF NEW.estado_cajon NOT IN (1, 2) THEN
    RETURN NEW;
  END IF;

  SELECT r.id, r.id_usuario, r.tiempo_fin, r.token_estacionamiento,
         COALESCE(e.nombre, 'tu estacionamiento') AS estacionamiento
    INTO v_res
  FROM "Reserva" r
  LEFT JOIN "Estacionamiento" e ON e.id = NEW.id_estacionamiento
  WHERE r.id_cajon = NEW.id
    AND r.confirmada
    AND r.tiempo_inicio <= now()
    AND r.tiempo_fin > now()
  ORDER BY r.tiempo_inicio DESC
  LIMIT 1;

  IF NOT FOUND THEN
    RETURN NEW;  -- el cajón no tiene una reserva en curso: nadie a quien avisar
  END IF;

  v_lugar := NEW.seccion || NEW.numero_cajon::text;
  v_fin := to_char(v_res.tiempo_fin AT TIME ZONE 'America/Mexico_City', 'HH24:MI');

  IF NEW.estado_cajon = 2 AND OLD.estado_cajon = 1 THEN
    v_tipo := 'cajon_desocupado';
    v_titulo := 'Tu cajón quedó vacío';
    v_mensaje := format('El cajón %s ya no tiene vehículo. Tu reserva sigue vigente hasta las %s.', v_lugar, v_fin);

  ELSIF NEW.estado_cajon = 2 THEN
    v_tipo := 'cajon_reservado';
    v_titulo := 'Tu reserva comenzó';
    v_mensaje := format('El cajón %s en %s está apartado para ti hasta las %s.', v_lugar, v_res.estacionamiento, v_fin);

  ELSIF v_res.token_estacionamiento IS NOT NULL THEN
    v_tipo := 'cajon_ocupado';
    v_titulo := 'Vehículo detectado';
    v_mensaje := format('Se detectó un vehículo en el cajón %s.', v_lugar);

  ELSE
    v_tipo := 'cajon_ocupado';
    v_titulo := 'Vehículo detectado en tu cajón';
    v_mensaje := format('Se detectó un vehículo en el cajón %s. Si eres tú, escanea el boleto en la app para confirmar tu llegada; si no, avisa al personal.', v_lugar);
  END IF;

  -- Evita ráfagas si el sensor parpadea: un mismo aviso por reserva cada minuto como máximo
  IF EXISTS (
    SELECT 1 FROM "Notificacion"
    WHERE id_reserva = v_res.id AND tipo = v_tipo
      AND created_at > now() - interval '1 minute'
  ) THEN
    RETURN NEW;
  END IF;

  INSERT INTO "Notificacion" (id_usuario, id_reserva, tipo, titulo, mensaje)
  VALUES (v_res.id_usuario, v_res.id, v_tipo, v_titulo, v_mensaje);

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RAISE WARNING 'cajon_notificar_cambio_estado: %', SQLERRM;
  RETURN NEW;
END;
$function$;

CREATE TRIGGER cajon_estado_notificar
  AFTER UPDATE OF estado_cajon ON "Cajon"
  FOR EACH ROW
  WHEN (OLD.estado_cajon IS DISTINCT FROM NEW.estado_cajon)
  EXECUTE FUNCTION cajon_notificar_cambio_estado();

-- ---------- Recordatorio 15 minutos antes del fin ----------
CREATE OR REPLACE FUNCTION public.notificar_reservas_por_terminar()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
BEGIN
  INSERT INTO "Notificacion" (id_usuario, id_reserva, tipo, titulo, mensaje)
  SELECT r.id_usuario, r.id, 'recordatorio_fin', 'Tu tiempo está por terminar',
         format('Tu reserva en %s termina a las %s (en %s min). %s',
                COALESCE(e.nombre, 'tu estacionamiento'),
                to_char(r.tiempo_fin AT TIME ZONE 'America/Mexico_City', 'HH24:MI'),
                GREATEST(1, ceil(extract(epoch FROM (r.tiempo_fin - now())) / 60))::int,
                CASE WHEN r.token_estacionamiento IS NULL
                     THEN 'Escanea tu boleto en la app para poder extender el tiempo.'
                     ELSE 'Si necesitas más tiempo, extiéndelo desde Inicio.' END)
  FROM "Reserva" r
  LEFT JOIN "Cajon" c ON c.id = r.id_cajon
  LEFT JOIN "Estacionamiento" e ON e.id = c.id_estacionamiento
  WHERE r.confirmada
    AND r.tiempo_inicio <= now()
    AND r.tiempo_fin > now()
    AND r.tiempo_fin <= now() + interval '15 minutes' -- anticipación del aviso
    AND NOT EXISTS (
      SELECT 1 FROM "Notificacion" n
      WHERE n.id_reserva = r.id
        AND n.tipo = 'recordatorio_fin'
        AND n.created_at >= r.tiempo_fin - interval '15 minutes' -- misma ventana
    );
END;
$function$;

SELECT cron.schedule(
  'recordatorios-fin-reserva',
  '* * * * *',
  $$ SELECT notificar_reservas_por_terminar() $$
);
