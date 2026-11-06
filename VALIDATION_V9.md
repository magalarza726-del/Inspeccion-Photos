# Validación v9

Validaciones realizadas antes de empaquetar:

- XML de `app/src/main/res`: parseo correcto.
- Núcleo Kotlin `Models.kt + InspectionTree.kt`: compilación aislada correcta.
- Smoke test del árbol: mover varias carpetas, impedir ciclos y rechazar un octavo subnivel: correcto.
- Búsqueda de errores sintácticos (`expecting`, `unexpected tokens`, redeclaraciones) sobre las fuentes Kotlin: sin incidencias sintácticas detectadas.
- No se añaden dependencias Gradle para el editor de fotos ni para la jerarquía.
- Persistencia de pantalla clásica: `rootSectionName` y `selectedFolderId` usan `rememberSaveable`.

El build Android completo debe ejecutarse en Android Studio porque el entorno de generación no dispone de la distribución Gradle 9.3.1 descargada ni acceso de red para obtenerla.
