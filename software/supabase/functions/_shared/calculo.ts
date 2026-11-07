// Fórmula de cobro de SmartParking (issue 12.1).
// Única fuente de verdad: la app solo muestra lo que calcula el servidor.

export type TipoPago = "reserva" | "extension";

export const COMISION: Record<TipoPago, number> = {
  reserva: 0.04,   // 4 %
  extension: 0.05, // 5 %
};

// Cupones válidos (solo aplican a reservas)
export const CUPONES: Record<string, number> = {
  PROMO10: 0.10,
};

export interface Desglose {
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

export function calcularDesglose(
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
