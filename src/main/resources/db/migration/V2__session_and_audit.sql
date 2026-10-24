alter table users add column password_hash varchar(100);
alter table users add column role varchar(32);
alter table users alter column password_hash set not null;
alter table users alter column role set not null;

create table sessions (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  user_id uuid not null references users (id),
  token_hash varchar(64) not null,
  created_at timestamptz not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  constraint sessions_token_hash_unique unique (token_hash)
);

create index sessions_organization_id_idx on sessions (organization_id);

create table audit_events (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  actor_user_id uuid references users (id),
  action varchar(64) not null,
  target_type varchar(64) not null,
  target_id uuid,
  created_at timestamptz not null
);

create index audit_events_organization_id_created_at_idx
  on audit_events (organization_id, created_at);
