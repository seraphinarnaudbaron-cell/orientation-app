-- =====================================================================
--  Orientation-J.P. — installation de la base Supabase
--  À exécuter une fois dans : Supabase > SQL Editor.
--  Le script est rejouable (idempotent). Il crée ses propres tables
--  (runner_positions, app_events, admin_secret) et complète `beacons` ;
--  les autres tables existantes ne sont pas modifiées.
--
--  ⚠️  Avant de l'exécuter, remplace CHANGE_MOI par ton nouveau mot de
--  passe admin (à la fin du fichier). Ne le mets jamais dans le code de l'app.
-- =====================================================================

create extension if not exists pgcrypto with schema extensions;

-- ---------- Tables ----------------------------------------------------
create table if not exists public.runner_positions (
  username   text primary key check (char_length(username) between 1 and 30),
  latitude   double precision not null check (latitude between -90 and 90),
  longitude  double precision not null check (longitude between -180 and 180),
  updated_at timestamptz not null default now()
);

create table if not exists public.beacons (
  id          bigint generated always as identity primary key,
  name        text not null default '',
  latitude    double precision not null check (latitude between -90 and 90),
  longitude   double precision not null check (longitude between -180 and 180),
  pattern     text not null check (pattern ~ '^[01]{9}$'),
  description text not null default '',
  created_at  timestamptz not null default now()
);
-- Si la table beacons existait déjà sans colonne name :
alter table public.beacons add column if not exists name text not null default '';

create table if not exists public.app_events (
  id         bigint generated always as identity primary key,
  username   text,
  event      text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.admin_secret (
  id            int primary key default 1 check (id = 1),
  password_hash text not null
);

-- ---------- Row Level Security ---------------------------------------
alter table public.runner_positions    enable row level security;
alter table public.beacons      enable row level security;
alter table public.app_events    enable row level security;
alter table public.admin_secret enable row level security;

drop policy if exists "lecture positions récentes" on public.runner_positions;
create policy "lecture positions récentes" on public.runner_positions
  for select to anon, authenticated
  using (updated_at > now() - interval '5 minutes');

drop policy if exists "lecture balises" on public.beacons;
create policy "lecture balises" on public.beacons
  for select to anon, authenticated using (true);

-- Aucune politique d'écriture : toute écriture passe par les fonctions ci-dessous.
-- app_stats et admin_secret n'ont aucune politique : illisibles depuis l'app.

-- ---------- Fonctions utilisateur ------------------------------------
create or replace function public.upsert_position(p_username text, p_lat double precision, p_lon double precision)
returns void language plpgsql security definer set search_path = public as $$
begin
  if p_username is null or char_length(trim(p_username)) not between 1 and 30 then
    raise exception 'Nom invalide';
  end if;
  insert into public.runner_positions (username, latitude, longitude, updated_at)
  values (trim(p_username), p_lat, p_lon, now())
  on conflict (username) do update
    set latitude = excluded.latitude, longitude = excluded.longitude, updated_at = now();
end $$;

create or replace function public.clear_position(p_username text)
returns void language sql security definer set search_path = public as $$
  delete from public.runner_positions where username = trim(p_username);
$$;

create or replace function public.log_event(p_username text, p_event text)
returns void language sql security definer set search_path = public as $$
  insert into public.app_events (username, event)
  values (left(trim(p_username), 30), left(p_event, 40));
$$;

-- ---------- Fonctions admin (mot de passe vérifié côté serveur) ------
create or replace function public._admin_ok(p_password text)
returns boolean language plpgsql security definer set search_path = public, extensions as $$
declare ok boolean;
begin
  select (password_hash = extensions.crypt(coalesce(p_password, ''), password_hash))
    into ok from public.admin_secret where id = 1;
  if not coalesce(ok, false) then
    perform pg_sleep(1);  -- freine les essais en rafale
    return false;
  end if;
  return true;
end $$;

create or replace function public.admin_check(p_password text)
returns boolean language sql security definer set search_path = public as $$
  select public._admin_ok(p_password);
$$;

create or replace function public.admin_add_beacon(
  p_password text, p_name text, p_lat double precision, p_lon double precision,
  p_pattern text, p_description text)
returns bigint language plpgsql security definer set search_path = public as $$
declare new_id bigint;
begin
  if not public._admin_ok(p_password) then raise exception 'Mot de passe incorrect'; end if;
  insert into public.beacons (name, latitude, longitude, pattern, description)
  values (coalesce(p_name, ''), p_lat, p_lon, p_pattern, coalesce(p_description, ''))
  returning id into new_id;
  return new_id;
end $$;

create or replace function public.admin_delete_beacon(p_password text, p_id bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public._admin_ok(p_password) then raise exception 'Mot de passe incorrect'; end if;
  delete from public.beacons where id = p_id;
end $$;

create or replace function public.admin_stats(p_password text)
returns json language plpgsql security definer set search_path = public as $$
begin
  if not public._admin_ok(p_password) then raise exception 'Mot de passe incorrect'; end if;
  return json_build_object(
    'beacons',         (select count(*) from public.beacons),
    'active_now',      (select count(*) from public.runner_positions where updated_at > now() - interval '2 minutes'),
    'launches',        (select count(*) from public.app_events where event = 'launch'),
    'distinct_users',  (select count(distinct username) from public.app_events),
    'validations',     (select count(*) from public.app_events where event = 'beacon_validated')
  );
end $$;

-- Les fonctions admin_* / _admin_ok ne doivent pas être listées en clair pour tout le monde,
-- mais l'app (rôle anon) doit pouvoir les appeler : le mot de passe fait la sécurité.
grant execute on all functions in schema public to anon, authenticated;

-- ---------- Mot de passe admin ---------------------------------------
-- ⚠️ Remplace CHANGE_MOI avant d'exécuter. Pour le changer plus tard, relance seulement ce bloc.
insert into public.admin_secret (id, password_hash)
values (1, extensions.crypt('CHANGE_MOI', extensions.gen_salt('bf')))
on conflict (id) do update set password_hash = excluded.password_hash;

-- ---------- Exemple de balise (supprime-la si tu veux) ----------------
insert into public.beacons (name, latitude, longitude, pattern, description)
select 'Balise 1', 45.0620, 5.5675, '101010101', 'Près du pont, côté rivière'
where not exists (select 1 from public.beacons);
