-- GymPulse Cloud-Backup: eine Zeile pro Nutzer mit dem kompletten JSON-Backup der App.
-- Im Supabase-Dashboard unter "SQL Editor" einmal ausführen. Das Skript ist wiederholbar.

create table if not exists public.backups (
    user_id    uuid        primary key default auth.uid() references auth.users (id) on delete cascade,
    payload    jsonb       not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

comment on table public.backups is 'GymPulse: letztes Cloud-Backup je Nutzer (JSON-Export der App).';

-- updated_at bei jeder Änderung serverseitig setzen, unabhängig von der Uhr des Geräts.
create or replace function public.backups_set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists backups_set_updated_at on public.backups;
create trigger backups_set_updated_at
    before insert or update on public.backups
    for each row execute function public.backups_set_updated_at();

-- Row Level Security: jedes Konto sieht und ändert nur die eigene Zeile.
alter table public.backups enable row level security;

drop policy if exists "backups_select_own" on public.backups;
create policy "backups_select_own" on public.backups
    for select to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists "backups_insert_own" on public.backups;
create policy "backups_insert_own" on public.backups
    for insert to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists "backups_update_own" on public.backups;
create policy "backups_update_own" on public.backups
    for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

drop policy if exists "backups_delete_own" on public.backups;
create policy "backups_delete_own" on public.backups
    for delete to authenticated
    using ((select auth.uid()) = user_id);

-- Anonyme Zugriffe (nur Publishable Key, ohne Anmeldung) bekommen keine Rechte.
revoke all on public.backups from anon;
grant select, insert, update, delete on public.backups to authenticated;
