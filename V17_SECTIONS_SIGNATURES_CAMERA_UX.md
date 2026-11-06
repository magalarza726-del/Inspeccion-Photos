# Inspeccion Photos v3.3.0

Actualización centrada en secciones configurables, firmas, formularios y flujo fotográfico.

## Cambios principales
- Secciones configurables en Panel de Control y plantillas de Formularios.
- Líneas A, B, C y D combinables libremente.
- Separadores verticales ajustables mediante sliders por línea.
- Celdas configurables como etiqueta fija o valor editable.
- De 1 a 3 firmas, con nombre y alineación independiente.
- Ordenamiento de formularios por columna: original, ascendente y descendente, con comparación numérica cuando aplica.
- Columnas ocultables de forma independiente en la app y en los reportes.
- Exportación de formularios actualizada para respetar secciones, columnas ocultas y firmas.
- Reporte personalizado de carpetas/subcarpetas con valores de sección persistentes.
- Configuración para ocultar PDF 1, 2, 3 y/o 4 del diálogo de exportación.
- Configuración “Preguntar si está bien la foto” con previsualización antes de aceptar.
- Sesión continua con tira horizontal de fotografías tomadas, inspirada en el flujo de cámara de WhatsApp.
- Vista previa de la imagen principal dentro de la carpeta.

## Compatibilidad
Los modelos y preferencias nuevos tienen valores por defecto para conservar proyectos y configuraciones anteriores.

## Validación en este entorno
Se intentó compilar `:app:assembleDebug`, pero el Gradle Wrapper requiere descargar Gradle 9.3.1 y este entorno no tiene acceso de red. Por eso el APK no pudo generarse aquí. El proyecto fuente queda preparado para abrirse y compilarse en Android Studio con conexión a Internet.
