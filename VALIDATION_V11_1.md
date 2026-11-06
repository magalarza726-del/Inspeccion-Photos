# Validación v11.1

- Núcleo Kotlin `Models + CustomReportModels + InspectionTree`: compilado con `kotlinc` -> OK.
- Nuevo recurso `ic_panel_control.xml`: XML válido.
- `examples/custom_report_config_DEMO.json`: JSON válido.
- Persistencia de tabla explícita: `reportTableEnabled` evita perder una tabla vacía.
- Exportación Fotos+estructura: orden físico preservado con prefijos numéricos y manifiesto v2.
- Reporte de prueba renderizado a PNG y revisado visualmente: sin clipping ni solapamientos.

No se ejecutó `assembleDebug` en este entorno porque no existe una distribución Gradle/Android SDK local y la red está deshabilitada. La compilación Android definitiva debe ejecutarse en Android Studio.
