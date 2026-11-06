# Auditoría técnica — InspeccionPhotos In Situ 2.0 / Fase 1

## Qué se reutilizó del proyecto existente

- `applicationId` y namespace `ec.espol.inspeccionphotos`.
- Android nativo + Jetpack Compose y el sistema de `@Preview`.
- `FileProvider` y captura mediante `ActivityResultContracts.TakePicture()`.
- Estrategia de almacenamiento local por archivos.
- Los módulos antiguos `Models.kt`, `InspectionRepository.kt` y `ExportUtils.kt` se conservan para no destruir compatibilidad con la reconstrucción anterior ni el respaldo `respaldo_datos.json`.
- El ZIP se conserva únicamente como backup manual; dejó de ser el transporte principal del flujo nuevo.
- Se mantiene la cadena Gradle/AGP del proyecto v3 FAST_SYNC para no repetir el problema de sincronizaciones lentas.

## Deuda técnica detectada en la versión anterior

1. `MainActivity.kt` concentraba UI, persistencia, cámara, exportación y navegación.
2. El modelo `Inspection -> CodeItem -> PhotoItem` sirve para inspecciones, pero no representa trayectoria, pose, GPS, sensores ni sesiones espaciales.
3. Una fotografía no tenía UUID, pose, precisión, estado de sincronización ni metadata independiente.
4. La persistencia central en `respaldo_datos.json` era demasiado acoplada para miles de fotos.
5. El ZIP era demasiado importante en el flujo y podía convertirse en un único punto de fallo.
6. No existía recuperación explícita de una captura o sesión interrumpida.

## Arquitectura aplicada

El código nuevo separa responsabilidades:

- `camera/`: EXIF y asociación foto-pose.
- `location/`: GNSS/GPS.
- `sensors/`: orientación del dispositivo.
- `survey/`: modelo espacial versionado.
- `storage/`: persistencia offline-first por sesión y por fotografía.
- `export/`: backup ZIP manual opcional.
- `ui/`: pantallas Compose y previews.
- `util/`: distancia y tiempo.

## Layout local por levantamiento

```text
surveys/<surveyId>/
├── session.json
├── manifest.json
├── route.jsonl
├── pois.jsonl
├── pending_capture.json        # sólo mientras una captura está pendiente
├── originals/
│   ├── FIMCP_0001.jpg
│   └── ...
├── metadata/
│   ├── 0001_<photoUUID>.json
│   └── ...
└── previews/                   # reservado para Fase 2
```

Cada original y su metadata son unidades independientes. Una corrupción de un ZIP ya no invalida la sesión completa.

## Crash safety implementado

Antes de abrir la cámara se guarda `pending_capture.json` con:

- Photo UUID;
- secuencia;
- nombre reservado;
- pose de respaldo;
- timestamp.

Si Android cierra InspeccionPhotos mientras la cámara está abierta y el original ya fue escrito, al volver se recupera la captura pendiente y se crea su metadata. Si sólo quedó un archivo vacío, se limpia de forma segura.

Las sesiones activas se detectan al abrir la app y ofrecen:

- CONTINUAR;
- FINALIZAR (abre auditoría antes de cerrar);
- DESCARTAR, conservando originales.

## Fase 1 implementada

- selección de facultad/zona;
- iniciar/finalizar;
- captura con cámara nativa;
- GPS con precisión horizontal y vertical cuando Android la entrega;
- orientación mediante Rotation Vector y fallback acelerómetro + magnetómetro;
- trayectoria periódica;
- metadata independiente por fotografía;
- niveles de calidad GPS;
- modo Exterior/Interior manual;
- puntos de interés de un toque;
- auditoría al finalizar;
- persistencia local;
- recuperación tras cierre/crash;
- `manifest.json` con `schemaVersion: 1.0`;
- backup ZIP manual opcional;
- previews de Inicio, Nuevo levantamiento, Captura y Auditoría.

## Limitación deliberada de Fase 1

La captura usa la cámara nativa del dispositivo en lugar de integrar CameraX. Esto minimiza dependencias y prioriza una captura estable. La pose se asocia usando el timestamp EXIF y el punto de trayectoria temporalmente más cercano; si no existe un punto cercano se conserva la pose de respaldo tomada al abrir la cámara y la confianza baja. Una futura cámara integrada podrá capturar pose y parámetros intrínsecos exactamente en el instante del obturador.

## Fases siguientes — no simuladas

### Fase 2
- Supabase privado;
- cola offline;
- SHA-256;
- verificación remota;
- preview/reference/thumbnail;
- reintento individual.

### Fase 3
- mapa;
- route.geojson;
- cobertura;
- GIS;
- edificio probable.

### Fase 4
- interiores avanzados;
- ARCore / odometría visual;
- ráfaga espacial;
- vídeo/escaneo caminando.

No se añadieron botones que finjan que estas funciones ya existen.
