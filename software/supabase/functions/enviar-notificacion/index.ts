// Edge Function: manda como push (FCM) cada fila nueva de la tabla "Notificacion".
//
// La llama un trigger de la base de datos (Database Webhook) con este cuerpo:
//   { "type": "INSERT", "table": "Notificacion", "record": { id, id_usuario, tipo, titulo, mensaje, ... } }
//
// Desplegar SIN verificación de JWT (el webhook no manda uno; se autentica con un secreto):
//   supabase functions deploy enviar-notificacion --no-verify-jwt
//
// Secretos:
// FIREBASE_SERVICE_ACCOUNT JSON de la cuenta de servicio (el mismo de notificar-bienvenida)
// FCM_WEBHOOK_SECRET texto que debe coincidir con el header x-webhook-secret del trigger
// SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY los inyecta Supabase.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// FCM (API HTTP v1)
interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

interface ResultadoEnvio {
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

// El token de Google dura 1 hora; se reutiliza mientras la instancia siga viva
let tokenEnCache: { valor: string; expira: number } | null = null;

async function obtenerAccessToken(cuenta: ServiceAccount): Promise<string> {
  const ahora = Math.floor(Date.now() / 1000);
  if (tokenEnCache && tokenEnCache.expira - 60 > ahora) return tokenEnCache.valor;

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
  const { access_token, expires_in } = await respuesta.json();
  tokenEnCache = { valor: access_token, expira: ahora + (expires_in ?? 3600) };
  return access_token;
}

/** Mensaje solo de datos: la app siempre ejecuta onMessageReceived y arma la notificación. */
async function enviarMensajeData(
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
        message: { token: tokenDispositivo, data, android: { priority: "HIGH" } },
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

// Función
interface FilaNotificacion {
  id: number;
  id_usuario: string;
  tipo: string;
  titulo: string;
  mensaje: string;
}

const responder = (cuerpo: unknown, status = 200) =>
  new Response(JSON.stringify(cuerpo), {
    status,
    headers: { "Content-Type": "application/json" },
  });

Deno.serve(async (req) => {
  if (req.method !== "POST") return responder({ error: "Método no permitido" }, 405);

  // 1. Solo el trigger de la base de datos puede llamarla
  const secreto = Deno.env.get("FCM_WEBHOOK_SECRET");
  if (!secreto || req.headers.get("x-webhook-secret") !== secreto) {
    return responder({ error: "No autorizado" }, 401);
  }

  let evento: { type?: string; record?: FilaNotificacion };
  try {
    evento = await req.json();
  } catch {
    return responder({ error: "JSON inválido" }, 400);
  }

  const n = evento.record;
  if (evento.type !== "INSERT" || !n?.id_usuario) return responder({ ignorado: true });

  // 2. Token del dispositivo del usuario
  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
  const { data: usuario } = await admin
    .from("Usuario")
    .select("fcm_token")
    .eq("id", n.id_usuario)
    .single();

  // Sin token (sesión cerrada o permiso nunca concedido) la fila queda como historial
  if (!usuario?.fcm_token) return responder({ enviada: false, motivo: "sin_token" });

  // 3. Cuenta de servicio de Firebase
  let cuenta: ServiceAccount;
  try {
    cuenta = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "");
  } catch {
    console.error("FIREBASE_SERVICE_ACCOUNT no está configurado o no es un JSON válido");
    return responder({ error: "Servidor sin configurar" }, 500);
  }

  // 4. Enviar
  let resultado: ResultadoEnvio;
  try {
    resultado = await enviarMensajeData(cuenta, usuario.fcm_token, {
      tipo: n.tipo,
      titulo: n.titulo,
      mensaje: n.mensaje,
      id_notificacion: String(n.id),
    });
  } catch (e) {
    console.error("Error al enviar a FCM:", e);
    return responder({ enviada: false, motivo: "fcm_error" }, 502);
  }

  if (resultado.tokenInvalido) {
    await admin.from("Usuario").update({ fcm_token: null }).eq("id", n.id_usuario);
    return responder({ enviada: false, motivo: "token_invalido" });
  }
  if (!resultado.ok) return responder({ enviada: false, motivo: "fcm_error" }, 502);

  await admin.from("Notificacion").update({ push_enviada: true }).eq("id", n.id);
  return responder({ enviada: true });
});
