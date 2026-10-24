create table beneficiaries (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  destination_account_number varchar(10) not null,
  destination_bank_code varchar(6) not null,
  account_name varchar(200) not null,
  created_by uuid not null references users (id),
  created_at timestamptz not null,
  constraint beneficiaries_id_organization unique (id, organization_id),
  constraint beneficiaries_account_per_organization unique (
    organization_id,
    destination_account_number,
    destination_bank_code
  ),
  constraint beneficiaries_account_number check (
    destination_account_number ~ '^[0-9]{10}$'
  ),
  constraint beneficiaries_bank_code check (destination_bank_code ~ '^[0-9]{3,6}$'),
  constraint beneficiaries_name_present check (char_length(account_name) between 1 and 200)
);

create index beneficiaries_organization_id_created_at_idx
  on beneficiaries (organization_id, created_at);
