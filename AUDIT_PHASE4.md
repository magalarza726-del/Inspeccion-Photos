# Auditoría técnica — Fase 4

## Objetivo

Fase 4 añade interiores avanzados, ARCore/VIO opcional, Ráfaga espacial y Escaneo caminando sin modificar la regla crítica de InspeccionPhotos: **una captura se considera segura sólo después de que original y metadata estén guardados localmente**.

## Qué se reutilizó

No se sustituyeron los sistemas de Fases 1–3:

- `SurveyRepository` y persistencia por sesión;
- cámara nativa para la fotografía normal de máxima simplicidad;
- metadata y asociación temporal con la trayectoria;
- procesamiento SHA-256 y previews;
- cola offline individual;
- Supabase/Auth/RLS;
- `route.jsonl` y `route.geojson`;
- mapa, GIS, identificación de edificios y cobertura;
- WorkManager.

Fase 4 se añadió en módulos separados: `interior/`, `arcore/`, `scan/`, `camera/`, `worker/`, `storage/` y pantallas específicas.

## Interiores

### Implementado

1. **Ancla exterior fiable**
   - conserva la última lectura GNSS con precisión ≤ 12 m;
   - al entrar en Interior crea un `InteriorAnchor` único;
   - guarda lat/lon/altitud, precisión, heading y presión barométrica disponible.

2. **Dead reckoning IMU/pasos**
   - usa detector de pasos si existe;
   - longitud de paso interna inicial 0.72 m;
   - usa heading para descomponer el paso en Este/Norte;
   - incrementa incertidumbre con cada paso;
   - si no existe heading, degrada la confianza.

3. **Barómetro**
   - diferencia de altitud relativa mediante aproximación hipsométrica;
   - no se presenta como altitud absoluta exacta.

4. **ARCore VIO opcional**
   - inicia una `Session` sólo en la pantalla dedicada;
   - obtiene pose de cámara de ARCore cuando `TrackingState.TRACKING`;
   - toma la primera pose como origen relativo;
   - rota X/-Z del mundo ARCore usando el heading del ancla exterior para obtener un ENU aproximado;
   - mantiene la precisión GNSS del ancla dentro del presupuesto de incertidumbre;
   - si no hay heading de ancla, añade penalización de incertidumbre.

5. **Fallback**
   - si ARCore no está disponible, los pasos/IMU siguen funcionando;
   - no se exige ARCore para iniciar ni completar un levantamiento.

6. **Cambio automático Exterior/Interior**
   - histéresis para evitar oscilación;
   - GPS > 25 m durante varias muestras sugiere Interior;
   - varias muestras ≤ 8 m permiten volver a Exterior;
   - si dejan de llegar actualizaciones GNSS, un watchdog de staleness también puede activar Interior.

### Límite intencional

Las coordenadas interiores son estimaciones relativas. Incluso cuando se proyectan a lat/lon para compartir una ruta, el `provider` identifica explícitamente `arcore_vio_relative` o `imu_dead_reckoning_relative` y conserva incertidumbre. ESPOL Builder u otro consumidor debe respetar esa incertidumbre.

## Ráfaga espacial

### Implementado

- CameraX `Preview + ImageCapture` solamente;
- no se enlaza VideoCapture en este modo para mejorar compatibilidad entre dispositivos;
- disparo por distancia, heading o tiempo máximo;
- intervalo mínimo entre capturas;
- soporte de distancia relativa interior si ambas poses pertenecen a la misma ancla;
- commits secuenciales: no se permite una segunda captura mientras se confirma la anterior;
- cada foto entra al mismo pipeline de seguridad que una foto normal;
- cada Ráfaga tiene ID, estado, tiempos, parámetros y lista de Photo IDs.

## Escaneo caminando / vídeo

### Implementado

- CameraX `Preview + VideoCapture` solamente;
- el `Recorder` usa su selector de calidad compatible por defecto;
- vídeo local MP4 sin audio;
- sidecar de pose aproximadamente cada 250 ms;
- cada muestra puede contener GNSS, orientación, pose interior e IMU;
- WorkManager extrae frames después de finalizar;
- selección inicial por desplazamiento/cambio angular/intervalo;
- extracción temporal con `MediaMetadataRetriever.OPTION_CLOSEST`;
- filtro de nitidez;
- hash perceptual 8×8 para reducir redundancia;
- cada frame conserva la pose interior relativa cuando existe;
- vídeo original intacto;
- recuperación de registros `RECORDING` después de cierre/crash: si existe MP4 local, pasa a procesamiento; si no, queda error explícito;
- ráfagas activas al reabrir pasan a `INTERRUPTED` sin borrar las fotografías ya seguras.

