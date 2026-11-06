# FAST_SYNC — nota Fase 4

Se conserva exactamente la base Gradle/AGP que se alineó con el proyecto SKC que el usuario confirmó que cargaba rápido:

- AGP 9.1.0
- Gradle Wrapper 9.3.1
- compileSdk 36
- Java bytecode 17

Fases 1–3 evitaban SDKs adicionales. Fase 4 sí necesita dos familias nuevas para cumplir el objetivo técnico:

- CameraX 1.6.1 (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-video`)
- ARCore 1.54.0

Por eso la **primera** sincronización de v7 puede ser más lenta si Android Studio aún no tiene esos AAR en caché. No se cambió el Wrapper ni se introdujo otro repositorio de dependencias.

Supabase continúa por REST nativo (`HttpURLConnection`), por lo que no se añadió el SDK Supabase.
