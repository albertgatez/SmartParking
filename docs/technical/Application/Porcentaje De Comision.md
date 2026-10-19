# Cálculo del porcentaje de comisión

SmartParking cobra una comisión por el servicio de reservar con anticipación. RF-06 establece que debe estar **entre 2 % y 5 %**.

## 1. Porcentajes definidos

| Tipo de pago | Comisión | Cuándo aplica |
|---|---|---|
| **Reserva** | **4 %** | Al pagar una reserva nueva en el Carrito |
| **Extensión de tiempo** | **5 %** | Al agregar horas a una reserva en uso, desde Inicio |

## 2. Justificación

- **Dentro del rango de RF-06** (2 % a 5 %).
- **El cuestionario la respalda:** el 94.1 % aceptaría pagar una comisión, pero el 70.6 % la condiciona a que sea baja o dependa de la situación (issue 1.2). Con 4 %, en una reserva de 2 horas a $20/h la comisión es de $1.60.
- **Reserva al 4 %:** es el uso principal de la app. Un porcentaje menor al máximo incentiva a reservar con anticipación, que es lo que permite planear la ocupación del estacionamiento.
- **Extensión al 5 %:** es una decisión de último momento que ocupa el cajón más tiempo del planeado y puede impedir que otra persona lo reserve. El punto extra compensa ese costo de oportunidad y sigue dentro del máximo de RF-06.
- **Sin cuota fija:** solo porcentaje, para que en reservas cortas la comisión no sea desproporcionada.

## 3. Fórmula

```
subtotal   = horas × precio_hora
comisión   = subtotal × porcentaje            (4 % reserva, 5 % extensión)
descuento  = subtotal × porcentaje_cupón      (0 si no hay cupón)
total      = subtotal + comisión − descuento
```

Todas las cantidades se redondean a 2 decimales (centavos). Stripe recibe el total en centavos (`round(total × 100)`).

Reglas:

- La comisión se calcula sobre el **subtotal sin descuento**: el cupón descuenta el precio del estacionamiento, no el servicio de SmartParking.
- Los cupones solo aplican a reservas, no a extensiones.
- Cupón vigente: `PROMO10`, 10 % de descuento.

## 4. Ejemplos

Estacionamiento con `precio_hora` = $20.00:

| Caso | Horas | Subtotal | Comisión | Descuento | **Total** |
|---|---|---|---|---|---|
| Reserva | 1 | $20.00 | $0.80 (4 %) | — | **$20.80** |
| Reserva | 3 | $60.00 | $2.40 (4 %) | — | **$62.40** |
| Reserva con `PROMO10` | 3 | $60.00 | $2.40 (4 %) | −$6.00 | **$56.40** |
| Extensión | 1 | $20.00 | $1.00 (5 %) | — | **$21.00** |
| Extensión | 2 | $40.00 | $2.00 (5 %) | — | **$42.00** |

## 5. Se muestra antes de pagar

| Pantalla | Qué se muestra |
|---|---|
| **Carrito** (reserva) | Subtotal, comisión, descuento del cupón (si aplica) y total, antes del botón "Confirmar pago" |
| **Diálogo de extensión** (Inicio) | Horas extra, subtotal, comisión y total, antes de confirmar |

## 6. Estado actual

Los porcentajes y la fórmula ya están en la app (`ShoppingCartFragment.COMISION_PORCENTAJE = 0.04` y `HomeFragment.COMISION_EXTENSION = 0.05`). En el issue 12.2 el cálculo pasa al backend para que la app solo muestre los montos.
