# Inspeccion Photos — Android

Proyecto Android (Kotlin / Jetpack Compose). Repositorio preparado para importar automáticamente un ZIP de Android Studio, compilar un APK de pruebas y publicarlo en **GitHub Releases**.

## Cargar el proyecto

1. En GitHub, usa **Add file → Upload files** en la rama `main`.
2. Arrastra el ZIP completo del proyecto Android Studio (con `gradlew`, `app/` y `build.gradle.kts`) **sin descomprimir**, a la raíz del repositorio.
3. Pulsa **Commit changes**. GitHub Actions descomprimirá el ZIP, colocará el código Kotlin en el repositorio y ejecutará `assembleDebug`.
4. Si compila, la sección **Releases** tendrá el APK `Inspeccion-Photos.apk`.

**Descarga directa de la última compilación exitosa:**
https://github.com/magalarza726-del/Inspeccion-Photos/releases/latest/download/Inspeccion-Photos.apk

> El enlace solo estará activo **después de una compilación y publicación correctas**.

## Actualizaciones

Repite la carga con un ZIP nuevo. No hace falta descomprimirlo. El flujo reemplaza los archivos anteriores del proyecto por los del ZIP y crea una nueva versión en Releases.

## Consideraciones

- La compilación publica un **APK debug** para pruebas, no un APK release firmado para producción.
- Los APK debug producidos en distintas ejecuciones pueden llevar firmas diferentes: para actualizar sin desinstalar se necesita configurar una firma persistente.
- Para uso continuo de la app y conservación de datos, configura una clave de firma de release administrada como secreto en GitHub Actions.
- Si alguna dependencia o compilación falla, revisa el registro en **Actions**; no se publica un nuevo APK si no compila.
