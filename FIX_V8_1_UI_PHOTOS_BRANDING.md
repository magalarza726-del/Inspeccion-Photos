# v8.1 — UI, fotografías y retirada de branding MARCA_ANTERIOR

Cambios aplicados sobre v8 ORIGINAL_LAYER:

1. **Texto blanco sobre fondo blanco**
   - Los diálogos blancos del flujo clásico ahora fuerzan texto, labels, cursor y botones con colores compatibles con fondo claro.
   - Se evita heredar los colores del tema oscuro en `OutlinedTextField` dentro de `AlertDialog`.

2. **Tomar foto**
   - El flujo clásico solicita `CAMERA` en tiempo de ejecución antes de abrir la cámara.
   - `FileProvider`, creación del archivo y lanzamiento de cámara están protegidos con manejo de errores.
   - Si la captura se cancela o falla, el temporal se elimina y la app no conserva una foto vacía.

3. **Seleccionar imagen**
   - Se cambió de `OpenDocument` a `GetContent` para un flujo más simple y compatible, ya que la imagen se copia de inmediato al almacenamiento privado de la app.
   - Se valida que el archivo importado exista y tenga contenido; los parciales se eliminan si ocurre un error.

4. **Branding MARCA_ANTERIOR**
   - Se quitó el logotipo y el nombre visible del encabezado de la interfaz.
   - Se quitó el logotipo y el texto `Laboratorio de Evaluación de Materiales` del PDF de inspección.
   - No se cambia `applicationId`, nombres internos de base de datos o rutas para no romper datos existentes.

El encabezado superior queda neutro; en horizontal conserva solamente el indicador de almacenamiento.

5. **Identidad visible de la app**
   - Se retiró el icono personalizado del `AndroidManifest`.
   - El nombre visible del launcher pasa a `In situ`.
   - Se eliminaron menciones visibles de `InspeccionPhotos` en mensajes de interfaz donde no eran necesarias.
   - Los identificadores internos (`applicationId`, tablas Supabase, SharedPreferences y rutas de compatibilidad) NO se renombran para evitar pérdida o desconexión de datos previos.
