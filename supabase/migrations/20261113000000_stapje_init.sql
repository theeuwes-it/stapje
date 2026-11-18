-- Stapje: tabellen voor synchronisatie tussen Android en web.
-- Elke gebruiker ziet en wijzigt alleen zijn eigen rijen (Row Level Security).

create table if not exists public.day_entries (
    user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
    date            date not null,
    day_part        text check (day_part in ('HELE_DAG', 'OCHTEND', 'MIDDAG', 'NIET_GEWERKT')),
    hours           double precision,
    activities      text[] not null default '{}',
    lab_work        boolean not null default false,
    lab_hours       double precision not null default 0,
    lab_description text not null default '',
    note            text not null default '',
    pain_overall    smallint check (pain_overall between 0 and 10),
    back_pain       smallint check (back_pain between 0 and 10),
    energy          smallint check (energy between 0 and 10),
    mood            smallint check (mood between 0 and 10),
    -- Tijdstip van de laatste wijziging op het apparaat (ms sinds 1970); nieuwste wint.
    updated_at_ms   bigint not null,
    -- Verwijderde dagen blijven als markering bestaan, zodat andere apparaten ze ook verwijderen.
    deleted         boolean not null default false,
    primary key (user_id, date)
);

create table if not exists public.user_settings (
    user_id       uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    data          jsonb not null,
    updated_at_ms bigint not null
);

alter table public.day_entries enable row level security;
alter table public.user_settings enable row level security;

create policy "day_entries: eigen rijen lezen" on public.day_entries
    for select to authenticated using ((select auth.uid()) = user_id);
create policy "day_entries: eigen rijen toevoegen" on public.day_entries
    for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "day_entries: eigen rijen wijzigen" on public.day_entries
    for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "day_entries: eigen rijen verwijderen" on public.day_entries
    for delete to authenticated using ((select auth.uid()) = user_id);

create policy "user_settings: eigen rij lezen" on public.user_settings
    for select to authenticated using ((select auth.uid()) = user_id);
create policy "user_settings: eigen rij toevoegen" on public.user_settings
    for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "user_settings: eigen rij wijzigen" on public.user_settings
    for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "user_settings: eigen rij verwijderen" on public.user_settings
    for delete to authenticated using ((select auth.uid()) = user_id);
