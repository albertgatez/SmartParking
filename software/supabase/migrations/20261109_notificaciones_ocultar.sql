-- Migración: el usuario puede quitar avisos de su pestaña Avisos (botón X o deslizar a la derecha).
--
-- Los avisos NO se borran: se marcan con oculta = true. Si se borraran, la tarea de
-- recordatorios (notificar_reservas_por_terminar, revisa con NOT EXISTS que no se haya enviado
-- ya el aviso) y el trigger del sensor del cajón (evita repetir el mismo aviso en 1 minuto)
-- volverían a crear el aviso y a mandar la notificación push.
--
-- El usuario solo puede cambiar la columna "oculta" de sus propios avisos.
-- Aplicar una sola vez en SQL Editor (ver software/supabase/README.md).

ALTER TABLE "Notificacion"
  ADD COLUMN IF NOT EXISTS oculta boolean NOT NULL DEFAULT false;

-- Solo la columna "oculta" se puede modificar desde la app
REVOKE UPDATE ON "Notificacion" FROM authenticated;
GRANT UPDATE (oculta) ON "Notificacion" TO authenticated;

-- Y solo en avisos propios
CREATE POLICY "notificacion_ocultar_propias" ON "Notificacion"
  FOR UPDATE TO authenticated
  USING (auth.uid() = id_usuario)
  WITH CHECK (auth.uid() = id_usuario);
