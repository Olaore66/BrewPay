create table account_name_enquiries (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  destination_account_number varchar(10) not null,
  destination_bank_code varchar(6) not null,
  account_name varchar(200) not null,
  created_by uuid not null references users (id),
  created_at timestamptz not null,
  constraint account_name_enquiries_id_organization unique (id, organization_id),
  constraint account_name_enquiries_account_number check (
    destination_account_number ~ '^[0-9]{10}$'
  ),
  constraint account_name_enquiries_bank_code check (
    destination_bank_code ~ '^[0-9]{3,6}$'
  ),
  constraint account_name_enquiries_name_present check (
    char_length(account_name) between 1 and 200
  )
);

create index account_name_enquiries_organization_id_created_at_idx
  on account_name_enquiries (organization_id, created_at);
