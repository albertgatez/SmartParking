-- =====================================================================
-- SmartParking - Token de Firebase Cloud Messaging (issue 11.1)
-- Aplicada el 05/11/2026
-- =====================================================================

ALTER TABLE "Usuario"
  ADD COLUMN IF NOT EXISTS fcm_token text,
  ADD COLUMN IF NOT EXISTS bienvenida_enviada boolean NOT NULL DEFAULT false;

-- Los usuarios que ya existían no reciben el aviso de bienvenida
UPDATE "Usuario" SET bienvenida_enviada = true;

-- La política RLS existente de Usuario (auth.uid() = id) ya protege ambas columnas.
