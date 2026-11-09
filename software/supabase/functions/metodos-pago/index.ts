// Edge Function: metodos-pago  (issue 7.3)
// Administra las tarjetas guardadas en Stripe del usuario que llama.
// El cliente de Stripe se obtiene SIEMPRE a partir del JWT; nunca se acepta un customer_id de la app.
//
// Desplegar CON verificación de JWT (es el valor por defecto):
//   cd software && supabase functions deploy metodos-pago
//
// POST { accion, api_version, id_metodo? }
//   "listar"        200 { metodos: [{ id, marca, ultimos4, mes_exp, anio_exp, predeterminada }] }
//   "preparar_alta" 200 { setupIntentClientSecret, customerId, ephemeralKey }
//   "llave_cliente" 200 { customerId, ephemeralKey }
//   "eliminar"      200 { eliminado: true }
//   "predeterminar" 200 { predeterminada: "pm_..." }
// La tarjeta predeterminada se guarda en Stripe (customer.invoice_settings.default_payment_method).
// Si el usuario tiene tarjetas y ninguna es predeterminada, "listar" marca la más reciente.
// Errores: { error } con 400, 401, 403, 404, 405 o 500.
//
// Secretos: STRIPE_SECRET_KEY. SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los inyecta Supabase.

import Stripe from "npm:stripe@14.25.0";
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY")!, { apiVersion: "2024-06-20" });
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

const ACCIONES = ["listar", "preparar_alta", "llave_cliente", "eliminar", "predeterminar"] as const;
type Accion = typeof ACCIONES[number];

// Versión de la API de Stripe que manda el SDK de Android, por ejemplo "2020-03-02"
// (puede traer códigos beta separados por ";").
const FORMATO_API_VERSION = /^\d{4}-\d{2}-\d{2}(;[A-Za-z0-9_=.]+)*$/;

