create table idempotency_records (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  action varchar(64) not null,
  idempotency_key varchar(128) not null,
  request_hash varchar(64) not null,
  response_status integer not null,
  response_body text not null,
  resource_id uuid not null,
  created_by uuid not null references users (id),
  created_at timestamptz not null,
  constraint idempotency_records_key_per_action unique (organization_id, action, idempotency_key),
  constraint idempotency_records_request_hash_sha256 check (char_length(request_hash) = 64)
);
