# Auditoría técnica — InspeccionPhotos In Situ 2.2 / Fase 3

## 1. Base reutilizada

Fase 3 se construyó sobre la Fase 2 sin reemplazar:

- captura nativa;
- persistencia local por fotografía;
- recuperación tras crash;
- GPS / orientación / trayectoria;
- SHA-256;
- previews;
- WorkManager;
- cola offline;
- Supabase privado y verificación remota;
- ZIP manual opcional;
- sistema de Compose Preview.

No se añadieron dependencias de mapas o GIS para mantener FAST_SYNC.

## 2. Modelo espacial

`schemaVersion` pasa a `1.2`.

Se añadieron modelos separados en `survey/SpatialModels.kt`:

- GeoPoint
- BuildingFootprint
- BuildingInference
- FacadeCoverage
- BuildingCoverage
- SpatialSummary
- SpatialAnalysisBundle

La metadata original de fotografías no se modifica destructivamente. El análisis derivado vive en `analysis/` y puede regenerarse.

## 3. Route GeoJSON

`gis/RouteGeoJsonExporter.kt` genera un FeatureCollection estándar con:

- LineString de trayectoria;
- segmentos por modo EXTERIOR / INTERIOR;
- puntos de fotografía;
- heading/pitch/roll;
- FOV cuando está disponible;
- precisión GPS;
- POIs;
- inferencia de edificio cuando supera el umbral.

El archivo es `route.geojson` en la raíz de la sesión.

## 4. GIS de edificios

`gis/BuildingGeoJson.kt` importa `Polygon` y `MultiPolygon` sin librerías externas.

El GIS se guarda como `gis/buildings.geojson`.

No se incluye un mapa oficial de ESPOL inventado. El usuario puede cargar datos reales cuando estén disponibles.

## 5. Identificación automática

`spatial/BuildingIdentifier.kt` calcula un candidato utilizando:

- posición de la cámara;
- orientación/heading;
- FOV;
- huella del edificio;
- distancia a la huella;
- precisión GPS;
- facultad/zona cuando existe;
- continuidad de fotos vecinas.

La continuidad sólo refuerza un candidato GIS ya plausible; jamás crea una identidad por sí sola.

Umbral actual: confianza >= 0.55. Bajo el umbral se registra `Desconocido`.

## 6. Cobertura

`coverage/CoverageAnalyzer.kt` clasifica desde qué lado fue observado cada edificio y estima cobertura de:

- Norte
- Este
- Sur
- Oeste

La métrica combina cantidad de observaciones, tomas cercanas, diversidad angular y penalización por GPS pobre. Es deliberadamente una **estimación de cobertura de levantamiento**, no una afirmación geométrica exacta.

Se producen advertencias cuando:

- una fachada carece de documentación;
- la cobertura es parcial;
- todas las fotos de una fachada tienen GPS bajo;
- no existen tomas cercanas del edificio.

## 7. Auditoría antes de abandonar el sitio

Al pulsar FINALIZAR, InspeccionPhotos primero ejecuta el análisis espacial fuera del hilo principal y después construye la auditoría.

Por tanto, el usuario puede elegir CONTINUAR LEVANTAMIENTO cuando aparezcan advertencias de cobertura.

Además, durante una sesión activa existe **Mapa / cobertura**, por lo que no hace falta esperar al final.

## 8. Mapa offline

`ui/SurveyMapScreen.kt` usa Compose Canvas y no requiere Internet.

Muestra:

- ruta exterior/interior;
- fotos;
- vector de heading;
- radio visual de precisión GPS;
- POIs;
- polígonos de edificios;
- preview local de la foto seleccionada cuando existe;
- metadata e inferencia;
- cobertura por edificio.

No usa tiles base todavía. Esto evita convertir el mapa en una dependencia de red y mantiene la captura offline-first.

## 9. Supabase

`supabase/phase3_schema.sql` añade `inspeccion_photos_spatial_analysis` con RLS.

`SyncEngine` puede respaldar análisis espacial incluso cuando no existen fotografías pendientes en la cola.

Se guardan:

- route GeoJSON;
- edificios GIS;
- inferencias;
- cobertura;
- resumen espacial.

Un fallo de este bloque no reinicia ni invalida la cola individual de originales.

## 10. Validación realizada

El núcleo puro Kotlin de:

- modelos espaciales;
- geometría/bearings;
- identificación de edificios;
- cobertura;

se compiló con `kotlinc` en el entorno de reconstrucción.

También se ejecutó un smoke test que identificó correctamente un edificio situado delante de una cámara de prueba y generó cobertura sin asignar fachadas no observadas.

No se pudo ejecutar el Gradle build Android completo en este entorno porque el Wrapper intenta descargar Gradle 9.3.1 y el contenedor no tiene acceso de red. La compilación Android/Compose definitiva corresponde al Android Studio del usuario, donde esa distribución ya forma parte del flujo FAST_SYNC.

## 11. Tests añadidos

- SpatialMathTest
- BuildingIdentifierTest

Se conservan los tests de Fases 1/2, incluida la simulación de 1.000 fotografías.

## 12. Pendiente para Fase 4

No se implementó todavía:

- ARCore;
- visual odometry;
- navegación interior relativa avanzada;
- ráfaga espacial automática;
- escaneo caminando/vídeo y selección de frames.

Fase 4 debe construirse sin degradar la garantía de captura local de Fases 1–3.
