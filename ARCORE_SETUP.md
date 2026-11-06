# ARCore — Fase 4

ARCore es opcional en InspeccionPhotos.

## Dispositivo compatible

Si Google Play Services for AR está disponible, **Seguimiento interior / ARCore** puede utilizar la pose visual-inercial de la cámara.

Si no está disponible:

- la app no bloquea la sesión;
- continúa IMU/pasos/barómetro cuando existan;
- la incertidumbre aumenta acorde al método disponible.

## Modelo espacial

ARCore entrega un sistema de coordenadas relativo, no latitud/longitud absoluta.

InspeccionPhotos:

1. conserva un ancla GNSS exterior;
2. toma la primera pose ARCore como origen local;
3. interpreta X como derecha y -Z como avance;
4. rota el desplazamiento con el heading del ancla exterior;
5. obtiene Este/Norte/Vertical aproximados;
6. suma la incertidumbre del ancla y deriva de VIO;
7. marca el proveedor como `arcore_vio_relative`.

Si falta heading de ancla, el sistema todavía conserva desplazamiento relativo pero aumenta la incertidumbre de orientación ENU.

## Cámara

ARCore y CameraX no comparten cámara simultáneamente en v2.3.

- `Seguimiento interior` → ARCore controla cámara para VIO.
- `Ráfaga espacial` → CameraX Photo.
- `Escaneo caminando` → CameraX Video.

Esto es deliberado para priorizar estabilidad.

## Privacidad

La aplicación informa en UI que el seguimiento AR utiliza Google Play Services for AR, provisto por Google LLC y sujeto a la Política de Privacidad de Google.
