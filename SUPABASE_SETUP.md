# Supabase — configuración Fases 2, 3 y 4

InspeccionPhotos sigue funcionando sin Supabase. La nube es una capa de respaldo/sincronización y nunca una condición para capturar.

## 1. Esquemas

En **SQL Editor** ejecuta, en orden:

1. `supabase/phase2_schema.sql`
2. `supabase/phase3_schema.sql`
3. `supabase/phase4_schema.sql`

Fase 2 crea proyectos/fotos, Storage privado y RLS.
Fase 3 añade análisis espacial.
Fase 4 añade `inspeccion_photos_phase4_data` para interiores, ráfagas y escaneos.

## 2. Configurar Android

En InspeccionPhotos:

**Configuración → Supabase privado**

Introduce:

- Project URL;
- publishable key o legacy anon key.

No introduzcas una `service_role`, `sb_secret_...` ni otra secret key. InspeccionPhotos rechaza claves `sb_secret_...` para evitar que una credencial administrativa quede dentro del APK.

## 3. Autenticación

En **Sincronización** puedes crear cuenta o iniciar sesión por correo/contraseña.

La contraseña no se guarda. Access/refresh tokens se cifran localmente con AES-GCM y Android Keystore.

## 4. Fotografías — Fase 2

Cada foto sigue:

`LOCAL_ONLY → PROCESSING → QUEUED → UPLOADED → VERIFIED`

- original y metadata primero local;
- SHA-256;
- reference/preview/thumbnail;
- tarea individual persistente;
- subida individual;
- descarga/stream remoto;
- SHA-256 remoto;
- `VERIFIED` sólo si coincide.

## 5. Espacial — Fase 3

`inspeccion_photos_spatial_analysis` guarda con RLS:

- `route.geojson`;
- GIS importado;
- inferencias edificio/foto;
- cobertura;
- resumen espacial.

## 6. Interiores/Ráfaga/Vídeo — Fase 4

`inspeccion_photos_phase4_data` guarda:

- `interior_route`;
- ráfagas;
- metadata de escaneos de vídeo;
- resumen Fase 4.

Los **frames representativos** extraídos del vídeo se suben como archivos individuales al bucket privado existente.

### MP4 bruto

El MP4 bruto permanece local en esta versión.

No se implementa una subida monolítica porque puede alcanzar varios GB. Para subirlo de forma fiable hace falta un protocolo reanudable/chunked con:

- IDs por bloque;
- reintentos por bloque;
- hash por bloque y/o hash final;
- reanudación después de cierre/red;
- confirmación antes de liberar almacenamiento local.

Hasta implementar ese transporte, `manifest.json` marca:

`rawVideoCloudTransport = LOCAL_ONLY_UNTIL_RESUMABLE_UPLOAD`

## 7. Privacidad

- bucket privado;
- RLS por usuario;
- sin enlaces públicos automáticos;
- originales locales no se eliminan por empezar una subida;
- interiores pueden contener información sensible y se mantienen privados salvo acción explícita del usuario.
