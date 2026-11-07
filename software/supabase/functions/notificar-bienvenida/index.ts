// Edge Function: manda la notificación de bienvenida al usuario que la pide (el del JWT).
//
// La app la llama una sola vez, justo después de registrar al usuario y guardar su token FCM.
// Solo puede mandarse a sí mismo, con un texto fijo, y una sola vez (bienvenida_enviada).
//
// Secretos necesarios:  FIREBASE_SERVICE_ACCOUNT  (JSON completo de la cuenta de servicio)
// SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los inyecta Supabase automáticamente.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// Envío de notificaciones con la API FCM HTTP v1.
// Se autentica con la cuenta de servicio de Firebase (secreto FIREBASE_SERVICE_ACCOUNT).

export interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

export interface ResultadoEnvio {
  ok: boolean;
  tokenInvalido: boolean; // el token ya no existe (app desinstalada, token rotado)
}

const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL = "https://oauth2.googleapis.com/token";

function base64url(entrada: string | ArrayBuffer): string {
  const bytes = typeof entrada === "string"
    ? new TextEncoder().encode(entrada)
    : new Uint8Array(entrada);
  let binario = "";
  for (const b of bytes) binario += String.fromCharCode(b);
  return btoa(binario).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importarLlavePrivada(pem: string): Promise<CryptoKey> {
  const base64 = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const der = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/** Token OAuth2 de Google (dura 1 hora) firmando un JWT con la cuenta de servicio. */
async function obtenerAccessToken(cuenta: ServiceAccount): Promise<string> {
  const ahora = Math.floor(Date.now() / 1000);
  const encabezado = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: cuenta.client_email,
    scope: SCOPE,
    aud: TOKEN_URL,
    iat: ahora,
    exp: ahora + 3600,
  }));
  const aFirmar = `${encabezado}.${claims}`;

  const llave = await importarLlavePrivada(cuenta.private_key);
  const firma = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    llave,
    new TextEncoder().encode(aFirmar),
  );

  const respuesta = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${aFirmar}.${base64url(firma)}`,
    }),
  });
  if (!respuesta.ok) {
    throw new Error(`Google OAuth respondió ${respuesta.status}: ${await respuesta.text()}`);
  }
  return (await respuesta.json()).access_token;
}

/**
 * Manda un mensaje solo de datos (sin bloque "notification"). Así la app siempre ejecuta
 * onMessageReceived y arma la notificación ella misma, esté abierta o en segundo plano.
 * Los valores de `data` deben ser texto.
 */
export async function enviarMensajeData(
  cuenta: ServiceAccount,
  tokenDispositivo: string,
  data: Record<string, string>,
): Promise<ResultadoEnvio> {
  const accessToken = await obtenerAccessToken(cuenta);

  const respuesta = await fetch(
    `https://fcm.googleapis.com/v1/projects/${cuenta.project_id}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: tokenDispositivo,
          data,
          android: { priority: "HIGH" },
        },
      }),
    },
  );

  if (respuesta.ok) return { ok: true, tokenInvalido: false };

  const cuerpo = await respuesta.text();
  console.error(`FCM respondió ${respuesta.status}: ${cuerpo}`);
  return {
    ok: false,
    tokenInvalido: respuesta.status === 404 || cuerpo.includes("UNREGISTERED"),
  };
}

const responder = (cuerpo: unknown, status = 200) =>
  new Response(JSON.stringify(cuerpo), {
    status,
    headers: { "Content-Type": "application/json" },
  });



Deno.serve(async (req) => {
  if (req.method !== "POST") return responder({ error: "Método no permitido" }, 405);

  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // 1. Quién llama (el id sale del JWT, no del cuerpo de la petición)
  const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: sesion, error: errorSesion } = await admin.auth.getUser(jwt);
  if (errorSesion || !sesion.user) return responder({ error: "No autenticado" }, 401);
  const idUsuario = sesion.user.id;

  // 2. Datos del usuario
  const { data: usuario, error: errorUsuario } = await admin
    .from("Usuario")
    .select("nombre, fcm_token, bienvenida_enviada")
    .eq("id", idUsuario)
    .single();
  if (errorUsuario || !usuario) return responder({ error: "Usuario no encontrado" }, 404);

  if (usuario.bienvenida_enviada) return responder({ enviada: false, motivo: "ya_enviada" });
  if (!usuario.fcm_token) return responder({ enviada: false, motivo: "sin_token" }, 409);

  // 3. Cuenta de servicio de Firebase
  let cuenta: ServiceAccount;
  try {
    cuenta = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "");
  } catch {
    console.error("FIREBASE_SERVICE_ACCOUNT no está configurado o no es un JSON válido");
    return responder({ error: "Servidor sin configurar" }, 500);
  }

  // 4. Enviar
  const nombre = (usuario.nombre ?? "").trim().split(/\s+/)[0];
  let resultado;
  try {
    resultado = await enviarMensajeData(cuenta, usuario.fcm_token, {
      tipo: "bienvenida",
      titulo: "¡Bienvenido a SmartParking!",
      mensaje: `${nombre ? `Hola ${nombre}, t` : "T"}u cuenta ha sido creada exitosamente. Ya puedes reservar tu lugar.`,
    });
  } catch (e) {
    console.error("Error al enviar a FCM:", e);
    return responder({ enviada: false, motivo: "fcm_error" }, 502);
  }

  if (resultado.tokenInvalido) {
    await admin.from("Usuario").update({ fcm_token: null }).eq("id", idUsuario);
    return responder({ enviada: false, motivo: "token_invalido" }, 410);
  }
  if (!resultado.ok) return responder({ enviada: false, motivo: "fcm_error" }, 502);

  // 5. Marcar como enviada
  await admin.from("Usuario").update({ bienvenida_enviada: true }).eq("id", idUsuario);
  return responder({ enviada: true });
});
