create table organizations (
  id uuid primary key,
  name varchar(160) not null,
  status varchar(32) not null,
  created_at timestamptz not null
);

create table users (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  email varchar(320) not null,
  first_name varchar(80) not null,
  last_name varchar(80) not null,
  status varchar(32) not null,
  created_at timestamptz not null,
  constraint users_email_per_organization unique (organization_id, email)
);

create index users_organization_id_idx on users (organization_id);
