-- =====================================================================
-- SmartParking - Políticas RLS, etapa 1 (issue 8.2)
--
-- Protege la escritura sin cambiar la app:
--   * Cada usuario solo puede crear, modificar y borrar SUS reservas.
--   * Nadie puede crear reservas sin sesión.
--   * Nadie puede crear ni modificar cajones desde la app; solo el gateway
--     con la llave secreta (service_role, que se salta RLS).
--
-- Se conserva por ahora la lectura de todas las reservas, porque la app
-- la usa para calcular los horarios ocupados. Se crea la vista
-- horarios_ocupados para que la app la use, y en la etapa 2 se cierra
-- la lectura directa de la tabla.
--
-- REQUISITO: el .env del gateway debe tener la llave secreta (sb_secret_...)
-- antes de aplicar este script. Si no, el gateway deja de actualizar Cajon.
-- =====================================================================

-- ---------- Reserva ----------
DROP POLICY IF EXISTS "Enable insert for authenticated users only" ON "Reserva";
DROP POLICY IF EXISTS "Actualizar reserva" ON "Reserva";
DROP POLICY IF EXISTS "Eliminar reserva" ON "Reserva";

-- Solo usuarios con sesión, solo a su nombre y sin confirmar
CREATE POLICY "reserva_insert_propia_pendiente" ON "Reserva"
  FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = id_usuario AND confirmada = false);

-- Solo sus reservas, y no las puede pasar a otro usuario
CREATE POLICY "reserva_update_propias" ON "Reserva"
  FOR UPDATE TO authenticated
  USING (auth.uid() = id_usuario)
  WITH CHECK (auth.uid() = id_usuario);

-- Solo sus reservas pendientes (las confirmadas quedan como historial)
CREATE POLICY "reserva_delete_propias_pendientes" ON "Reserva"
  FOR DELETE TO authenticated
  USING (auth.uid() = id_usuario AND confirmada = false);

-- "Ver reservas" (SELECT para todos) se conserva hasta la etapa 2.

-- Vista para que la app consulte la disponibilidad sin ver quién reservó.
-- Se crea desde ahora para poder cambiar la app antes de la etapa 2.
CREATE OR REPLACE VIEW public.horarios_ocupados
WITH (security_invoker = false) AS
  SELECT id_cajon, tiempo_inicio, tiempo_fin
  FROM "Reserva";

GRANT SELECT ON public.horarios_ocupados TO anon, authenticated;


-- ---------- Cajon ----------
DROP POLICY IF EXISTS "Actualizar" ON "Cajon";
DROP POLICY IF EXISTS "Enable insert for authenticated users only" ON "Cajon";
-- "Ver cajones" (SELECT para todos) se conserva.
-- Crear o modificar cajones: solo con la llave secreta (gateway y panel).

-- ---------- Usuario, Pagos, Estacionamiento, Imagen_estacionamiento ----------
-- Sin cambios: sus políticas actuales ya son correctas.
