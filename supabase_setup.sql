-- supabase_setup.sql
create table public.positions (
  user_id text primary key,
  username text,
  latitude double precision,
  longitude double precision,
  updated_at timestamptz
);
grant select, insert, update on public.positions to anon;
