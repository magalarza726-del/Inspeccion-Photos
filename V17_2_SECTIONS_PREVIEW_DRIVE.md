# v17.2 — Secciones por ventana + vistas previas + Google Drive

## Correcciones

- Las secciones aparecen ahora en la vista previa del Reporte Personalizado.
- Las secciones aparecen también en la vista previa de los formatos de Formularios.
- En Carpetas/Fotos, las secciones ya no ocupan espacio permanente: se abren mediante un icono propio junto al icono de tabla.
- En Formularios, las secciones se rellenan desde un icono que abre una ventana independiente.
- Se eliminó el editor de secciones incrustado encima de la tabla del formulario, por lo que la tabla recupera el espacio principal de la pantalla.
- Los valores de las secciones de cada formulario siguen guardándose dentro de su `FormSubmission`.
- Se añadió Configuración → Google Drive con autorización OAuth 2.0 y comprobación real contra Drive API v3 usando `drive.file`.

## Versión Android

- versionCode: 332
- versionName: 3.3.2-sections-preview-drive

## Nota de compilación

El entorno de generación utilizado no puede resolver `services.gradle.org`, por lo que no fue posible ejecutar el build Android completo. Se realizó validación sintáctica local de los archivos Kotlin modificados y compilación aislada del cliente REST puro de Google Drive.
