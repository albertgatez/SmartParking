# Código del Arduino — SmartParking

Programa del Arduino que lee los sensores ultrasónicos HC-SR04 de cada cajón, enciende sus LEDs y envía el estado a la Raspberry Pi por puerto serial.

| | |
|---|---|
| **Placa** | Arduino Uno (o compatible con CH340) |
| **Sketch** | `smartparking_sensores/smartparking_sensores.ino` |
| **Librerías** | Ninguna (solo las del núcleo de Arduino) |
| **Baud rate** | 9600 |
| **Cajones** | 3 |

## Materiales

- 1 Arduino Uno y su cable USB
- 3 sensores ultrasónicos HC-SR04
- 9 LEDs: 3 verdes (libre), 3 rojos (ocupado) y 3 amarillos (reservado). En el cajón para personas con discapacidad se pone un LED azul en lugar del verde.
- 9 resistencias de 220 Ω (una por LED / De preferencia)
- Protoboard y cables

## Conexiones

| Cajón | TRIG | ECHO | LED libre | LED ocupado | LED reservado |
|---|---|---|---|---|---|
| 1 | D2 | D3 | D8 | D11 | A0 |
| 2 | D4 | D5 | D9 | D12 | A1 |
| 3 | D6 | D7 | D10 | D13 | A2 |

- **Sensores:** VCC a 5V y GND a GND del Arduino.
- **LEDs:** pin del Arduino → resistencia de 220 Ω → pata larga (+) del LED; la pata corta (−) a GND.
- Los pines A0–A2 se usan como salidas digitales.

## Cómo cargarlo

1. Abrir `smartparking_sensores.ino` en Arduino IDE.
2. En **Herramientas**, elegir la placa **Arduino Uno** y su puerto (en Windows `COMx`, en Linux `/dev/ttyUSB0` o `/dev/ttyACM0`).
3. Presionar **Subir**. Si sale *programmer is not responding*, cerrar el Monitor Serie o cualquier programa que esté usando el puerto (por ejemplo, el gateway).

## Funcionamiento

Los sensores se leen uno a la vez, cada 40 ms, sin usar `delay()`. Cada cajón tiene uno de tres estados:

| Código | Estado | LED |
|---|---|---|
| `0` | Libre | Verde (azul en el cajón para discapacidad) |
| `1` | Ocupado | Rojo fijo. **Parpadea** si el cajón está reservado y quien se estacionó no ha escaneado el QR de la reserva. |
| `2` | Reservado | Amarillo |

Un cajón pasa a **ocupado** cuando el sensor detecta un objeto a menos de 10 cm durante 3 lecturas seguidas. Si no hay eco (no hay nada enfrente), se considera **libre**.

### Parámetros principales

Se ajustan al inicio del sketch:

| Parámetro | Valor | Significado |
|---|---|---|
| `UMBRAL_CM` | 10 | Distancia a la que el cajón se considera ocupado |
| `HISTERESIS_CM` | 3 | Debe alejarse a más de 13 cm para volver a libre |
| `DISTANCIA_MINIMA_CM` | 2 | Lecturas menores se descartan como ruido |
| `DISTANCIA_MAXIMA_CM` | 100 | Alcance máximo de lectura |
| `LECTURAS_CONFIRMACION` | 3 | Lecturas iguales seguidas para cambiar de estado |
| `PAUSA_ENTRE_SENSORES_MS` | 40 | Pausa entre sensores para que no se interfieran |
| `intervaloEnvioMs` | 2000 | Cada cuánto se reenvía el estado de todos los cajones |

## Protocolo serial (9600 baudios)

### Mensajes que envía el Arduino

```
Sensor <n>: <estado>
```

Ejemplo: `Sensor 1: 0`, `Sensor 2: 1`, `Sensor 3: 2`.

Se envía al encender, cada vez que un cajón cambia de estado y cada 2 s para todos los cajones.

### Comandos que recibe

Los envía el gateway de la Raspberry Pi; también se pueden escribir en el Monitor Serie (mayúsculas o minúsculas).

| Comando | Efecto | Ejemplo |
|---|---|---|
| `R<n>` | Activa la reserva del cajón n (LED amarillo) | `R1` |
| `C<n>` | Quien reservó ya escaneó su QR (el rojo queda fijo en lugar de parpadear) | `C1` |
| `L<n>` | Termina la reserva del cajón n | `L1` |
| `F<n>` | Cambia el intervalo de envío a n segundos (1–5) | `F3` |

## Prueba rápida con el Monitor Serie

1. Abrir **Herramientas → Monitor Serie** a **9600 baudios**.
2. Cada 2 s deben aparecer `Sensor 1: 0`, `Sensor 2: 0` y `Sensor 3: 0`.
3. Acercar la mano a un sensor → `Sensor N: 1` y LED rojo.
4. Escribir `R1` → `Sensor 1: 2` y LED amarillo.
5. Con `R1` activo, acercar la mano al sensor 1 → el rojo parpadea; escribir `C1` → queda fijo.
6. Escribir `L1` y retirar la mano → `Sensor 1: 0` y LED de libre.

>NOTA: El Monitor Serie y el gateway no pueden usar el puerto al mismo tiempo.
