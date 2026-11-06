# Inspeccion Photos v9 — jerarquía, persistencia, PDFs y editor

## Cambios principales

- Árbol de carpetas recursivo con **hasta 7 subniveles** bajo cada carpeta raíz.
- Cada carpeta configura de forma independiente:
  - permitir fotos;
  - permitir subcarpetas;
  - vista Galería o Lista.
- Reorganización de carpetas mediante selección múltiple y movimiento a cualquier destino válido.
- Validación para impedir ciclos y superar 7 subniveles.
- Carpetas contraíbles/expandibles, incluida acción Contraer/Expandir todas las carpetas del nivel visible.
- Migración automática de los códigos históricos del APK a subcarpetas del nuevo árbol.
- Persistencia de navegación con `rememberSaveable`: al rotar el teléfono se mantiene la sección y carpeta abiertas.
- Persistencia del destino de cámara externa durante recreación de Activity.
- Cámara configurable:
  - preguntar Cámara/Galería o abrir Cámara directamente;
  - preguntar o no por el editor tras capturar;
  - modo una foto por vez o sesión continua CameraX.
- Sesión continua CameraX mantiene la cámara abierta y agrega todas las fotografías al finalizar.
- Editor integrado sin dependencias externas:
  - rotación de 90°;
  - recorte central 50–100%;
  - brillo;
  - contraste;
  - nitidez;
  - restauración del original cuando existe copia protegida.
- El original de la primera edición se conserva en `photos/originals/`.
- Exportación genera **cuatro PDFs automáticamente**:
  - 1 figura por fila;
  - 2 figuras por fila;
  - 3 figuras por fila;
  - 4 figuras por fila.
- Los cuatro PDFs se incluyen en el ZIP y también se copian a Descargas.
- ZIP mantiene empaquetado rápido sin recomprimir JPG/PDF.
- El JSON clásico pasa a formato `inspeccion-photos`, versión 3, con árbol recursivo y migración desde `codes`.

## Previews

Además de las previews históricas, abrir:

`app/src/main/java/ec/espol/inspeccionphotos/ui/HierarchyPreviews.kt`

Incluye una preview horizontal de raíz jerárquica y una preview vertical de carpeta interna.

## Nota de compilación

El núcleo puro de `Models.kt` + `InspectionTree.kt` fue compilado con `kotlinc` en el entorno de trabajo. El build Android/Compose completo requiere Android Studio porque el wrapper necesita Gradle 9.3.1 y este entorno no dispone de acceso de red para descargar la distribución.
