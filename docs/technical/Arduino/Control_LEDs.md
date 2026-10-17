# Control de LEDs

Cada cajón tiene tres LEDs que muestran su estado. Solo hay un LED encendido a la vez por cajón.

## Conexiones

| Cajón | LED libre | LED ocupado | LED reservado |
|---|---|---|---|
| 1 (discapacidad) | D8 — **azul** | D11 — rojo | A0 — amarillo |
| 2 | D9 — verde | D12 — rojo | A1 — amarillo |
| 3 | D10 — verde | D13 — rojo | A2 — amarillo |

Cada LED lleva una resistencia de 220 Ω. El cajón 1 está marcado como de discapacidad en la base de datos (`Cajon.discapacitado = true`), así que en su pin de libre se pone un LED azul. El código es el mismo para los tres cajones; solo cambia el color físico del LED.

## Significado de cada color

| Color | Estado | Código | Cuándo se enciende |
|---|---|---|---|
| Verde | Libre | `0` | No hay auto y no hay reserva activa. |
| Azul | Libre (discapacidad) | `0` | Igual que el verde, en el cajón 1. |
| Amarillo | Reservado | `2` | Hay una reserva confirmada en curso y el cajón está vacío. |
| Rojo fijo | Ocupado | `1` | Hay un auto. Si el cajón estaba reservado, quien reservó ya escaneó su QR. |
| Rojo parpadeando | Ocupado sin confirmar | `1` | El cajón está reservado y hay un auto, pero no se ha escaneado el QR de la reserva: posiblemente alguien que no reservó ocupó el lugar. |

## Prioridad de los estados

El Arduino decide el estado en este orden:

1. Si el sensor detecta un auto → **ocupado** (rojo).
2. Si no, y hay una reserva activa → **reservado** (amarillo).
3. Si no → **libre** (verde/azul).

El sensor tiene prioridad sobre la reserva: si alguien se estaciona en un cajón reservado, el cajón se reporta como ocupado y el rojo parpadea. Cuando el auto se va y la reserva sigue vigente, vuelve a amarillo.

## Activación por orden externa

El estado **reservado** no lo detecta el sensor: lo activa una orden por serial que envía el gateway de la Raspberry Pi. El gateway consulta cada 5 s la tabla `Reserva` en Supabase y manda:

| Comando | Cuándo lo envía el gateway | Efecto en los LEDs |
|---|---|---|
| `R<n>` | Empieza una reserva confirmada del cajón n | Libre → amarillo |
| `C<n>` | Quien reservó escaneó su QR (`token_estacionamiento`) | Rojo parpadeando → rojo fijo |
| `L<n>` | Termina la reserva | Amarillo → libre |

Estos comandos también se pueden escribir a mano en el Monitor Serie de Arduino IDE (9600 baudios) para probar.


## Pruebas que se comprobaron en el video

Con el Monitor Serie abierto y el gateway detenido:

| # | Acción | Resultado esperado |
|---|---|---|
| 1 | Encender el Arduino | Los 3 cajones en libre (cajón 1 azul, 2 y 3 verde). |
| 2 | Mano frente al sensor 2 | Cajón 2 en rojo fijo. |
| 3 | Retirar la mano | Cajón 2 vuelve a verde. |
| 4 | Escribir `R1` | Cajón 1 en amarillo. |
| 5 | Mano frente al sensor 1 | Cajón 1 en rojo parpadeando. |
| 6 | Escribir `C1` | Cajón 1 en rojo fijo. |
| 7 | Retirar la mano | Cajón 1 vuelve a amarillo. |
| 8 | Escribir `L1` | Cajón 1 vuelve a azul. |
