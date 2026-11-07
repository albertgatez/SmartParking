# Evidencia — Sincronización con el backend

**Issue:** 15.2 Sincronización con el backend
**Fecha:** 06/11/2026
**Componentes:** Arduino → gateway en Raspberry Pi 4 → Supabase (tabla `Cajon`) y Supabase (tabla `Reserva`) → gateway → LEDs del Arduino

## 1. Sensores → Supabase

Cada cambio detectado por un sensor se guarda en `Cajon.estado_cajon` (`0` libre, `1` ocupado, `2` reservado). Extracto del log del gateway:

```
18:09:11,139 [INFO] MQTT smartparking/cajon/1/estado -> ocupado
18:09:11,708 [INFO] Supabase: cajon 1 = ocupado
18:09:12,747 [INFO] MQTT smartparking/cajon/2/estado -> ocupado
18:09:13,619 [INFO] Supabase: cajon 2 = ocupado
18:09:16,510 [INFO] MQTT smartparking/cajon/3/estado -> ocupado
18:09:16,924 [INFO] Supabase: cajon 3 = ocupado
18:09:17,327 [INFO] MQTT smartparking/cajon/3/estado -> libre
18:09:17,604 [INFO] Supabase: cajon 3 = libre
```

**Resultado:** El estado queda guardado en Supabase entre 0.3 y 0.9 s después de que el sensor lo detecta. El requisito RNF-03 pide menos de 5 s.

## 2. Reservas → LEDs del Arduino

El gateway consulta cada 5 s las reservas confirmadas en curso y manda al Arduino:

| Comando | Cuándo | Efecto en el cajón |
|---|---|---|
| `R<n>` | Empieza una reserva confirmada | LED amarillo |
| `C<n>` | La persona que reservó escaneó su QR | Si hay auto, el rojo queda fijo en lugar de parpadear |
| `L<n>` | Termina la reserva | Vuelve a verde |

### Prueba con reservas reales de la app

| Reserva | Cajón | Horario | Observado | Resultado |
|---|---|---|---|---|
| 187 | 3 | 16:00 – 18:00 | Al arrancar el gateway, el cajón 3 se encendió en amarillo; a las 18:00 regresó a libre | Temrinado |
| 188 | 3 | 18:00 – 19:00 | Se reinició la Raspberry con la reserva activa y el cajón 3 recuperó su estado de reservado | Terminado |

### Prueba del flujo completo desde la app

| # | Paso | Resultado esperado | Resultado |
|---|---|---|---|
| 1 | Crear y pagar una reserva en la app (tarjeta de prueba de Stripe) | En menos de 5 s el LED del cajón cambia a amarillo (`Arduino <- R<n>`) | ✅ |
| 2 | Poner un objeto frente al sensor del cajón reservado sin escanear el QR | LED rojo parpadeando | Terminado |
| 3 | Escanear el QR en la app | En menos de 5 s el rojo queda fijo (`Arduino <- C<n>`) | Terminado |
| 4 | Retirar el objeto con la reserva vigente | Vuelve a amarillo | Terminado |
| 5 | Termina la hora de la reserva | El cajón vuelve a verde (`Arduino <- L<n>`) | Terminado |

## 3. Continuidad

- Al reiniciar la Raspberry, el gateway arranca solo y vuelve a consultar las reservas vigentes.
- Si se pierde Internet, el gateway sigue leyendo el Arduino y guarda en Supabase los estados pendientes al recuperar la conexión (reintento cada 5 s).

## Criterios de terminado

| Criterio | Estado |
|---|---|
| Cada cambio de un sensor se refleja en `Cajon.estado_cajon` en menos de 5 s | Terminado (0.3 – 0.9 s) |
| Una reserva confirmada enciende el LED amarillo en menos de 5 s | Terminado |
| Al escanear el QR el rojo deja de parpadear; al terminar la reserva vuelve a libre | Terminado |
| Si se pierde Internet, se guardan los estados pendientes al reconectar | Implementado en el gateway |
| El gateway usa la llave `service_role` en su `.env` | Pendiente; se cambia al aplicar las políticas RLS |
| Resultados documentados | Terminado (Este documento) |
