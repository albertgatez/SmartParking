-- =====================================================================
-- SmartParking - Preparación para la etapa 2 de RLS
--
-- 1. La vista horarios_ocupados se ejecuta con los permisos de su dueño
--    (security_invoker = false) y tenía permisos de INSERT/UPDATE/DELETE para
--    anon y authenticated. Como es una vista simple, PostgreSQL permite escribir
--    a través de ella y esas escrituras se saltan el RLS de Reserva:
--    cualquiera, incluso sin sesión, podía borrar o modificar reservas ajenas.
--    Ahora solo se puede leer.
-- 2. Se agrega la columna id a la vista, para que la app pueda excluir su propia
--    reserva al revisar la disponibilidad de una extensión (id=neq.<id>).
--    No expone datos del usuario.
-- 3. La función obtener_cajones_con_reservas_hoy no la usa la app, hace
--    referencia a una columna que no existe (Reserva.estado) y era SECURITY
--    DEFINER ejecutable sin sesión. Se le quita el permiso de ejecución.
-- =====================================================================

-- ---------- 1 y 2. Vista horarios_ocupados ----------
-- CREATE OR REPLACE VIEW solo permite agregar columnas al final
CREATE OR REPLACE VIEW public.horarios_ocupados
WITH (security_invoker = false) AS
  SELECT id_cajon, tiempo_inicio, tiempo_fin, id
  FROM "Reserva";

REVOKE ALL ON public.horarios_ocupados FROM anon, authenticated;
GRANT SELECT ON public.horarios_ocupados TO anon, authenticated;

-- ---------- 3. Función sin uso ----------
REVOKE EXECUTE ON FUNCTION public.obtener_cajones_con_reservas_hoy(integer) FROM PUBLIC, anon, authenticated;
