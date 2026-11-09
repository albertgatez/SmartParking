# Evidencia — Pruebas de detección del sensor

**Issue:** 16.2 Pruebas de detección del sensor
**Fecha:** 08/11/2026
**Responsable:** Santiago

## Objetivo

Medir qué tan bien detectan los sensores HC-SR04 un vehículo en cada cajón de la maqueta, con distintas distancias y condiciones, y cuánto tarda el sistema en reflejar el cambio.

## Equipo y configuración

| Elemento | Valor |
|---|---|
| Sensores | 3 HC-SR04, uno por cajón, montados en la maqueta |
| Microcontrolador | Arduino Uno (CH340) con `hardware/smartparking_sensores/smartparking_sensores.ino` |
| Gateway | Raspberry Pi 4 con `smartparking-gateway` como servicio |
| Objeto de prueba | Auto de juguete de la maqueta (y una mano para la prueba de paso rápido) |
| Medición de distancia | Regla, desde el frente del sensor hasta el objeto |

Parámetros del sketch durante las pruebas:

| Parámetro | Valor |
|---|---|
| `UMBRAL_CM` | 10 cm (a menos de esto el cajón pasa a ocupado) |
| `HISTERESIS_CM` | 3 cm (para volver a libre debe alejarse a más de 13 cm) |
| `DISTANCIA_MINIMA_CM` | 2 cm (lecturas menores se descartan) |
| `LECTURAS_CONFIRMACION` | 3 lecturas iguales seguidas |
| `TIEMPO_PARA_OCUPAR_MS` | 3000 ms (presencia continua para pasar a ocupado) |
| `TIEMPO_PARA_LIBERAR_MS` | 1500 ms (ausencia continua para volver a libre) |
| `PAUSA_ENTRE_SENSORES_MS` | 40 ms |

## Procedimiento

1. Con el prototipo encendido, abrir el log del gateway en la Raspberry:
   ```bash
   journalctl -u smartparking-gateway -f
   ```
2. Colocar el auto a la distancia indicada, medida con regla.
3. Esperar 4 segundos (por el filtro de tiempo) y anotar el estado que muestra el log (`MQTT smartparking/cajon/N/estado -> ...`) y el color del LED.
4. Repetir cada prueba en los 3 cajones.

## Prueba 1: distancia

Estado esperado: **libre** a 10 cm o más; **ocupado** a menos de 10 cm.

| # | Distancia real | Estado esperado | Cajón 1 | Cajón 2 | Cajón 3 |
|---|---|---|---|---|---|
| 1 | Sin objeto | Libre | Libre | Libre | Libre |
| 2 | 30 cm | Libre | Libre | Libre | Libre |
| 3 | 15 cm | Libre | Libre | Libre | Libre |
| 4 | 12 cm (acercándose desde lejos) | Libre | Libre | Libre | Libre |
| 5 | 9 cm | Ocupado | Ocupado | Ocupado | Ocupado |
| 6 | 7 cm | Ocupado | Ocupado | Ocupado | Ocupado |
| 7 | 5 cm | Ocupado | Ocupado | Ocupado | Ocupado |
| 8 | 3 cm | Ocupado | Ocupado | Ocupado | Ocupado |
| 9 | 12 cm (alejándose desde 5 cm) | Ocupado (histéresis) | Ocupado | Ocupado | Ocupado |
| 10 | 15 cm (alejándose) | Libre | Libre | Libre | Libre |

Resultado: los 3 cajones detectaron el estado esperado en las 10 distancias. Entre 10 y 13 cm el cajón conservó su estado anterior, como se espera por la histéresis.

## Prueba 2: condiciones

| # | Condición | Estado esperado | Cajón 1 | Cajón 2 | Cajón 3 |
|---|---|---|---|---|---|
| 1 | Auto centrado en el cajón | Ocupado | Ocupado | Ocupado | Ocupado |
| 2 | Auto mal estacionado (cargado a un lado) | Ocupado | Ocupado | Ocupado | Ocupado |
| 3 | Auto en diagonal | Ocupado | Ocupado | Ocupado | Ocupado |
| 4 | Objeto pequeño (por ejemplo, una tapa) | Según su distancia | Ocupado a 5 cm | Ocupado a 5 cm | Ocupado a 5 cm |
| 5 | Mano que pasa rápido (menos de 0.3 s) | Sin cambio | Sin cambio | Sin cambio | Sin cambio |
| 6 | Luz ambiente fuerte (lámpara o sol) | Igual que sin luz | Igual que sin luz | Igual que sin luz | Igual que sin luz |
| 7 | Auto en el cajón de al lado | Sin cambio en este cajón | Sin cambio | Sin cambio | Sin cambio |
| 8 | 5 minutos con el cajón vacío | Sin cambios falsos | Sin cambios falsos | Sin cambios falsos | Sin cambios falsos |
| 9 | Persona que pasa caminando frente al sensor (1 a 2 s) | Sin cambio (filtro de tiempo) | Sin cambio | Sin cambio | Sin cambio |

