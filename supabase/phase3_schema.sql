-- Inspeccion Photos In Situ 2.2 / Fase 3
-- Ejecutar DESPUÉS de phase2_schema.sql.
-- Añade almacenamiento privado de análisis espacial por levantamiento.

alter table public.inspeccion_photos_surveys
    alter column schema_version set default '1.2';

create table if not exists public.inspeccion_photos_spatial_analysis (
    survey_id uuid primary key references public.inspeccion_photos_surveys(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    schema_version text not null default '1.2',
    route_geojson jsonb,
    buildings jsonb not null default '[]'::jsonb,
    building_inferences jsonb not null default '[]'::jsonb,
    coverage jsonb not null default '[]'::jsonb,
    summary jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

create index if not exists inspeccion_photos_spatial_analysis_user_id_idx
    on public.inspeccion_photos_spatial_analysis(user_id);

alter table public.inspeccion_photos_spatial_analysis enable row level security;

revoke all on table public.inspeccion_photos_spatial_analysis from anon, authenticated;
grant select, insert, update, delete on table public.inspeccion_photos_spatial_analysis to authenticated;

drop policy if exists "inspeccion_photos spatial select own" on public.inspeccion_photos_spatial_analysis;
create policy "inspeccion_photos spatial select own"
on public.inspeccion_photos_spatial_analysis for select to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos spatial insert own" on public.inspeccion_photos_spatial_analysis;
create policy "inspeccion_photos spatial insert own"
on public.inspeccion_photos_spatial_analysis for insert to authenticated
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos spatial update own" on public.inspeccion_photos_spatial_analysis;
create policy "inspeccion_photos spatial update own"
on public.inspeccion_photos_spatial_analysis for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos spatial delete own" on public.inspeccion_photos_spatial_analysis;
create policy "inspeccion_photos spatial delete own"
on public.inspeccion_photos_spatial_analysis for delete to authenticated
using ((select auth.uid()) = user_id);
