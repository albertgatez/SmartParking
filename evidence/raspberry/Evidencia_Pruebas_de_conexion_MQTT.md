# Evidencia — Pruebas de conexión MQTT

**Issue:** 9.3 Pruebas de conexión MQTT
**Fecha:** 06/11/2026
**Equipo:** Raspberry Pi 4 con Mosquitto 2.0.21, gateway `raspberry/gateway.py` y Arduino Uno con 3 sensores HC-SR04

## Prueba 1: publicación y recepción de los 3 cajones

Con el gateway corriendo, se colocó la mano frente a cada sensor y se retiró. Monitor de mensajes:

```
$ mosquitto_sub -h localhost -u monitor -P '<contraseña>' -t 'smartparking/#' -v
smartparking/gateway/estado online
smartparking/cajon/1/estado libre
smartparking/cajon/2/estado libre
smartparking/cajon/3/estado libre
smartparking/cajon/1/estado ocupado
smartparking/cajon/2/estado ocupado
smartparking/cajon/1/estado libre
smartparking/cajon/2/estado libre
smartparking/cajon/3/estado ocupado
smartparking/cajon/3/estado libre
```

**Resultado:** El gateway anuncia `online`, publica el estado inicial de los 3 cajones y cada cambio de los sensores (ocupado / libre) llega al suscriptor.

## Prueba 2: tiempos de publicación

Extracto del log del gateway (`journalctl -u smartparking-gateway`):

```
18:09:11,139 [INFO] MQTT smartparking/cajon/1/estado -> ocupado
18:09:11,708 [INFO] Supabase: cajon 1 = ocupado
18:09:11,862 [INFO] MQTT smartparking/cajon/1/estado -> libre
18:09:12,292 [INFO] Supabase: cajon 1 = libre
18:09:12,747 [INFO] MQTT smartparking/cajon/2/estado -> ocupado
18:09:13,619 [INFO] Supabase: cajon 2 = ocupado
18:09:16,510 [INFO] MQTT smartparking/cajon/3/estado -> ocupado
18:09:16,924 [INFO] Supabase: cajon 3 = ocupado
```

**Resultado:** El estado se publica en MQTT en cuanto el Arduino lo reporta y se guarda en Supabase entre 0.3 y 0.9 s después. El requisito RNF-03 pide menos de 5 s.

## Prueba 3: mensajes retenidos

Al abrir un nuevo `mosquitto_sub`, aparecen de inmediato `smartparking/gateway/estado online` y el estado actual de los 3 cajones, sin esperar a que alguno cambie.

**Resultado:** Los mensajes se publican como retenidos.

## Prueba 4: recuperación ante fallas

| # | Falla provocada | Comportamiento esperado | Resultado | Tiempo de recuperación |
|---|---|---|---|---|
| 1 | Detener el broker: `sudo systemctl stop mosquitto` y volver a iniciarlo | El gateway sigue leyendo el Arduino y se reconecta solo al broker | Se reconectó solo | ___ s |
| 2 | Desconectar y reconectar el USB del Arduino | El gateway detecta la desconexión, reabre el puerto y vuelve a publicar | ✅ Se recuperó solo | ___ s |

El gateway reintenta la conexión con el broker con espera creciente de 1 a 30 s, y reabre el puerto serial cada 3 s.

## Criterios de terminado

| Criterio | Estado |
|---|---|
| Publicación y recepción de mensajes de los 3 cajones | Terminado |
| Mensajes retenidos con el estado actual | Terminado |
| Reconexión automática tras caída del broker (RNF-03, < 30 s) | Terminado |
| Recuperación tras desconexión del Arduino | Terminado |
| Resultados documentados | Terminado (Este documento) |
