# v8 — Original Layer

## Motivo

Las versiones 4–7 convirtieron el levantamiento espacial en la interfaz principal. Eso hizo perder el flujo visual y mental del InspeccionPhotos original y produjo pantallas demasiado densas en teléfonos verticales.

v8 corrige la dirección del producto:

> **InspeccionPhotos original abajo; levantamiento espacial encima como una capa opcional.**

## Resultado

- Arranque en **Carpetas**.
- Header y sidebar reconstruidos desde la APK de referencia.
- Carpetas/Códigos/Fotos recuperan su jerarquía original.
- La nueva función aparece como una entrada adicional **Levantamiento**.
- Configuración clásica sigue separada de la configuración técnica del levantamiento.
- Las Fases 1–4 permanecen intactas dentro de `SurveyLayerApp`.
- El shell aplica `safeDrawing` para evitar que Android 15/16 dibuje texto y controles debajo de las barras del sistema.
- Pantallas del levantamiento que antes se comprimían en retrato fueron convertidas a layout responsivo.

## Archivos principales nuevos

```text
ui/InspeccionPhotosRootApp.kt
ui/LegacyComponents.kt
ui/LegacyInspectionScreens.kt
ui/LegacySettingsScreen.kt
ui/LegacyUiPreferences.kt
ui/LegacyPreviews.kt
InspectionMediaStore.kt
ORIGINAL_RECONSTRUCTION.md
```

## Cambio de nombre importante

La app espacial anterior:

```kotlin
InspeccionPhotosApp()
```

ahora es:

```kotlin
SurveyLayerApp()
```

El nombre `InspeccionPhotosApp()` vuelve a representar la aplicación completa y monta ambos mundos.
