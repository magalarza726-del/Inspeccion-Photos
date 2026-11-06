# Notas de reconstrucción

La base procede de la reconstrucción funcional del APK de InspeccionPhotos y las capturas de referencia. Las fases posteriores evolucionan esa base; no reemplazan el proyecto desde cero.

## Fase 1

Captura fiable, GPS, orientación, trayectoria, metadata, persistencia local y recuperación después de crash.

## Fase 2

SHA-256, previews, cola offline individual, Supabase privado y verificación remota.

## Fase 3

`route.geojson`, mapa offline, GIS, inferencia probabilística de edificios y cobertura.

## Fase 4 / v2.3

Se añadieron:

- `interior/` — dead reckoning, anclas e incertidumbre;
- `arcore/` — VIO opcional;
- `scan/` — políticas de Ráfaga y selección de frames;
- CameraX integrado sólo para Ráfaga/Vídeo;
- WorkManager para extracción de frames;
- persistencia de vídeo + sidecar de pose;
- sincronización de metadata Fase 4;
- `schemaVersion 1.3`.

La fotografía normal sigue usando el flujo nativo anterior, porque su simplicidad es una ventaja para la ruta crítica.

La prioridad sigue siendo original + metadata local antes que hash, preview, análisis o red.

`Exportar ZIP` permanece sólo como backup manual opcional.


## Corrección de semántica posicional 1.3.1

- `GNSS_ABSOLUTE`: `horizontalAccuracyMeters` representa precisión GNSS y puede producir `GpsQuality`.
- `INTERIOR_RELATIVE`: la coordenada es una estimación relativa anclada al último GNSS exterior; `horizontalAccuracyMeters` representa **incertidumbre de pose relativa**, nunca precisión GPS.
- Las fotos interiores ya no pueden aparecer como `GPS EXCELLENT` sólo porque ARCore/IMU tenga baja incertidumbre local.
- GeoJSON exporta `positionReference`, `gnssHorizontalAccuracyMeters`, `relativePoseUncertaintyMeters` y `horizontalAccuracyMeaning`.
- Datos antiguos se migran al leerlos según el `provider` (`arcore_vio_relative`, `imu_dead_reckoning_relative`, etc.).
