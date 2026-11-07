-- =====================================================================
-- SmartParking - Políticas RLS, etapa 2 (issue 8.2)
-- Se aplica DESPUÉS de la etapa 1 y SOLO cuando:
--   1. La app consulta la disponibilidad desde la vista horarios_ocupados
--      (creada en la etapa 1) en lugar de leer la tabla Reserva.
--   2. El webhook de Stripe ya confirma las reservas (issue 10.1); a partir
--      de aquí la app ya no puede cambiar el campo confirmada.
-- =====================================================================

-- ---------- Reserva ----------
DROP POLICY IF EXISTS "Ver reservas" ON "Reserva";

CREATE POLICY "reserva_select_propias" ON "Reserva"
  FOR SELECT TO authenticated
  USING (auth.uid() = id_usuario);

-- Impide que el usuario se confirme la reserva a sí mismo desde la app
CREATE OR REPLACE FUNCTION public.reserva_proteger_confirmada()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF auth.role() = 'authenticated' AND NEW.confirmada IS DISTINCT FROM OLD.confirmada THEN
    RAISE EXCEPTION 'La confirmación de la reserva la hace el servidor';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reserva_proteger_confirmada ON "Reserva";
CREATE TRIGGER trg_reserva_proteger_confirmada
  BEFORE UPDATE ON "Reserva"
  FOR EACH ROW EXECUTE FUNCTION public.reserva_proteger_confirmada();

-- ---------- Pagos ----------
DROP POLICY IF EXISTS "usuarios_pueden_ver_sus_pagos" ON "Pagos";
CREATE POLICY "pagos_select_propios" ON "Pagos"
  FOR SELECT TO authenticated
  USING (auth.uid() = usuario_id);
-- Insertar solo con service_role (webhook de Stripe).

-- ---------- Usuario, Estacionamiento, Imagen_estacionamiento ----------
-- Se conservan las políticas actuales (ya son correctas).
