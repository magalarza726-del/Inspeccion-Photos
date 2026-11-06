# Inspeccion Photos v13 — Herramientas de carpeta y Panel de Control por pestañas

- `Duplicar carpeta`: copia carpeta, subcarpetas, fotos y datos asociados.
- `Clonar estructura`: copia solamente la jerarquía/nombres de carpetas, sin fotos ni contenido de reporte.
- Imagen principal: cámara, galería o evidencia existente.
- Tabla e Imagen principal: accesos rápidos por icono dentro de la carpeta abierta.
- Panel de Control: pestañas separadas `Formato de fotos` y `Formatos de formularios`.

# Inspeccion Photos v10 — UI Refresh

Esta versión toma como referencia visual el mockup aprobado de Inspeccion Photos y conserva la funcionalidad de la v9.1.

## Cambios visuales principales

- Splash de inicio con logo de Inspeccion Photos.
- En teléfono, navegación mediante menú lateral deslizable en vez de una barra permanente estrecha.
- Barra superior contextual con nombre de pantalla/carpeta.
- Pantalla Carpetas simplificada con búsqueda, Lista/Galería y botón flotante para crear carpeta.
- Vista de subcarpetas y fotos más compacta, con tarjetas similares al diseño aprobado.
- Vista Lista de fotos muestra fecha y tamaño del archivo.
- Menús contextuales como hoja inferior oscura.
- Reorganización de carpetas con selección múltiple dedicada.
- Cámara CameraX integrada tanto en modo una-por-una como sesión continua.
- Revisión de foto a pantalla completa antes de abrir el editor.
- Editor visual con herramientas rápidas y sliders.
- Configuración reorganizada en secciones compactas.
- PDF de 1 a 4 columnas con cabecera de empresa, logo y ruta de carpeta.

## Preview

Abre `app/src/main/java/ec/espol/inspeccionphotos/ui/HierarchyPreviews.kt` y usa **Split/Design**.

---

# Inspeccion Photos — v9

Esta revisión añade jerarquía de hasta 7 subniveles, reorganización de carpetas, vistas Lista/Galería por carpeta, persistencia de navegación al rotar, PDFs automáticos de 1–4 figuras por fila, editor de fotos y configuración avanzada de cámara. Consulta `V9_HIERARCHY_EDITOR.md`.

# Inspeccion Photos — v8.2

Esta revisión añade configuración de visibilidad para Levantamiento y OT, branding configurable para PDFs y exportación ZIP optimizada. El `applicationId` se conserva para no romper datos existentes.

# InspeccionPhotos in situ v8 — ORIGINAL + capa de levantamiento

Esta revisión cambia la arquitectura visual de las versiones 4–7: **el levantamiento espacial ya no reemplaza a InspeccionPhotos**.

La base visible vuelve a ser el flujo del APK original de InspeccionPhotos in situ:

- **Carpetas** de inspección.
- búsqueda por **Carpeta / Código**.
- OT.
- códigos 001, 002, 003…
- observación general por código.
- fotografía desde cámara o galería.
- menú de fotografía: mostrar, renombrar, observación, descargar y eliminar.
- PDF de observaciones.
- `respaldo_datos.json`.
- exportación y uso compartido de `.ZIP`.
- modo claro / oscuro.
- barra lateral compacta / expandida.

Sobre esa base se monta una sola entrada nueva en la barra lateral:

**Levantamiento**

Ahí viven las Fases 1–4: GPS, IMU, trayectoria, SHA-256, cola offline, Supabase, GeoJSON, GIS/cobertura, interior relativo, ARCore, ráfaga espacial y escaneo caminando/vídeo.

## Fuente de reconstrucción

La UI y el flujo original se reconstruyeron tomando como autoridad el APK suministrado por el usuario:

- paquete: `ec.espol.inspeccionphotos`
- SHA-256 del APK de referencia: `20eb862b3a476e2e2b69a09538d226346697202e07b11ade2fb1598f34b1a81d`
- fuente Kotlin visible en el DEX: `MainActivity.kt`
- modelos originales identificados: `Inspection`, `CodeItem`, `PhotoItem`
- funciones originales identificadas, entre otras: `renderFolders`, `renderInspectionDetail`, `folderCard`, `codeCard`, `photoCard`, `openCamera`, `openImagePicker`, `createObservationPdf`, `exportInspection`, `saveData` y `loadData`.

También se reutilizaron directamente del APK de referencia el **icono original** y el **logo del PDF**, y se reconstruyeron los vectores de carpeta, configuración, menú, más opciones, cámara, búsqueda, imagen, descarga y comentario a partir de los recursos originales.

Consulta `ORIGINAL_RECONSTRUCTION.md` para el inventario detallado.

## Arquitectura

```text
MainActivity
└── InspeccionPhotosApp                 ← shell ORIGINAL restaurado
    ├── Carpetas                 ← flujo original
    │   ├── Inspección
    │   ├── OT
    │   ├── Códigos
    │   ├── Fotos
    │   └── PDF / JSON / ZIP
    │
    ├── Levantamiento            ← CAPA adicional
    │   └── SurveyLayerApp
    │       ├── Fase 1 offline-first
    │       ├── Fase 2 integridad/Supabase
    │       ├── Fase 3 GIS/cobertura
    │       └── Fase 4 interior/ARCore/ráfaga/vídeo
    │
    └── Configuración            ← configuración visual original
```

La capa de levantamiento se mantiene separada del modelo clásico de inspecciones. No convierte una carpeta antigua en un `Survey`, ni obliga a utilizar GPS/Supabase para seguir usando el InspeccionPhotos tradicional.

## Abrir en Android Studio

