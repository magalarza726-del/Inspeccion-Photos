# Google Drive API — configuración de Inspeccion Photos

La app incluye en **Configuración → Google Drive** un flujo OAuth 2.0 para comprobar una conexión real con Google Drive usando Google Identity Services y el alcance:

`https://www.googleapis.com/auth/drive.file`

La app no guarda el access token en SharedPreferences. Cuando el usuario pulsa **Conectar Google Drive** o **Verificar / cambiar cuenta**, Google Play Services solicita/recupera autorización y la app valida el token haciendo una llamada autenticada a Drive API v3.

## Configuración una sola vez en Google Cloud

1. Crea o selecciona un proyecto en Google Cloud Console.
2. Habilita **Google Drive API** para ese proyecto.
3. Configura la pantalla de consentimiento OAuth.
4. Crea un **OAuth client ID de tipo Android**.
5. Usa como package name: `ec.espol.inspeccionphotos`.
6. Registra la huella **SHA-1** del certificado con el que compilas/firmas la app. Para producción, registra también la huella del certificado de release correspondiente.
7. Compila e instala la app. Entra a **Configuración → Google Drive → Conectar Google Drive**.

No se debe colocar un client secret dentro del APK para este flujo Android.

## Implementación añadida

- Dependencia: `com.google.android.gms:play-services-auth:21.5.1`
- UI OAuth: `LegacySettingsScreen.kt`
- Verificación Drive REST: `sync/GoogleDriveRestClient.kt`
- Estado de última conexión verificada: `LegacyUiPreferences.kt`

Esta versión prepara una conexión autorizada y comprobable. No añade todavía sincronización automática de fotos/exportaciones con Drive; esa funcionalidad puede consumir este mismo flujo de autorización en una siguiente fase.
