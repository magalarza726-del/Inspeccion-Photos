# Auditoría de reconstrucción — APK original como autoridad

## 1. Regla de esta versión

La app original vuelve a ser el producto principal. El sistema espacial es un módulo adicional y no una interfaz sustituta.

En particular, al arrancar la aplicación el usuario entra a **Carpetas**, no al dashboard de levantamiento.

## 2. Evidencia recuperada del APK original

APK de referencia:

`SHA-256 20eb862b3a476e2e2b69a09538d226346697202e07b11ade2fb1598f34b1a81d`

El APK conserva nombres Kotlin no ofuscados. Se identificaron, entre otros:

```text
MainActivity.kt
Inspection
CodeItem
PhotoItem
Palette

buildAppShell
buildHeader
rebuildSidebar
renderFolders
renderInspectionDetail
renderSettings
folderCard
newFolderCard
codeCard
codeRow
photoRecycler
photoCard
addPhotoCard
openCamera
openImagePicker
showImageReview
createFolderDialog
createCodeDialog
renameFolderDialog
renameCodeDialog
renamePhotoDialog
editInspectionOt
editCodeObservation
editPhotoObservation
downloadPhoto
createObservationPdf
exportInspection
shareZipFile
cloneInspection
saveData
loadData
ensureThumbnail
```

También se identificaron los nombres originales:

```text
inspeccion_photos_prefs
dark_mode
sidebar_expanded
respaldo_datos.json
```

## 3. Flujo visual restaurado

### Carpetas

- `Gestiona tus carpetas de inspección de campo.`
- `Buscar carpetas o códigos...`
- `Buscar por: Carpeta / Código`
- `Carpetas registradas (n)`
- tarjetas horizontales de carpeta.
- tarjeta `Nueva carpeta`.

Menú visible de carpeta restaurado según la interfaz de referencia:

1. Abrir carpeta
2. Agregar/editar OT
3. Renombrar carpeta
4. Exportar y compartir .ZIP
5. Eliminar carpeta

La lógica de clonado identificada en el APK se conserva en código, pero no se fuerza como una opción nueva en el menú de referencia.

### Detalle de inspección

- nombre de carpeta y fecha.
- `OT:` + `Guardar OT`.
- `+ Código`.
- `Exportar y compartir .ZIP`.
- `Desliza para ver más fotos.`
- una fila por código: tarjeta de código → Agregar foto → fotografías.

### Fotografías

Agregar foto:

- Tomar foto
- Seleccionar imagen

Menú de fotografía:

- Mostrar imagen
- Renombrar foto
- Observación de la foto
- Descargar foto
- Eliminar foto

## 4. Recursos originales

Se incorporaron desde el APK de referencia:

- `inspeccion_photos_original_logo.png`
- `logo_pdf_original.png`

Y se reprodujeron desde los vectores originales:

- `ic_folder_original.xml`
- `ic_settings_original.xml`
- `ic_menu_original.xml`
- `ic_more_vert_original.xml`
- `ic_plus_original.xml`
- `ic_camera_original.xml`
- `ic_search_original.xml`
- `ic_image_original.xml`
- `ic_download_original.xml`
- `ic_comment_original.xml`

## 5. Capa espacial

La única ampliación estructural de navegación es **Levantamiento** en la barra lateral. Al entrar se monta `SurveyLayerApp` y quedan disponibles las Fases 1–4 existentes.

Esto evita mezclar dos conceptos:

```text
Inspection / CodeItem / PhotoItem
```

continúan siendo la inspección clásica, mientras que:

```text
SurveySession / PhotoRecord / RoutePoint / POI / SpatialAnalysis
```

pertenecen al levantamiento espacial.

## 6. Cambios respecto a v7.1

- `MainActivity` sigue siendo mínima.
- `InspeccionPhotosApp` vuelve a representar el shell de InspeccionPhotos original.
- la antigua app espacial se renombró a `SurveyLayerApp`.
- se eliminó el segundo encabezado del módulo espacial.
- Carpetas es la sección inicial.
- se restauró el logo original en el header.
- se restauraron iconos originales en navegación, carpetas, códigos y fotos.
- se restauró el estilo de diálogos blancos del APK de referencia.
- se agregó persistencia clásica robusta compatible con `respaldo_datos.json`.
- se restauraron PDF/JSON/ZIP/fotos del flujo tradicional.
- se corrigió la responsividad de Inicio, Configuración, Sincronización y Captura del módulo espacial para que no vuelva a partir palabras letra por letra en un teléfono vertical.

## 7. Lo que no se afirma

Un APK compilado no permite recuperar exactamente comentarios, nombres de variables locales y estructura fuente línea por línea. Por tanto, este proyecto no pretende ser el código fuente perdido byte-a-byte.

Sí pretende reproducir el **contrato observable** del APK original: modelos, funciones, textos, recursos, navegación y operaciones visibles, mientras el código nuevo se mantiene modular y previsualizable desde Android Studio.
