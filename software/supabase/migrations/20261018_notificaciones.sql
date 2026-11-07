-- =====================================================================
-- SmartParking - Notificaciones push (issues 11.1 y 11.2)
-- PROPUESTA: revisar en equipo antes de aplicar.
-- Requiere desplegar la Edge Function enviar-notificacion y guardar en
-- Vault la URL del proyecto y la llave service_role:
--   SELECT vault.create_secret('https://kjbmwwbzdsvunzndeusf.supabase.co', 'project_url');
--   SELECT vault.create_secret('<service_role key>', 'service_role_key');
-- =====================================================================

-- ---------- Tokens de los teléfonos (11.1) ----------
CREATE TABLE IF NOT EXISTS public."Dispositivo" (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES public."Usuario"(id) ON DELETE CASCADE,
  token_fcm text NOT NULL UNIQUE,
  plataforma text NOT NULL DEFAULT 'android',
  actualizado_en timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public."Dispositivo" ENABLE ROW LEVEL SECURITY;
CREATE POLICY "dispositivo_propio" ON public."Dispositivo"
  FOR ALL TO authenticated
  USING (auth.uid() = usuario_id) WITH CHECK (auth.uid() = usuario_id);

-- ---------- Historial de avisos (11.2) ----------
CREATE TABLE IF NOT EXISTS public."Notificacion" (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES public."Usuario"(id) ON DELETE CASCADE,
  tipo text NOT NULL CHECK (tipo IN ('confirmada', 'por_terminar', 'cajon_ocupado', 'expirada')),
  titulo text NOT NULL,
  cuerpo text NOT NULL,
  id_reserva bigint,
  leida boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public."Notificacion" ENABLE ROW LEVEL SECURITY;
CREATE POLICY "notificacion_propia_select" ON public."Notificacion"
  FOR SELECT TO authenticated USING (auth.uid() = usuario_id);
CREATE POLICY "notificacion_propia_update" ON public."Notificacion"
  FOR UPDATE TO authenticated USING (auth.uid() = usuario_id);

ALTER TABLE public."Reserva" ADD COLUMN IF NOT EXISTS aviso_fin_enviado boolean NOT NULL DEFAULT false;

-- ---------- Llamada a la Edge Function ----------
CREATE OR REPLACE FUNCTION public.notificar(p_usuario uuid, p_tipo text, p_titulo text, p_cuerpo text, p_reserva bigint)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  PERFORM net.http_post(
    url := (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'project_url') || '/functions/v1/enviar-notificacion',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'service_role_key')
    ),
    body := jsonb_build_object('usuario_id', p_usuario, 'tipo', p_tipo, 'titulo', p_titulo, 'cuerpo', p_cuerpo, 'id_reserva', p_reserva)
  );
END;
$$;

-- ---------- N2: 15 minutos antes del fin (cada minuto) ----------
CREATE OR REPLACE FUNCTION public.avisar_reservas_por_terminar()
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r record;
BEGIN
  FOR r IN
    UPDATE "Reserva" SET aviso_fin_enviado = true
    WHERE confirmada AND NOT aviso_fin_enviado
      AND tiempo_fin BETWEEN now() AND now() + interval '15 minutes'
    RETURNING id, id_usuario
  LOOP
    PERFORM notificar(r.id_usuario, 'por_terminar', 'Tu tiempo está por terminar',
                      'Te quedan 15 minutos. Puedes extender tu reserva desde la app.', r.id);
  END LOOP;
END;
$$;

SELECT cron.schedule('avisar-reservas-por-terminar', '* * * * *', $$ SELECT public.avisar_reservas_por_terminar() $$);

-- ---------- N3: alguien ocupó un cajón reservado ----------
CREATE OR REPLACE FUNCTION public.avisar_cajon_ocupado()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r record;
BEGIN
  IF NEW.estado_cajon = 1 AND OLD.estado_cajon IS DISTINCT FROM 1 THEN
    SELECT id, id_usuario INTO r FROM "Reserva"
    WHERE id_cajon = NEW.id AND confirmada
      AND token_estacionamiento IS NULL
      AND now() BETWEEN tiempo_inicio AND tiempo_fin
    LIMIT 1;
    IF FOUND THEN
      PERFORM notificar(r.id_usuario, 'cajon_ocupado', 'Tu lugar fue ocupado',
                        'Alguien se estacionó en tu cajón reservado. Avisa al personal del estacionamiento.', r.id);
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_avisar_cajon_ocupado ON "Cajon";
CREATE TRIGGER trg_avisar_cajon_ocupado
  AFTER UPDATE OF estado_cajon ON "Cajon"
  FOR EACH ROW EXECUTE FUNCTION public.avisar_cajon_ocupado();

-- N1 (reserva confirmada) lo envía el stripe-webhook llamando a enviar-notificacion.
