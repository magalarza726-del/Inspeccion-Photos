# Build Fix v10.2

Corrige el error de compilación en `InspeccionPhotosRootApp.kt`:

`Unresolved reference 'clickable'`

La causa era que el archivo usaba `Modifier.clickable { ... }` dentro del diálogo **Mover carpeta** sin importar la extensión de Compose Foundation.

Se añadió:

```kotlin
import androidx.compose.foundation.clickable
```

También se revisaron todos los archivos Kotlin del proyecto que usan `.clickable`; el resto ya tenía su import correspondiente.
