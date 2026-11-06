# Inspeccion Photos v12 — Formularios

## Separación conceptual

El **Panel de Control** mantiene el diseñador del Reporte Personalizado de fotografías y ahora añade un administrador independiente de formatos de formularios.

Los campos 1–4 del encabezado de cada formulario (Título, Subtítulo, espacio izquierdo y espacio derecho) pertenecen a la plantilla del formulario y no están vinculados a los campos equivalentes del reporte fotográfico.

## Formatos

Cada formato incluye:

- nombre del formato;
- título y subtítulo;
- espacio 3 y 4 como vacío, texto o imagen;
- 1–12 columnas;
- 21 filas iniciales por defecto;
- comportamiento independiente por columna: `FREE_TEXT` o `DROPDOWN`;
- lista de opciones propia para cada columna desplegable.

## Llenado

La sección **Formularios** permite:

1. escoger un formato;
2. crear un llenado nuevo;
3. escribir en celdas libres;
4. escoger valores de listas desplegables;
5. agregar/quitar filas hasta un máximo de 100;
6. guardar automáticamente como borrador;
7. finalizar el formulario;
8. reabrir o eliminar llenados guardados.

Cada llenado guarda un snapshot de su plantilla para conservar su estructura histórica aunque la plantilla maestra cambie después.

## Persistencia

Archivos internos:

- `form_templates.json`
- `form_submissions.json`
- `form_template_images/`

