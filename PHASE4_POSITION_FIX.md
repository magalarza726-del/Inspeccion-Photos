# Corrección Fase 4 — semántica GNSS vs pose interior relativa

Versión de esquema: **1.3.1**

## Problema corregido

Una pose interior producida por ARCore/IMU podía tener una incertidumbre local pequeña (por ejemplo ±1.8 m). Como `LocationSnapshot.quality` clasificaba cualquier `horizontalAccuracyMeters`, esa incertidumbre podía aparecer erróneamente como `GPS EXCELLENT`.

## Regla nueva

- `GNSS_ABSOLUTE`: `horizontalAccuracyMeters` significa precisión GNSS y sí puede producir `GpsQuality`.
- `INTERIOR_RELATIVE`: `horizontalAccuracyMeters` significa incertidumbre de una pose relativa anclada al último GNSS exterior. **Nunca** produce `GpsQuality`.
- `UNKNOWN`: no se asume ninguna precisión GNSS.

## Cambios

- `LocationSnapshot` incorpora `PositionReference`.
- `GpsQuality` devuelve `UNAVAILABLE` para toda pose `INTERIOR_RELATIVE`.
- El seguimiento interior marca explícitamente las coordenadas aproximadas como `INTERIOR_RELATIVE`.
- Las capturas interiores usan la posición relativa efectiva cuando está disponible.
- `PoseResolver` guarda confianza `INTERIOR_RELATIVA_ALTA/MEDIA/BAJA/MUY_BAJA`.
- La migración corrige fotografías antiguas que tuvieran `GPS EXCELLENT` con provider `arcore_vio_relative` o `imu_dead_reckoning_relative`.
- La auditoría separa fotos GNSS de fotos con pose interior relativa.
- Mapa, ráfaga, vídeo y detalle de fotografías ya no llaman “GPS” a la incertidumbre interior.
- `route.geojson` exporta campos separados:
  - `positionReference`
  - `gnssHorizontalAccuracyMeters`
  - `relativePoseUncertaintyMeters`
  - `horizontalAccuracyMeaning`
- La identificación GIS permite pose interior relativa, pero limita su peso porque consistencia local no equivale a precisión geográfica absoluta.

## Validación realizada

Se compiló aisladamente el núcleo Kotlin de modelos + PoseResolver + InteriorDeadReckoner y se ejecutó un smoke test que verifica:

1. una pose interior relativa de ±1.8 m produce `GpsQuality.UNAVAILABLE`;
2. una medición GNSS absoluta de ±1.8 m continúa produciendo `GpsQuality.EXCELLENT`;
3. PoseResolver etiqueta la primera como `INTERIOR_RELATIVA_ALTA`.

El build Android completo debe ejecutarse en Android Studio porque este entorno no puede descargar la distribución Gradle 9.3.1.
