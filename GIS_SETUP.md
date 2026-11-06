# GIS en InspeccionPhotos — Fase 3

InspeccionPhotos puede trabajar sin GIS: `route.geojson`, el mapa de trayectoria, fotos, vectores de orientación y POIs siguen funcionando.

Para identificación automática de edificios y cobertura de fachadas, abre un levantamiento y usa **Importar GIS**.

El archivo debe ser GeoJSON `FeatureCollection` con geometrías `Polygon` o `MultiPolygon`.

Propiedades reconocidas para el nombre del edificio:

- `name`
- `building`
- `ref`
- `code`
- `nombre`

Propiedades opcionales para facultad/zona:

- `faculty`
- `facultad`
- `zone`
- `zona`

Ejemplo mínimo:

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id": "24E",
      "properties": {
        "name": "24E",
        "faculty": "FIMCP"
      },
      "geometry": {
        "type": "Polygon",
        "coordinates": [[
          [-79.96680, -2.14715],
          [-79.96645, -2.14715],
          [-79.96645, -2.14685],
          [-79.96680, -2.14685],
          [-79.96680, -2.14715]
        ]]
      }
    }
  ]
}
```

## Regla de confianza

InspeccionPhotos no asigna un edificio sólo porque esté cerca. Cruza:

1. posición de la foto;
2. heading/orientación;
3. huella GIS;
4. distancia;
5. precisión GPS;
6. continuidad de la secuencia fotográfica.

Si la confianza final es menor al umbral interno, la salida es **Edificio probable: Desconocido**.

## Supabase

Si ya ejecutaste `supabase/phase2_schema.sql`, ejecuta también:

`supabase/phase3_schema.sql`

La tabla `inspeccion_photos_spatial_analysis` queda protegida con RLS por usuario y guarda route GeoJSON, edificios, inferencias, cobertura y resumen espacial.
