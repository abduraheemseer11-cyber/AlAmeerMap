-- Al Ameer Map: run this once in Supabase > SQL Editor.

create table if not exists public.markers (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null default auth.uid() references auth.users(id) on delete cascade,
  modem_number   text not null default '',
  network_number text not null default '',
  modem_type     text not null default '',
  device_ip      text not null default '',
  username       text not null default '',
  password_enc   text not null default '',   -- AES-GCM ciphertext produced by the app
  client_name    text not null default '',
  client_phone   text not null default '',
  latitude       double precision not null check (latitude  between -90  and 90),
  longitude      double precision not null check (longitude between -180 and 180),
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

create index if not exists markers_user_idx         on public.markers (user_id);
create index if not exists markers_user_network_idx on public.markers (user_id, network_number);

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

drop trigger if exists markers_set_updated_at on public.markers;
create trigger markers_set_updated_at
  before update on public.markers
  for each row execute function public.set_updated_at();

-- Row Level Security: every user sees and changes ONLY their own rows.
alter table public.markers enable row level security;

revoke all on public.markers from anon;
grant select, insert, update, delete on public.markers to authenticated;

drop policy if exists "markers_select_own" on public.markers;
drop policy if exists "markers_insert_own" on public.markers;
drop policy if exists "markers_update_own" on public.markers;
drop policy if exists "markers_delete_own" on public.markers;

create policy "markers_select_own" on public.markers
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy "markers_insert_own" on public.markers
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy "markers_update_own" on public.markers
  for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy "markers_delete_own" on public.markers
  for delete to authenticated
  using ((select auth.uid()) = user_id);
