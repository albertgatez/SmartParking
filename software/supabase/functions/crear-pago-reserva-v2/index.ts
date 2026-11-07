// Edge Function: crear-pago-reserva-v2  (issues 10.1 y 12.2)
// Versión en un solo archivo para desplegar desde el editor del panel de Supabase.
// La app manda qué quiere pagar; el monto lo calcula el servidor con los datos de la BD.
//
// POST  { id_reserva, tipo: "reserva" | "extension", horas_extra?, cupon?, solo_cotizar? }
// 200   { clientSecret, customerId, desglose }     (o solo { desglose } si solo_cotizar)

import Stripe from "npm:stripe@14.25.0";
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

// ---------- Cálculo del cobro (incluido aquí para desplegar desde el panel) ----------
// Fórmula de cobro de SmartParking (issue 12.1).
// Única fuente de verdad: la app solo muestra lo que calcula el servidor.

type TipoPago = "reserva" | "extension";

const COMISION: Record<TipoPago, number> = {
  reserva: 0.04,   // 4 %
  extension: 0.05, // 5 %
};

// Cupones válidos (solo aplican a reservas)
const CUPONES: Record<string, number> = {
  PROMO10: 0.10,
};

interface Desglose {
  tipo: TipoPago;
  horas: number;
  precio_hora: number;
  subtotal: number;
  comision: number;
  porcentaje_comision: number;
  cupon: string | null;
  descuento: number;
  total: number;
  total_centavos: number;
}

const redondear = (n: number) => Math.round(n * 100) / 100;

function calcularDesglose(
  tipo: TipoPago,
  horas: number,
  precioHora: number,
  cupon?: string | null,
): Desglose {
  if (!Number.isInteger(horas) || horas < 1) throw new Error("Horas inválidas");

  const codigo = cupon?.trim().toUpperCase() || null;
  const pctCupon = tipo === "reserva" && codigo ? CUPONES[codigo] : undefined;
  if (codigo && pctCupon === undefined) throw new Error("Cupón inválido");

  const subtotal = redondear(horas * precioHora);
  const comision = redondear(subtotal * COMISION[tipo]);
  const descuento = pctCupon ? redondear(subtotal * pctCupon) : 0;
  const total = redondear(subtotal + comision - descuento);

  return {
    tipo,
    horas,
    precio_hora: precioHora,
    subtotal,
    comision,
    porcentaje_comision: COMISION[tipo],
    cupon: pctCupon ? codigo : null,
    descuento,
    total,
    total_centavos: Math.round(total * 100),
  };
}
// ---------- Fin del cálculo ----------

const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY")!, { apiVersion: "2024-06-20" });
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

const HORA_MS = 60 * 60 * 1000;
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  // 1. Usuario autenticado (a partir de su JWT)
  const jwt = req.headers.get("Authorization")?.replace("Bearer ", "");
  const { data: auth } = await admin.auth.getUser(jwt ?? "");
  const user = auth?.user;
  if (!user) return json({ error: "No autenticado" }, 401);

  let body: { id_reserva?: number; tipo?: TipoPago; horas_extra?: number; cupon?: string; solo_cotizar?: boolean };
  try { body = await req.json(); } catch { return json({ error: "JSON inválido" }, 400); }

  const tipo: TipoPago = body.tipo === "extension" ? "extension" : "reserva";

  // 2. Reserva, cajón y precio del estacionamiento
  const { data: reserva } = await admin
    .from("Reserva")
    .select("id, id_usuario, id_cajon, tiempo_inicio, tiempo_fin, confirmada, expiracion, Cajon(id_estacionamiento, Estacionamiento(precio_hora))")
    .eq("id", body.id_reserva ?? -1)
    .single();

  if (!reserva || reserva.id_usuario !== user.id) return json({ error: "Reserva no encontrada" }, 404);

  // deno-lint-ignore no-explicit-any
  const cajon = reserva.Cajon as any;
  const precioHora = Number(cajon?.Estacionamiento?.precio_hora);
  const idEstacionamiento = cajon?.id_estacionamiento;

  // 3. Validaciones y horas a cobrar
  let horas: number;
  if (tipo === "reserva") {
    if (reserva.confirmada) return json({ error: "La reserva ya está pagada" }, 409);
    if (reserva.expiracion && new Date(reserva.expiracion) < new Date())
      return json({ error: "La reserva expiró" }, 410);
    horas = Math.round((Date.parse(reserva.tiempo_fin) - Date.parse(reserva.tiempo_inicio)) / HORA_MS);
  } else {
    if (!reserva.confirmada) return json({ error: "Solo se extienden reservas pagadas" }, 409);
    horas = Number(body.horas_extra);
    if (!Number.isInteger(horas) || horas < 1 || horas > 3) return json({ error: "Horas extra inválidas (1 a 3)" }, 400);

    // Las horas extra deben estar libres
    const finActual = reserva.tiempo_fin;
    const finNuevo = new Date(Date.parse(finActual) + horas * HORA_MS).toISOString();
    const { count } = await admin
      .from("Reserva")
      .select("id", { count: "exact", head: true })
      .eq("id_cajon", reserva.id_cajon)
      .neq("id", reserva.id)
      .lt("tiempo_inicio", finNuevo)
      .gt("tiempo_fin", finActual);
    if ((count ?? 0) > 0) return json({ error: "El cajón está ocupado en ese horario" }, 409);
  }

  // 4. Desglose calculado en el servidor
  let desglose;
  try {
    desglose = calcularDesglose(tipo, horas, precioHora, body.cupon);
  } catch (e) {
    return json({ error: (e as Error).message }, 400);
  }
  if (body.solo_cotizar) return json({ desglose });

  // 5. Cliente de Stripe del usuario (se crea la primera vez)
  const { data: perfil } = await admin.from("Usuario").select("customer_id, correo, nombre").eq("id", user.id).single();
  let customerId = perfil?.customer_id as string | null;
  if (!customerId) {
    const customer = await stripe.customers.create({ email: perfil?.correo, name: perfil?.nombre, metadata: { usuario_id: user.id } });
    customerId = customer.id;
    await admin.from("Usuario").update({ customer_id: customerId }).eq("id", user.id);
  }

  // 6. PaymentIntent con el monto del servidor; el desglose viaja en metadata para el webhook
  const intent = await stripe.paymentIntents.create({
    amount: desglose.total_centavos,
    currency: "mxn",
    customer: customerId,
    automatic_payment_methods: { enabled: true },
    metadata: {
      id_reserva: String(reserva.id),
      usuario_id: user.id,
      id_estacionamiento: String(idEstacionamiento),
      tipo,
      horas: String(horas),
      subtotal: desglose.subtotal.toFixed(2),
      comision: desglose.comision.toFixed(2),
      descuento: desglose.descuento.toFixed(2),
      total: desglose.total.toFixed(2),
      cupon: desglose.cupon ?? "",
    },
  });

  return json({ clientSecret: intent.client_secret, customerId, desglose });
});
