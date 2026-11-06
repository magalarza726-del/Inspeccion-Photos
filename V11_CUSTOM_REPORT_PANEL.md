# Inspeccion Photos v11 - Panel de Control

Esta revisión añade un generador de Reporte Personalizado sin eliminar las exportaciones anteriores.

## Panel de Control
- Título y subtítulo.
- Espacios laterales 3 y 4: vacío, texto centrado (máx. 70 caracteres, admite saltos de línea) o imagen ajustada al cuadro.
- Formatos: PDF, Word, Excel.
- Alineación imagen principal/tabla: izquierda, derecha, arriba o abajo.
- Ancho de imagen: 25% a 80%.
- Tabla configurable de 1-12 filas y 1-8 columnas, con nombres editables.
- Evidencias: 1-4 columnas simétricas.
- Importar/exportar JSON portátil.
- Vista previa en vivo.

## Durante la inspección
Cada carpeta ofrece `Agregar/Editar tabla` y `Agregar/Cambiar imagen principal`. Si no existe ninguno de los dos, las evidencias suben automáticamente sin reservar espacio. Si sólo existe uno, se centra.

## Exportación
Antes de crear el ZIP se muestran casillas para seleccionar:
- Fotos y estructura de carpetas.
- PDF 1 columna.
- PDF 2 columnas.
- PDF 3 columnas.
- PDF 4 columnas.
- Reporte Personalizado.

La última selección queda persistida. El modo Fotos+estructura incluye un manifiesto JSON y el respaldo completo para conservar el orden al transportar la inspección.

## Ajustes de integridad en v11.1
- La presencia de una tabla se guarda mediante `reportTableEnabled`, así una tabla vacía sigue apareciendo si el inspector la agregó explícitamente.
- `Fotos y estructura de carpetas` antepone índices (`0001_`, `0002_`, ...) a carpetas/fotos dentro del ZIP para preservar el orden incluso al extraerlo con herramientas externas.
- Las imágenes principales del reporte se incluyen en `_reporte/imagen_principal.*` cuando se exporta la estructura.
- El manifiesto de estructura registra orden, observaciones, configuración básica de carpeta, tabla e imagen principal.
- Ejemplo de plantilla: `examples/custom_report_config_DEMO.json`.