1. Descomprime el ZIP.
2. Abre la carpeta `InspeccionPhotos_AndroidStudio_v8_ORIGINAL_LAYER`.
3. Usa **Distribution: Wrapper**.
4. Conserva el JBR/JDK con el que ya sincronizó el proyecto anterior.
5. Ejecuta **Sync Project with Gradle Files** y luego **Build > Make Project**.

Se conserva la cadena FAST_SYNC del proyecto que ya funcionó en el equipo del usuario:

- Android Gradle Plugin 9.1.0
- Gradle 9.3.1
- compileSdk 36
- bytecode Java 17

## Vista previa — interfaz ORIGINAL

Abre:

`app/src/main/java/ec/espol/inspeccionphotos/ui/LegacyPreviews.kt`

Previews nuevas:

- **ORIGINAL · Carpetas horizontal**
- **ORIGINAL · Inspección horizontal**
- **ORIGINAL · Carpetas vertical**
- **ORIGINAL + capa de levantamiento**

Son las previews recomendadas para comparar visualmente con las capturas del APK original.

Las previews técnicas de las Fases 1–4 siguen disponibles en:

`app/src/main/java/ec/espol/inspeccionphotos/ui/Previews.kt`

## Persistencia clásica

El módulo original usa:

`respaldo_datos.json`

El lector acepta tanto un arreglo raíz histórico como el objeto versionado usado por la reconstrucción. Antes de reemplazar un respaldo se conserva `respaldo_datos.bak`. Si el JSON está dañado, no se sobrescribe: se conserva una copia `respaldo_datos_corrupto_<timestamp>.json`.

Las nuevas fotos del modo clásico se guardan de forma privada en:

`filesDir/legacy_inspections/<inspectionId>/photos/`

El sistema espacial mantiene su almacenamiento independiente `surveys/`.

## Exportación clásica

**Exportar y compartir .ZIP** vuelve a generar:

- PDF de observaciones con el logo original del laboratorio.
- `respaldo_datos.json`.
- fotografías de la inspección.
- ZIP compartible por Android.

PDF, JSON y ZIP se copian también a Descargas. Las fotografías pueden descargarse individualmente a Pictures/InspeccionPhotos.

## Fases espaciales conservadas

No se eliminaron las Fases 1–4. Siguen incluidos los módulos `camera/`, `location/`, `sensors/`, `survey/`, `storage/`, `sync/`, `integrity/`, `previews/`, `coverage/`, `gis/`, `interior/`, `arcore/`, `scan/`, `spatial/` y `worker/`.

La semántica posicional corregida de Fase 4 se conserva:

- `GNSS_ABSOLUTE`
- `INTERIOR_RELATIVE`
- `UNKNOWN`

Una pose interior relativa nunca se presenta como precisión GPS absoluta.

## Nota de validación

En este entorno se realizó auditoría estructural del proyecto, comprobación de sintaxis Kotlin de los archivos añadidos (sin Android/Compose en el classpath), comprobación de recursos y prueba de integridad del ZIP. El build Android completo debe ejecutarse en Android Studio porque aquí no está instalado el SDK/distribución Gradle necesaria para resolver las APIs Android.

## Revisión v8.1 — interfaz/fotos/branding

Esta revisión corrige tres incidencias detectadas en dispositivo real:

- diálogos claros ya no heredan texto blanco del tema oscuro;
- cámara y selector de imágenes del módulo clásico tienen flujo robusto de permisos/errores;
- se retira el branding visible de MARCA_ANTERIOR/InspeccionPhotos del encabezado y del PDF; el launcher se muestra como `In situ` sin icono personalizado.

Consulta `FIX_V8_1_UI_PHOTOS_BRANDING.md` para el detalle técnico.


## v11 - Panel de Control y Reporte Personalizado

- Nuevo botón **Panel de Control** en la navegación principal.
- Plantilla configurable: título, subtítulo, dos espacios laterales de texto/imagen/vacío, tabla, imagen principal y evidencias.
- Exportación personalizada en PDF, Word y Excel. Word y Excel conservan la apariencia del PDF; Excel añade una hoja `Datos`.
- Importar/exportar configuración de reporte como JSON portátil.
- Vista previa en tiempo real.
- Por carpeta: `Agregar tabla` y `Agregar imagen principal`.
- Antes del ZIP se eligen Fotos+estructura, PDF 1/2/3/4 y Reporte Personalizado; la selección se recuerda.

## v11.1 - Integridad del Reporte Personalizado
La tabla se persiste incluso si todas sus celdas quedan vacías. En la exportación Fotos+estructura, carpetas y fotos llevan prefijos numéricos que conservan el orden fuera de la aplicación; también se transporta la imagen principal de cada carpeta y un manifiesto JSON enriquecido.

## v12 — Formularios independientes

Se añadió un módulo **Formularios** separado de las carpetas/fotos y del Reporte Personalizado.

- Navegación: Carpetas → Levantamiento → Formularios → Panel de Control → Configuración.
- En **Panel de Control** se pueden crear varios formatos de formularios independientes.
- Cada formato conserva su propio Título, Subtítulo, espacio 3 y espacio 4; no modifica el Reporte de Fotos.
- De 1 a 12 columnas configurables.
- Cada columna puede usar celdas libres o celdas desplegables.
- Las opciones desplegables pertenecen exclusivamente a su columna.
- El formato inicia con 21 filas por defecto y admite hasta 100 durante el llenado.
- En **Formularios** se selecciona un formato, se completa la tabla y se guarda automáticamente como borrador.
- Los formularios completados conservan una copia del formato utilizado aunque posteriormente se modifique la plantilla.