class ErrorHttp extends Error {
  constructor(public status: number, mensaje: string) {
    super(mensaje);
  }
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

/** Devuelve el customer_id del usuario; si no tiene, crea el cliente en Stripe y lo guarda. */
async function obtenerCliente(usuarioId: string): Promise<string> {
  const { data: perfil, error } = await admin
    .from("Usuario")
    .select("customer_id, correo, nombre")
    .eq("id", usuarioId)
    .maybeSingle();
  if (error) throw error;
  if (!perfil) throw new ErrorHttp(404, "Usuario no encontrado");
  if (perfil.customer_id) return perfil.customer_id as string;

  const cliente = await stripe.customers.create({
    email: perfil.correo ?? undefined,
    name: perfil.nombre ?? undefined,
    metadata: { usuario_id: usuarioId },
  });

  // Solo se guarda si sigue vacío, por si dos peticiones llegan al mismo tiempo
  const { data: actualizado, error: errorUpdate } = await admin
    .from("Usuario")
    .update({ customer_id: cliente.id })
    .eq("id", usuarioId)
    .is("customer_id", null)
    .select("customer_id");
  if (errorUpdate) throw errorUpdate;

  if (!actualizado || actualizado.length === 0) {
    // Otra petición ya guardó un cliente: se usa ese y se borra el que sobra
    await stripe.customers.del(cliente.id).catch(() => {});
    const { data: otro } = await admin.from("Usuario").select("customer_id").eq("id", usuarioId).single();
    if (!otro?.customer_id) throw new Error("No se pudo guardar el cliente de Stripe");
    return otro.customer_id as string;
  }
  return cliente.id;
}

async function crearLlaveCliente(customerId: string, apiVersion: string): Promise<string> {
  const llave = await stripe.ephemeralKeys.create({ customer: customerId }, { apiVersion });
  return llave.secret!;
}

/** Valida el id y verifica que la tarjeta pertenezca al cliente; si no, lanza 400, 403 o 404. */
async function verificarDueno(idMetodo: unknown, customerId: string): Promise<string> {
  if (typeof idMetodo !== "string" || !idMetodo.startsWith("pm_")) {
    throw new ErrorHttp(400, "id_metodo inválido");
  }

  let metodo: Stripe.PaymentMethod;
  try {
    metodo = await stripe.paymentMethods.retrieve(idMetodo);
  } catch (e) {
    if ((e as { code?: string }).code === "resource_missing") {
      throw new ErrorHttp(404, "Método de pago no encontrado");
    }
    throw e;
  }

  const dueno = typeof metodo.customer === "string" ? metodo.customer : metodo.customer?.id;
  if (dueno !== customerId) throw new ErrorHttp(403, "El método de pago no pertenece al usuario");
  return idMetodo;
}

/** Id de la tarjeta predeterminada del cliente en Stripe, o null. */
async function obtenerPredeterminada(customerId: string): Promise<string | null> {
  const cliente = await stripe.customers.retrieve(customerId);
  if (cliente.deleted) return null;
  const pm = cliente.invoice_settings?.default_payment_method;
  if (!pm) return null;
  return typeof pm === "string" ? pm : pm.id;
}

async function guardarPredeterminada(customerId: string, idMetodo: string): Promise<void> {
  await stripe.customers.update(customerId, { invoice_settings: { default_payment_method: idMetodo } });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  try {
    // 1. Usuario autenticado (a partir de su JWT)
    const jwt = req.headers.get("Authorization")?.replace("Bearer ", "");
    const { data: auth } = await admin.auth.getUser(jwt ?? "");
    const user = auth?.user;
    if (!user) return json({ error: "No autenticado" }, 401);

    // 2. Cuerpo de la petición
    let body: { accion?: string; api_version?: string; id_metodo?: string };
    try { body = await req.json(); } catch { return json({ error: "JSON inválido" }, 400); }

    const accion = body.accion as Accion;
    if (!ACCIONES.includes(accion)) return json({ error: "Acción inválida" }, 400);

    const apiVersion = body.api_version ?? "";
    if ((accion === "preparar_alta" || accion === "llave_cliente") && !FORMATO_API_VERSION.test(apiVersion)) {
      return json({ error: "api_version inválida" }, 400);
    }

    // 3. Cliente de Stripe del usuario (se crea la primera vez)
    const customerId = await obtenerCliente(user.id);

    // 4. Acción
    switch (accion) {
      case "listar": {
        const lista = await stripe.paymentMethods.list({ customer: customerId, type: "card", limit: 100 });

        // La predeterminada debe ser una de sus tarjetas; si no hay, se marca la más reciente
        let predeterminada = await obtenerPredeterminada(customerId);
        if (lista.data.length > 0 && !lista.data.some((m) => m.id === predeterminada)) {
          predeterminada = lista.data[0].id;
          await guardarPredeterminada(customerId, predeterminada);
        }

        const metodos = lista.data.map((m) => ({
          id: m.id,
          marca: m.card?.brand ?? "",
          ultimos4: m.card?.last4 ?? "",
          mes_exp: m.card?.exp_month ?? 0,
          anio_exp: m.card?.exp_year ?? 0,
          predeterminada: m.id === predeterminada,
        }));
        return json({ metodos });
      }

      case "preparar_alta": {
        const setupIntent = await stripe.setupIntents.create({
          customer: customerId,
          usage: "off_session",
          payment_method_types: ["card"],
          metadata: { usuario_id: user.id },
        });
        const ephemeralKey = await crearLlaveCliente(customerId, apiVersion);
        return json({ setupIntentClientSecret: setupIntent.client_secret, customerId, ephemeralKey });
      }

      case "llave_cliente": {
        const ephemeralKey = await crearLlaveCliente(customerId, apiVersion);
        return json({ customerId, ephemeralKey });
      }

      case "eliminar": {
        const idMetodo = await verificarDueno(body.id_metodo, customerId);
        await stripe.paymentMethods.detach(idMetodo);
        return json({ eliminado: true });
      }

      case "predeterminar": {
        const idMetodo = await verificarDueno(body.id_metodo, customerId);
        await guardarPredeterminada(customerId, idMetodo);
        return json({ predeterminada: idMetodo });
      }
    }
  } catch (e) {
    if (e instanceof ErrorHttp) return json({ error: e.message }, e.status);
    console.error("metodos-pago:", e);
    return json({ error: "Error interno" }, 500);
  }
});
