# Inspeccion Photos v17.3 — JSON de formularios y vista previa

## Cambios

- Importación y exportación JSON de formatos de formulario.
- Exportación individual o conjunta de formatos desde Panel de Control → Formularios.
- El JSON portátil incluye columnas, opciones desplegables, visibilidad, secciones, firmas y las imágenes de encabezado en Base64.
- Importar un formato genera un ID nuevo para evitar sobrescribir formatos existentes y ajusta nombres duplicados.
- Importación y exportación JSON de formularios ya llenados desde la pantalla Formularios.
- Los formularios guardados exportados incluyen datos de celdas, valores de secciones y una copia del formato usado.
- Vista previa de formularios rediseñada como una página A4: encabezado, secciones, tabla visible, firmas y pie de página.
- La vista previa respeta columnas ocultas en el reporte y limita visualmente el número de columnas/secciones sin hacer desaparecer la tabla.
- Se mejoró también la representación compacta de secciones usada en las vistas previas.

## Validación

No fue posible ejecutar la compilación Android completa porque el entorno no puede descargar Gradle 9.3.1 desde services.gradle.org. Los cambios se revisaron estáticamente y no se modificó la lógica de exportación PDF/Word/Excel existente.
