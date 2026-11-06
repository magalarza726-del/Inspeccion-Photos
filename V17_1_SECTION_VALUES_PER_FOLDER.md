# Inspeccion Photos v3.3.1

## Descripciones por carpeta/subcarpeta

- Las celdas de tipo **Dato/Descripción** de las secciones ya no se editan directamente sobre la pantalla de la carpeta.
- Cada carpeta o subcarpeta tiene su propio botón **Descripciones** dentro de las acciones de Reporte.
- Al pulsarlo se abre una ventana independiente, siguiendo el mismo patrón del editor de Tabla.
- La ventana indica explícitamente el nombre de la carpeta que se está editando.
- **Guardar** persiste únicamente los valores de esa carpeta; otra subcarpeta mantiene valores independientes.
- **Cancelar** descarta cambios no guardados.
- **Limpiar** elimina las descripciones de esa carpeta sin modificar la plantilla global.
- En la pantalla de la carpeta se mantiene una vista previa no editable y un contador de campos llenados.
- La definición de títulos, líneas A/B/C/D, etiquetas y anchos sigue administrándose desde Panel de Control.

No se cambió el formato de persistencia porque `reportSectionValues` ya pertenece a cada nodo `Inspection`; por tanto el respaldo existente conserva compatibilidad.
