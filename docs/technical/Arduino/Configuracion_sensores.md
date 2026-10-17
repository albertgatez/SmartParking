# Configuración del comportamiento de los sensores

Cómo decide el Arduino si un cajón está **libre** u **ocupado** con los sensores HC-SR04. Todos los parámetros están al inicio de `smartparking_sensores.ino`.

## Parámetros

| Parámetro | Valor | Qué controla |
|---|---|---|
| `UMBRAL_CM` | 10 cm | A menos de esta distancia el cajón pasa a **ocupado**. |
| `HISTERESIS_CM` | 3 cm | Para volver a **libre**, el objeto debe alejarse a más de 13 cm (10 + 3). |
| `DISTANCIA_MINIMA_CM` | 2 cm | Las lecturas de menos de 2 cm se descartan como ruido. |
| `DISTANCIA_MAXIMA_CM` | 100 cm | Alcance máximo; más lejos o sin eco = no hay nada. |
| `TIMEOUT_ECO_US` | ≈ 5.8 ms | Tiempo máximo de espera del eco, calculado para 100 cm. |
| `LECTURAS_CONFIRMACION` | 3 | Lecturas iguales seguidas para aceptar un cambio de estado. |
| `PAUSA_ENTRE_SENSORES_MS` | 40 ms | Pausa entre la lectura de un sensor y el siguiente. |
| `intervaloEnvioMs` | 2000 ms | Cada cuánto se reenvía el estado de todos los cajones (1–5 s). |

## Justificación

### Umbral de 10 cm

El prototipo es una **maqueta a escala**: el sensor está en el fondo del cajón, apuntando hacia donde llega el auto de juguete. Con el cajón vacío, el sensor mide la pared de enfrente o no recibe eco. Con un auto, la lectura baja a pocos centímetros. 10 cm separa con claridad los dos casos en el tamaño del cajón de la maqueta.

En un estacionamiento real se ajustaría solo esta constante (por ejemplo, unos 150 cm con el sensor en el techo), sin cambiar el resto del código.

### Histéresis de 3 cm

Si se usara un solo límite, un objeto justo en los 10 cm haría que el estado cambiara de libre a ocupado una y otra vez por pequeñas variaciones de la lectura. Con la histéresis:

- **Libre → ocupado:** distancia < 10 cm.
- **Ocupado → libre:** distancia > 13 cm.
- Entre 10 y 13 cm el cajón **conserva** su estado anterior.

### Sin eco = libre

Cuando no hay nada frente al sensor, el pulso no regresa y `pulseIn` devuelve 0. Eso se interpreta como cajón libre, no como error. Así el LED de libre siempre queda encendido con el cajón vacío.

### Filtro contra lecturas falsas

El HC-SR04 a veces da lecturas sueltas incorrectas (rebotes, ángulos, un 1 cm falso). Se usan dos filtros:

1. **Lecturas descartadas:** las menores a 2 cm no cuentan ni a favor ni en contra.
2. **3 lecturas consecutivas:** el estado solo cambia si 3 lecturas seguidas indican lo mismo. Si una lectura contradice, el contador se reinicia.

Cada sensor se lee cada 120 ms (3 sensores × 40 ms), así que un cambio real se confirma en unos **0.36 s**, lo bastante rápido para el usuario y suficiente para ignorar picos de ruido.

### Pausa entre sensores

Los sensores se leen uno a la vez con 40 ms de separación para que el eco de un sensor no lo capte el de al lado. El programa no usa `delay()`, así que sigue atendiendo comandos y LEDs mientras espera.

## Frecuencia de envío configurable.

El Arduino envía `Sensor <n>: <estado>`:

- En cuanto un cajón **cambia** de estado.
- De forma **periódica** para todos los cajones, cada `intervaloEnvioMs`.

El intervalo se cambia en ejecución, sin volver a cargar el sketch, con el comando serial `F<n>`, donde `n` va de 1 a 5 segundos:

| Comando | Intervalo |
|---|---|
| `F1` | 1 s |
| `F2` | 2 s (valor inicial) |
| `F5` | 5 s |

Valores fuera de 1–5 se ignoran.

## Tabla de pruebas: distancia real vs. estado detectado

Procedimiento: colocar un objeto frente al sensor 1 a la distancia indicada (medida con regla), esperar 1 s y anotar lo que muestra el Monitor Serie y el LED.

| # | Distancia real | Lectura aprox. del sensor | Estado esperado | Estado detectado | LED | ¿Correcto? |
|---|---|---|---|---|---|---|
| 1 | Sin objeto | Sin eco | 0 – libre | 0 – libre | Verde | Si |
| 2 | 30 cm | 29–31 cm | 0 – libre | 0 – libre | Verde | Si |
| 3 | 15 cm | 14–16 cm | 0 – libre | 0 – libre | Verde | Si |
| 4 | 12 cm (acercando desde lejos) | 11–12 cm | 0 – libre | 0 – libre | Verde | Si |
| 5 | 9 cm | 8–9 cm | 1 – ocupado | 1 – ocupado | Rojo | Si |
| 6 | 5 cm | 5 cm | 1 – ocupado | 1 – ocupado | Rojo | Si |
| 7 | 3 cm | 3–4 cm | 1 – ocupado | 1 – ocupado | Rojo | Si |
| 8 | 12 cm (alejando desde 5 cm) | 11–12 cm | 1 – ocupado (histéresis) | 1 – ocupado | Rojo | Si |
| 9 | 15 cm (alejando) | 14–15 cm | 0 – libre | 0 – libre | Verde | Si |
| 10 | Mano que pasa rápido (< 0.3 s) | 6 cm por un instante | Sin cambio | 0 – libre (sin cambio) | Verde | Si |

**Resultado:** 10 de 10 casos correctos. El cambio de estado se observó en menos de medio segundo y no hubo cambios falsos durante 5 minutos con el cajón vacío.

### Prueba de frecuencia de envío

| Comando | Intervalo esperado | Intervalo observado | ¿Correcto? |
|---|---|---|---|
| (inicio) | 2 s | 2 s | Si |
| `F1` | 1 s | 1 s | Si |
| `F5` | 5 s | 5 s | Si |
| `F9` | Se ignora (sigue en 5 s) | 5 s | Si |