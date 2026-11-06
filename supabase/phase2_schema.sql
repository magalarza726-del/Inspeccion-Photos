-- Inspeccion Photos In Situ 2.1 / Fase 2
-- Ejecutar una vez en Supabase SQL Editor.
-- El bucket queda PRIVADO. La app usa Auth + RLS; nunca requiere service_role en Android.

create table if not exists public.inspeccion_photos_surveys (
    id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    place text not null,
    schema_version text not null default '1.1',
    started_at_epoch_ms bigint not null,
    ended_at_epoch_ms bigint,
    status text not null,
    payload jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

create index if not exists inspeccion_photos_surveys_user_id_idx on public.inspeccion_photos_surveys(user_id);

create table if not exists public.inspeccion_photos_photos (
    id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    survey_id uuid not null references public.inspeccion_photos_surveys(id) on delete cascade,
    sequence integer not null,
    file_name text not null,
    sha256 text,
    storage_path text,
    sync_state text not null default 'QUEUED',
    metadata jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now(),
    unique (survey_id, sequence)
);

create index if not exists inspeccion_photos_photos_user_id_idx on public.inspeccion_photos_photos(user_id);
create index if not exists inspeccion_photos_photos_survey_id_idx on public.inspeccion_photos_photos(survey_id);
create index if not exists inspeccion_photos_photos_sha256_idx on public.inspeccion_photos_photos(sha256);

alter table public.inspeccion_photos_surveys enable row level security;
alter table public.inspeccion_photos_photos enable row level security;

revoke all on table public.inspeccion_photos_surveys from anon, authenticated;
revoke all on table public.inspeccion_photos_photos from anon, authenticated;
grant select, insert, update, delete on table public.inspeccion_photos_surveys to authenticated;
grant select, insert, update, delete on table public.inspeccion_photos_photos to authenticated;

drop policy if exists "inspeccion_photos surveys select own" on public.inspeccion_photos_surveys;
create policy "inspeccion_photos surveys select own"
on public.inspeccion_photos_surveys for select to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos surveys insert own" on public.inspeccion_photos_surveys;
create policy "inspeccion_photos surveys insert own"
on public.inspeccion_photos_surveys for insert to authenticated
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos surveys update own" on public.inspeccion_photos_surveys;
create policy "inspeccion_photos surveys update own"
on public.inspeccion_photos_surveys for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos surveys delete own" on public.inspeccion_photos_surveys;
create policy "inspeccion_photos surveys delete own"
on public.inspeccion_photos_surveys for delete to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos photos select own" on public.inspeccion_photos_photos;
create policy "inspeccion_photos photos select own"
on public.inspeccion_photos_photos for select to authenticated
using ((select auth.uid()) is not null and (select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos photos insert own" on public.inspeccion_photos_photos;
create policy "inspeccion_photos photos insert own"
on public.inspeccion_photos_photos for insert to authenticated
with check ((select auth.uid()) is not null and (select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos photos update own" on public.inspeccion_photos_photos;
create policy "inspeccion_photos photos update own"
on public.inspeccion_photos_photos for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "inspeccion_photos photos delete own" on public.inspeccion_photos_photos;
create policy "inspeccion_photos photos delete own"
on public.inspeccion_photos_photos for delete to authenticated
using ((select auth.uid()) = user_id);

-- Bucket privado. Si ya existe, sólo se fuerza public=false.
insert into storage.buckets (id, name, public)
values ('inspeccion_photos-originals', 'inspeccion_photos-originals', false)
on conflict (id) do update set public = false;

drop policy if exists "inspeccion_photos storage select own" on storage.objects;
create policy "inspeccion_photos storage select own"
on storage.objects for select to authenticated
using (
    bucket_id = 'inspeccion_photos-originals'
    and owner_id = (select auth.uid()::text)
);

drop policy if exists "inspeccion_photos storage insert own folder" on storage.objects;
create policy "inspeccion_photos storage insert own folder"
on storage.objects for insert to authenticated
with check (
    bucket_id = 'inspeccion_photos-originals'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

drop policy if exists "inspeccion_photos storage update own" on storage.objects;
create policy "inspeccion_photos storage update own"
on storage.objects for update to authenticated
using (
    bucket_id = 'inspeccion_photos-originals'
    and owner_id = (select auth.uid()::text)
)
with check (
    bucket_id = 'inspeccion_photos-originals'
    and owner_id = (select auth.uid()::text)
);

drop policy if exists "inspeccion_photos storage delete own" on storage.objects;
create policy "inspeccion_photos storage delete own"
on storage.objects for delete to authenticated
using (
    bucket_id = 'inspeccion_photos-originals'
    and owner_id = (select auth.uid()::text)
);
