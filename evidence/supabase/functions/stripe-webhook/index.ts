// Edge Function: stripe-webhook  (issues 10.1 y 12.2)
// Stripe avisa cuando un pago se completa. Aquí, y no en la app, se confirma
// o extiende la reserva y se registra el pago.
// Desplegar con:  supabase functions deploy stripe-webhook --no-verify-jwt

import Stripe from "npm:stripe@14.25.0";
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY")!, { apiVersion: "2024-06-20" });
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const WEBHOOK_SECRET = Deno.env.get("STRIPE_WEBHOOK_SECRET")!;
const HORA_MS = 60 * 60 * 1000;

Deno.serve(async (req) => {
  // 1. Verificar que el evento viene de Stripe
  const firma = req.headers.get("stripe-signature") ?? "";
  const cuerpo = await req.text();
  let evento: Stripe.Event;
  try {
    evento = await stripe.webhooks.constructEventAsync(cuerpo, firma, WEBHOOK_SECRET);
  } catch {
    return new Response("Firma inválida", { status: 400 });
  }

  if (evento.type !== "payment_intent.succeeded") {
    return new Response("Evento ignorado", { status: 200 });
  }

  const intent = evento.data.object as Stripe.PaymentIntent;
  const m = intent.metadata;
  const idReserva = Number(m.id_reserva);

  // 2. Evitar duplicados: Stripe puede mandar el mismo evento más de una vez
  const { data: existente } = await admin
    .from("Pagos").select("id").eq("stripe_payment_intent", intent.id).maybeSingle();
  if (existente) return new Response("Ya procesado", { status: 200 });

  // 3. Confirmar o extender la reserva
  if (m.tipo === "extension") {
    const { data: r } = await admin.from("Reserva").select("tiempo_fin").eq("id", idReserva).single();
    if (r) {
      const nuevoFin = new Date(Date.parse(r.tiempo_fin) + Number(m.horas) * HORA_MS).toISOString();
      const { error } = await admin.from("Reserva").update({ tiempo_fin: nuevoFin }).eq("id", idReserva);
      // Si otra reserva ocupó ese horario (restricción EXCLUDE), se reembolsa
      if (error) await stripe.refunds.create({ payment_intent: intent.id });
    }
  } else {
    await admin.from("Reserva").update({ confirmada: true, expiracion: null }).eq("id", idReserva);
  }

  // 4. Registrar el pago con su desglose
  const { error } = await admin.from("Pagos").insert({
    id_reserva: String(idReserva),
    usuario_id: m.usuario_id,
    id_usuario: typeof intent.customer === "string" ? intent.customer : null,
    id_estacionamiento: m.id_estacionamiento,
    tipo: m.tipo,
    subtotal: Number(m.subtotal),
    comision: Number(m.comision),
    descuento: Number(m.descuento),
    monto_total: intent.amount_received / 100,
    stripe_payment_intent: intent.id,
    metadata: { cupon: m.cupon || null, horas: Number(m.horas) },
  });

  if (error) {
    console.error("No se pudo registrar el pago", intent.id, error.message);
    return new Response("Error al registrar", { status: 500 }); // Stripe reintentará
  }
  return new Response("ok", { status: 200 });
});
