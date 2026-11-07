-- =====================================================================
-- SmartParking - Envío de notificaciones push (issue 11.2)
-- Aplicada el 06/11/2026
--
-- Cada fila nueva en Notificacion llama a la Edge Function
-- enviar-notificacion, que la manda a Firebase.
--
-- <secreto> debe ser IDÉNTICO al secreto FCM_WEBHOOK_SECRET de las
-- Edge Functions. El valor real NO se sube al repositorio.
-- =====================================================================

CREATE TRIGGER notificacion_enviar_push
  AFTER INSERT ON "Notificacion"
  FOR EACH ROW
  EXECUTE FUNCTION supabase_functions.http_request(
    'https://kjbmwwbzdsvunzndeusf.supabase.co/functions/v1/enviar-notificacion',
    'POST',
    '{"Content-Type":"application/json","x-webhook-secret":"<secreto>"}',
    '{}',
    '5000'
  );
