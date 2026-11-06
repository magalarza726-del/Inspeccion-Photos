# v18.2.1 — Compile fix

Corregido `SurveyMapScreen.kt`:

- Se eliminaron dos imports duplicados de `java.io.File` que habían quedado insertados en medio del archivo.
- `java.io.File` queda importado una sola vez en la cabecera.
- Esto corrige los errores `Conflicting import` y `imports are only allowed in the beginning of file` reportados por Kotlin.
- No se modificó la lógica de mapas, cámara, rotación ni metadatos espaciales.

Versión: `3.4.3-rotation-spatial-compile-fix` / versionCode `343`.
