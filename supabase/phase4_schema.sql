-- Inspeccion Photos In Situ 2.3 / Fase 4
-- Ejecutar DESPUÉS de phase2_schema.sql y phase3_schema.sql.
-- Guarda metadatos de interiores, ráfagas y escaneo caminando.
-- El MP4 bruto NO se sube todavía: requiere transporte reanudable/chunked para no volver al problema del ZIP gigante.

alter table public.inspeccion_photos_surveys
    alter column schema_version set default '1.3';

create table if not exists public.inspeccion_photos_phase4_data (
    survey_id uuid primary key references public.inspeccion_photos_surveys(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    schema_version text not null default '1.3',
    interior_route jsonb not null default '[]'::jsonb,
    bursts jsonb not null default '[]'::jsonb,
    video_scans jsonb not null default '[]'::jsonb,
    summary jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

create index if not exists inspeccion_photos_phase4_data_user_id_idx
    on public.inspeccion_photos_phase4_data(user_id);

alter table public.inspeccion_photos_phase4_data enable row level security;
revoke all on table public.inspeccion_photos_phase4_data from anon, authenticated;
grant select, insert, update, delete on table public.inspeccion_photos_phase4_data to authenticated;

drop policy if exists "inspeccion_photos phase4 select own" on public.inspeccion_photos_phase4_data;
create policy "inspeccion_photos phase4 select own"
on public.inspeccion_photos_phase4_data for select to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos phase4 insert own" on public.inspeccion_photos_phase4_data;
create policy "inspeccion_photos phase4 insert own"
on public.inspeccion_photos_phase4_data for insert to authenticated
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos phase4 update own" on public.inspeccion_photos_phase4_data;
create policy "inspeccion_photos phase4 update own"
on public.inspeccion_photos_phase4_data for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos phase4 delete own" on public.inspeccion_photos_phase4_data;
create policy "inspeccion_photos phase4 delete own"
on public.inspeccion_photos_phase4_data for delete to authenticated
using ((select auth.uid()) = user_id);
