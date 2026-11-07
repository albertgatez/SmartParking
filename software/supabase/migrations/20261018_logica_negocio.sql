-- =====================================================================
-- SmartParking - Reglas de negocio en la BD (issue 8.3)
-- PROPUESTA: revisar en equipo antes de aplicar.
-- Antes: resolver el traslape existente entre las reservas 173 y 174.
-- =====================================================================

-- RN-01 / RN-04: un cajón no puede tener reservas que se crucen
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE "Reserva"
  ADD CONSTRAINT reserva_sin_traslape
  EXCLUDE USING gist (
    id_cajon WITH =,
    tstzrange(tiempo_inicio, tiempo_fin) WITH &&
  );

ALTER TABLE "Reserva"
  ADD CONSTRAINT reserva_horario_valido CHECK (tiempo_fin > tiempo_inicio);

-- RN-03 / RN-05: validaciones al crear una reserva
CREATE OR REPLACE FUNCTION public.reserva_validar_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  -- Una sola reserva activa por usuario
  IF EXISTS (
    SELECT 1 FROM "Reserva"
    WHERE id_usuario = NEW.id_usuario
      AND tiempo_fin > now()
      AND (confirmada OR expiracion > now())  -- ignora pendientes ya vencidas
  ) THEN
    RAISE EXCEPTION 'El usuario ya tiene una reserva activa' USING ERRCODE = 'P0001';
  END IF;

  -- Cajones para discapacidad
  IF (SELECT discapacitado FROM "Cajon" WHERE id = NEW.id_cajon)
     AND NOT COALESCE((SELECT discapacitado FROM "Usuario" WHERE id = NEW.id_usuario), false) THEN
    RAISE EXCEPTION 'Cajón exclusivo para personas con discapacidad' USING ERRCODE = 'P0002';
  END IF;

  -- La expiración siempre la fija el servidor
  NEW.expiracion := now() + interval '5 minutes';
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reserva_validar_insert ON "Reserva";
CREATE TRIGGER trg_reserva_validar_insert
  BEFORE INSERT ON "Reserva"
  FOR EACH ROW EXECUTE FUNCTION public.reserva_validar_insert();

-- RN-02: eliminar reservas pendientes vencidas cada minuto
CREATE EXTENSION IF NOT EXISTS pg_cron;

SELECT cron.schedule(
  'limpiar-reservas-pendientes',
  '* * * * *',
  $$ DELETE FROM "Reserva" WHERE confirmada = false AND expiracion < now() $$
);

-- Para quitar la tarea:  SELECT cron.unschedule('limpiar-reservas-pendientes');
