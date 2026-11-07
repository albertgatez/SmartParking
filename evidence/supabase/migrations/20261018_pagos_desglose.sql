-- =====================================================================
-- SmartParking - Desglose de pagos (issues 10.1 y 12.2)
-- PROPUESTA: revisar en equipo antes de aplicar.
-- =====================================================================

-- Pagos no tenía llave primaria (issue 4.2)
ALTER TABLE "Pagos" ADD PRIMARY KEY (id);

ALTER TABLE "Pagos"
  ADD COLUMN IF NOT EXISTS comision double precision,
  ADD COLUMN IF NOT EXISTS descuento double precision DEFAULT 0,
  ADD COLUMN IF NOT EXISTS tipo text CHECK (tipo IN ('reserva', 'extension')),
  ADD COLUMN IF NOT EXISTS stripe_payment_intent text UNIQUE;  -- evita registrar dos veces el mismo pago

-- El trigger actual sobrescribe usuario_id buscando por customer_id.
-- Ahora solo lo rellena si el webhook no lo mandó.
CREATE OR REPLACE FUNCTION public.rellenar_id_en_pagos()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.usuario_id IS NULL THEN
    NEW.usuario_id := (SELECT id FROM "Usuario" WHERE customer_id = NEW.id_usuario LIMIT 1);
  END IF;
  RETURN NEW;
END;
$$;