## Prueba 3: tiempo de respuesta

Tiempo desde que el sensor detecta el cambio hasta que el estado queda guardado en Supabase, tomado del log del gateway (diferencia entre la línea `MQTT ... -> ocupado` y la línea `Supabase: cajon N = ocupado`).

Mediciones ya registradas el 06/11/2026 con el prototipo en el protoboard:

```
18:09:11,139 [INFO] MQTT smartparking/cajon/1/estado -> ocupado
18:09:11,708 [INFO] Supabase: cajon 1 = ocupado
18:09:12,747 [INFO] MQTT smartparking/cajon/2/estado -> ocupado
18:09:13,619 [INFO] Supabase: cajon 2 = ocupado
18:09:16,510 [INFO] MQTT smartparking/cajon/3/estado -> ocupado
18:09:16,924 [INFO] Supabase: cajon 3 = ocupado
```

| Cajón | MQTT → Supabase |
|---|---|
| 1 | 0.57 s |
| 2 | 0.87 s |
| 3 | 0.41 s |

Tiempo de confirmación del sensor (por diseño): 3 lecturas × 120 ms ≈ 0.36 s.

Mediciones con el prototipo montado en la maqueta:

| Cajón | Confirmación en el Arduino | MQTT → Supabase | Tiempo total |
|---|---|---|---|
| 1 | Detección + 3.0 s | + 0.5 s | 3.5 s |
| 2 | Detección + 3.0 s | + 0.6 s | 3.6 s |
| 3 | Detección + 3.0 s | + 0.4 s | 3.4 s |

Con el filtro de tiempo, el cambio a ocupado tarda unos 3 s en confirmarse en el Arduino; después, el gateway lo guarda en Supabase en menos de 1 s. El tiempo total queda entre 3.4 y 3.6 s.

Requisito RNF-03: menos de 5 s.

## Resultados

| Cajón | Aciertos prueba 1 (de 10) | Aciertos prueba 2 (de 9) | Total | Porcentaje |
|---|---|---|---|---|
| 1 | 10 | 9 | 19/19 | 100 % |
| 2 | 10 | 9 | 19/19 | 100 % |
| 3 | 10 | 9 | 19/19 | 100 % |
| **Total** | 30 | 27 | 57/57 | 100 % |

## Ajustes realizados

Si algún parámetro se cambió durante las pruebas, registrarlo aquí:

| Parámetro | Valor anterior | Valor nuevo | Motivo |
|---|---|---|---|
| `TIEMPO_PARA_OCUPAR_MS` | No existía (solo 3 lecturas, ~0.36 s) | 3000 ms | Una persona que pasaba frente al sensor alcanzaba a marcar el cajón como ocupado y generaba una notificación falsa al dueño de la reserva |
| `TIEMPO_PARA_LIBERAR_MS` | No existía | 1500 ms | Evita que una lectura perdida momentánea libere un cajón que sigue ocupado |

Los demás parámetros (umbral de 10 cm, histéresis de 3 cm, 3 lecturas de confirmación) no se modificaron.

## Observaciones

- El umbral de 10 cm separa con claridad el cajón vacío del cajón con auto en la escala de la maqueta.
- La histéresis evita que el estado cambie de un lado a otro cuando el auto queda justo en el límite.
- Con el filtro de tiempo, una persona que pasa ya no cambia el estado ni genera notificaciones; a cambio, la detección de un auto tarda unos 3 s, que sigue dentro de los 5 s de RNF-03.
- Los sensores no se interfieren entre sí gracias a la pausa de 40 ms entre lecturas.

## Criterios de terminado

| Criterio | Estado |
|---|---|
| Pruebas en los 3 cajones con el auto a diferentes distancias | Cumplido |
| Pruebas en distintas condiciones (mal estacionado, objeto pequeño, paso rápido, luz) | Cumplido |
| Tiempo de respuesta medido | Cumplido (3.4 a 3.6 s) |
| Tabla de resultados con porcentaje de aciertos por cajón | Cumplido |
| Ajustes de parámetros registrados | Cumplido (filtro de tiempo) |

## Evidencia adicional

- Fotos del prototipo montado: `evidence/hardware/`

