-- =====================================================================
-- SmartParking - Políticas RLS (issue 8.2)
-- PROPUESTA: revisar en equipo antes de aplicar.
-- Requiere que el gateway use la llave service_role (se salta RLS).
-- =====================================================================

-- ---------- Reserva ----------
DROP POLICY IF EXISTS "Ver reservas" ON "Reserva";
DROP POLICY IF EXISTS "Enable insert for authenticated users only" ON "Reserva";
DROP POLICY IF EXISTS "Actualizar reserva" ON "Reserva";
DROP POLICY IF EXISTS "Eliminar reserva" ON "Reserva";

CREATE POLICY "reserva_select_propias" ON "Reserva"
  FOR SELECT TO authenticated
  USING (auth.uid() = id_usuario);

CREATE POLICY "reserva_insert_propia_pendiente" ON "Reserva"
  FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = id_usuario AND confirmada = false);

-- El usuario puede registrar su QR o extender su reserva, pero no confirmarla ni pasarla a otro usuario.
-- La confirmación la hace el webhook de Stripe con service_role.
CREATE POLICY "reserva_update_propias" ON "Reserva"
  FOR UPDATE TO authenticated
  USING (auth.uid() = id_usuario)
  WITH CHECK (auth.uid() = id_usuario);

CREATE POLICY "reserva_delete_propias_pendientes" ON "Reserva"
  FOR DELETE TO authenticated
  USING (auth.uid() = id_usuario AND confirmada = false);

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

-- Disponibilidad sin exponer quién reservó
CREATE OR REPLACE VIEW public.horarios_ocupados
WITH (security_invoker = false) AS
  SELECT id_cajon, tiempo_inicio, tiempo_fin
  FROM "Reserva";

GRANT SELECT ON public.horarios_ocupados TO anon, authenticated;

-- ---------- Cajon ----------
DROP POLICY IF EXISTS "Actualizar" ON "Cajon";
DROP POLICY IF EXISTS "Enable insert for authenticated users only" ON "Cajon";
-- Se conserva "Ver cajones" (SELECT para todos). Escribir solo con service_role.

-- ---------- Pagos ----------
DROP POLICY IF EXISTS "usuarios_pueden_ver_sus_pagos" ON "Pagos";
CREATE POLICY "pagos_select_propios" ON "Pagos"
  FOR SELECT TO authenticated
  USING (auth.uid() = usuario_id);
-- Insertar solo con service_role (webhook de Stripe).

-- ---------- Usuario, Estacionamiento, Imagen_estacionamiento ----------
-- Se conservan las políticas actuales (ya son correctas).
