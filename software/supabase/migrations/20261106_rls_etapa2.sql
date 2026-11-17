-- =====================================================================
-- SmartParking - Políticas RLS, etapa 2 
-- Se aplica DESPUÉS de la etapa 1 y de 20261117_rls_preparacion_etapa2.sql,
-- y SOLO cuando la app instalada ya:
--   1. Consulta la disponibilidad desde la vista horarios_ocupados en lugar de
--      leer la tabla Reserva (SupabaseClient.obtenerReservasDeHoy y
--      verificarDisponibilidad).
--   2. Ya no confirma ni extiende reservas (lo hace stripe-webhook).
-- Una versión anterior de la app seguiría funcionando, pero mostraría como libres
-- horarios ocupados por otros usuarios (la restricción reserva_sin_traslape
-- impide igual la doble reserva) y no podría marcar su reserva como confirmada.
--
-- Las Edge Functions, el gateway y los triggers usan service_role o
-- SECURITY DEFINER, así que estos cambios no les afectan.
-- =====================================================================

-- ---------- Reserva: cada usuario solo ve las suyas ----------
DROP POLICY IF EXISTS "Ver reservas" ON "Reserva";

CREATE POLICY "reserva_select_propias" ON "Reserva"
  FOR SELECT TO authenticated
  USING (auth.uid() = id_usuario);
-- La disponibilidad de todos se consulta con la vista horarios_ocupados.

-- ---------- Reserva: columnas que la app puede escribir ----------
-- Las políticas limitan las FILAS (solo las propias); los permisos limitan las COLUMNAS.
-- Al crear: los datos de la reserva (confirmada debe ser false por la política;
-- expiracion la fija el trigger reserva_validar_insert).
REVOKE INSERT ON "Reserva" FROM anon, authenticated;
GRANT INSERT (id_usuario, id_cajon, tiempo_inicio, tiempo_fin, confirmada) ON "Reserva" TO authenticated;

-- Al modificar: solo el boleto escaneado. confirmada y tiempo_fin los cambia stripe-webhook.
REVOKE UPDATE ON "Reserva" FROM anon, authenticated;
GRANT UPDATE (token_estacionamiento) ON "Reserva" TO authenticated;

-- Segunda barrera por si en el futuro se vuelve a dar permiso sobre confirmada
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

-- ---------- Usuario: columnas que la app puede escribir ----------
-- customer_id lo escriben las Edge Functions de pago y bienvenida_enviada
-- notificar-bienvenida (service_role). contrasena no la usa nadie.
REVOKE INSERT, UPDATE ON "Usuario" FROM anon, authenticated;
GRANT INSERT (id, nombre, correo, telefono, discapacitado) ON "Usuario" TO authenticated;
GRANT UPDATE (nombre, correo, telefono, discapacitado, url_foto_perfil, fcm_token) ON "Usuario" TO authenticated;

-- ---------- Pagos ----------
DROP POLICY IF EXISTS "usuarios_pueden_ver_sus_pagos" ON "Pagos";
CREATE POLICY "pagos_select_propios" ON "Pagos"
  FOR SELECT TO authenticated
  USING (auth.uid() = usuario_id);
-- Insertar solo con service_role (webhook de Stripe).

-- ---------- Estacionamiento, Imagen_estacionamiento, Cajon, Notificacion ----------
-- Se conservan las políticas actuales (ya son correctas).

-- ---------- Opcional: columna Usuario.contrasena ----------
-- Las contraseñas las guarda Supabase Auth. Revisar que nada la use antes de borrarla.
-- UPDATE "Usuario" SET contrasena = NULL;
-- ALTER TABLE "Usuario" DROP COLUMN contrasena;
