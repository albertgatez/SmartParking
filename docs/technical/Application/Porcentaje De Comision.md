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
- Los cupones están en `CUPONES` dentro de `crear-pago-reserva-v2/index.ts`, cada uno con su porcentaje y una descripción breve. Para agregar uno se añade ahí y se vuelve a desplegar la función.

### Cupones en el Carrito

- Mientras el usuario escribe el código (desde 3 caracteres, con 0.6 s de espera para no consultar en cada tecla), la app pide el desglose con `solo_cotizar` y ese `cupon`.
- **Si es válido**, aparece debajo del campo una tarjeta con el código, el descuento en pesos y la descripción (`desglose.cupon_descripcion`), y el texto "Toca para aplicar". Al tocarla, el cupón se aplica y el desglose se actualiza.
- **Si no es válido**, aparece "Cupón no válido" debajo del campo.
- El botón **Aplicar** sigue funcionando igual. Con un cupón aplicado, el campo queda bloqueado y el botón cambia a **Quitar**, que quita el cupón y recalcula el precio sin descuento.
- El cupón aplicado se guarda en el teléfono (`pagos/PreferenciasPago`, junto con el id de la reserva), así que se conserva al cambiar de pestaña o cerrar la app. Se borra al pagar, cuando la reserva expira o al hacer otra reserva. Si al volver el servidor dice que ya no es válido, se quita y se avisa.
- Si el servidor todavía no manda `cupon_descripcion` (versión anterior de la función), la app muestra el porcentaje calculado, por ejemplo "10 % de descuento en el precio del estacionamiento."

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

El cálculo está en el backend, en la Edge Function `crear-pago-reserva-v2` (`software/supabase/functions/crear-pago-reserva-v2/index.ts`, función `calcularDesglose`). La app ya no tiene porcentajes ni cupones: pide el desglose con `solo_cotizar: true` para mostrarlo en el Carrito y en el diálogo de extensión, y el mismo cálculo se usa al crear el pago. Un cupón solo se aplica si el servidor lo valida.
