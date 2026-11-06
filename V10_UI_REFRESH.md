# V10 UI Refresh

## Referencia aplicada

La interfaz se actualizó con la composición visual aprobada que mostraba 12 estados: splash, menú principal, carpetas, subcarpetas, galería, lista, menú de foto, cámara, revisión postcaptura, editor, configuración y reorganización; además de los cuatro formatos de PDF.

## Adaptación responsive

- Teléfono vertical: drawer deslizable + top bar contextual + contenido a ancho completo.
- Tablet/horizontal: se conserva sidebar permanente para trabajo rápido.
- La ruta seleccionada sigue guardándose con `rememberSaveable`, por lo que rotar el teléfono no devuelve a la raíz.

## Cámara

Ambos modos de captura usan la cámara integrada CameraX. `ONE_BY_ONE` finaliza tras una fotografía; `BATCH` conserva la cámara abierta hasta pulsar Finalizar.

## PDF

Las cuatro variantes siguen generándose automáticamente. La cabecera usa el logo personalizado si existe y, si no, el logo de Inspeccion Photos. Las imágenes se recortan al marco manteniendo proporción mediante center-crop.