### Límite intencional de nube

El MP4 bruto **no se sincroniza todavía**. Un POST monolítico de varios GB no es aceptable para la arquitectura offline-first. Fase 4 sólo sincroniza metadata y frames derivados; el transporte bruto requiere protocolo reanudable/chunked y verificación de integridad por partes.

## Cámara compartida ARCore + CameraX

No se implementó `SharedCamera` en esta fase.

ARCore controla la cámara únicamente dentro de `InteriorTrackingScreen`. Ráfaga y Vídeo controlan la cámara mediante CameraX y siguen recibiendo IMU/trayectoria. Esta separación reduce estados de carrera, cierres de cámara y pérdidas de captura.

Una futura Fase 4.x podría evaluar SharedCamera sólo después de pruebas en varios dispositivos.

## Privacidad ARCore

La app muestra que ARCore funciona mediante Google Play Services for AR, provisto por Google LLC, en:

- Inicio;
- Configuración;
- pantalla de seguimiento interior.

Supabase sigue privado por defecto. No se crean enlaces públicos automáticamente.

## Rendimiento/batería

- sensores sólo se activan durante una sesión activa;
- los eventos de sensores pueden llegar a decenas de Hz, pero la actualización de estado UI/sidecar se limita aproximadamente a 10 Hz salvo pasos;
- ruta GNSS conserva los perfiles Alta/Balanced existentes;
- extracción de frames se ejecuta con WorkManager/IO;
- no se calculan nitidez/hash dentro del callback de cámara.

## Schema

`schemaVersion = 1.3`

Los datos anteriores 1.0/1.1/1.2 no se borran. Los módulos Fase 4 son aditivos.

## Tests añadidos

- dead reckoning de paso Este;
- transformación ARCore hacia Este con heading exterior 90°;
- política temporal/angular de Ráfaga;
- selección de muestras de vídeo;
- smoke test puro de transformación ARCore + Ráfaga;
- compilación pura de serializers Fase 4 contra `org.json` de prueba.

## Qué NO se afirma

- ARCore no produce GPS absoluto interior;
- IMU/pasos no equivalen a precisión centimétrica;
- no existe aún SLAM propio fuera de ARCore;
- no hay SharedCamera ARCore/CameraX;
- no hay subida reanudable del MP4 bruto;
- no se afirma que todos los dispositivos tengan barómetro, step detector o ARCore.

## Criterio para considerar Fase 4 aceptable

1. salir al exterior y obtener un ancla fiable;
2. entrar a interior y comprobar que aumenta una ruta relativa aun sin GPS;
3. si ARCore es compatible, comprobar que aparecen muestras `ARCORE_VIO`;
4. realizar una Ráfaga y verificar que cada foto aparece individualmente en `originals/` + `metadata/`;
5. grabar un Escaneo caminando y verificar MP4 + poses + frames representativos;
6. matar/reabrir la app y verificar que los archivos previos permanecen;
7. comprobar que una caída de red no interrumpe ningún modo de captura.


## Corrección de semántica posicional 1.3.1

- `GNSS_ABSOLUTE`: `horizontalAccuracyMeters` representa precisión GNSS y puede producir `GpsQuality`.
- `INTERIOR_RELATIVE`: la coordenada es una estimación relativa anclada al último GNSS exterior; `horizontalAccuracyMeters` representa **incertidumbre de pose relativa**, nunca precisión GPS.
- Las fotos interiores ya no pueden aparecer como `GPS EXCELLENT` sólo porque ARCore/IMU tenga baja incertidumbre local.
- GeoJSON exporta `positionReference`, `gnssHorizontalAccuracyMeters`, `relativePoseUncertaintyMeters` y `horizontalAccuracyMeaning`.
- Datos antiguos se migran al leerlos según el `provider` (`arcore_vio_relative`, `imu_dead_reckoning_relative`, etc.).
