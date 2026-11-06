# v18.2 — Rotación EXIF y observaciones espaciales completas

## Correcciones de fotografías
- La cámara integrada sincroniza `targetRotation` de CameraX con la rotación real de la pantalla al enlazar y justo antes de cada captura.
- Se añadió una decodificación común que respeta la orientación EXIF (incluye 90°, 180°, 270° y variantes espejadas).
- La orientación EXIF se aplica al mostrar fotos en galería/lista, revisión, confirmación de captura y editor.
- Los reportes estándar, espaciales, personalizados y las imágenes de encabezado de formularios usan bitmaps orientados correctamente.
- El editor de fotos parte de la imagen ya orientada; al guardar una edición queda normalizada físicamente y deja de depender del EXIF para verse derecha.

## Funciones espaciales
- Se reactivó la captura inercial mientras `Agregar funciones espaciales` está habilitado.
- Cada foto de una sesión continua congela sus propios datos espaciales en el instante de la captura; ya no todas reciben el último dato disponible al terminar la sesión.
- La observación espacial incluye, cuando el dispositivo lo ofrece:
  - latitud y longitud;
  - precisión horizontal y vertical;
  - altitud, velocidad y proveedor de ubicación;
  - azimut, pitch y roll;
  - precisión de orientación;
  - giroscopio X/Y/Z;
  - aceleración X/Y/Z;
  - magnetómetro X/Y/Z;
  - presión barométrica y contador de pasos.
- El Reporte espacial deja de truncar la observación a una sola línea y muestra el bloque espacial en varias líneas debajo de cada fotografía.

## Versión
- versionCode: 342
- versionName: 3.4.2-rotation-spatial-fix
