# Auditoría técnica — InspeccionPhotos In Situ 2.1 / Fase 2

## Base conservada

Esta versión es una evolución de Fase 1, no una reescritura. Se conservan cámara, GPS, orientación, trayectoria, recuperación de capturas, inspecciones heredadas, ZIP manual, previews de Android Studio y el mismo package `ec.espol.inspeccionphotos`.

## Integridad local

Después de confirmar original + metadata, el trabajo pesado se mueve a WorkManager:

- SHA-256 del original
- reference ~1920 px
- preview ~960 px
- thumbnail ~320 px
- cola de sincronización persistente

El original nunca se recomprime ni modifica.

## Cola offline

Cada foto tiene un archivo independiente `sync/queue/<photoId>.json` con intentos, próximo reintento y último error. La caída de una subida no reinicia las demás.

## Sincronización

Se añadió cliente REST nativo de Supabase sin SDK adicional para no inflar Gradle. Sólo se añadió WorkManager 2.10.3, dependencia que ya existía en el proyecto SKC suministrado como referencia de sincronización rápida.

Flujo de integridad remota:

1. subir original;
2. persistir estado UPLOADED;
3. leer el objeto remoto como stream;
4. recalcular SHA-256;
5. comparar con hash local;
6. si difiere, re-subir una vez esa unidad;
7. sólo marcar VERIFIED tras coincidencia;
8. persistir metadata verificada en Postgres.

## Credenciales

- URL y publishable/anon key: SharedPreferences de configuración.
- Contraseña: nunca persistida.
- Access/refresh token: AES-GCM con clave Android Keystore.
- service_role/secret key: no requerida ni permitida como configuración de cliente.

## Migración

`schemaVersion` actual: `1.1`.

Los archivos 1.0 se decodifican con defaults y `migratePhase2()` actualiza session/photo metadata sin borrar originales.

## Pendiente para Fase 3

- mapa de recorrido;
- `route.geojson`;
- cobertura geométrica;
- identificación automática de edificios/GIS.

No se añadió ARCore, Google Maps ni Supabase SDK en Fase 2.
