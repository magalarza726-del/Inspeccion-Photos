# Inspeccion Photos v18 — navegación, ZIP, formularios y funciones espaciales

Versión Android: `3.4.0-home-zip-spatial-export` (`versionCode 340`).

## Cambios principales

1. Se eliminó del código la vinculación histórica con el nombre anterior. El namespace y applicationId pasan a `ec.espol.inspeccionphotos`.
2. Nueva pantalla principal sin barra lateral, con accesos por icono a Fotos + Carpetas, Formularios, Formatos personalizados y Configuración.
3. La navegación deja de reiniciarse al rotar: la Activity maneja los cambios de orientación/tamaño y los estados principales usan estado guardable.
4. Los formularios ya llenados adoptan automáticamente cambios compatibles de su formato: opciones desplegables, exportaciones, encabezados, visibilidad, secciones, firmas y columnas compatibles por ID.
5. Configuración se simplificó: se retiraron los apartados informativos de subniveles y el acceso al antiguo modo de levantamiento.
6. Las celdas, títulos de sección y nombres de firma pueden quedar vacíos o contener espacios sin restaurar automáticamente los textos de ejemplo.
7. Se puede importar un ZIP creado con Fotos + Carpetas. Se reconstruyen carpetas/subcarpetas, orden, fotos, observaciones, vista, imagen principal, tabla y valores de secciones.
8. El antiguo modo de levantamiento ya no forma parte de la navegación. Configuración ofrece `Agregar funciones espaciales`, desactivado por defecto. Cuando está activo, las capturas de cámara guardan ubicación/orientación disponible dentro de la observación de la foto sin cambiar la interfaz de Fotos + Carpetas.
9. Nueva selección de exportación: Fotos + Carpetas, Reporte estándar (con segundo paso para PDF a 1/2/3/4 columnas), Reporte espacial cuando las funciones espaciales están activas y Reporte personalizado.

## Nota de migración de paquete

Al cambiar el `applicationId`, Android considera esta compilación una aplicación distinta de versiones instaladas con el identificador anterior. También hay que registrar `ec.espol.inspeccionphotos` en el cliente OAuth Android de Google Drive junto con la huella SHA correspondiente.

## Validación en este entorno

- Los modelos Kotlin puros modificados compilan con `kotlinc`.
- Se verificó que no quedan referencias textuales al nombre anterior ni al antiguo enum de navegación de levantamiento en la interfaz principal.
- `./gradlew :app:assembleDebug` no pudo ejecutarse porque el entorno no tiene Gradle 9.3.1 en caché y no puede resolver `services.gradle.org` para descargarlo. Por tanto, la compilación Android completa debe validarse en Android Studio.
