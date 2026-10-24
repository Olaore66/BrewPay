create table collection_accounts (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  external_reference varchar(64) not null,
  clearing_account_id uuid not null,
  merchant_account_id uuid not null,
  created_by uuid not null references users (id),
  created_at timestamptz not null,
  constraint collection_accounts_reference_unique unique (external_reference),
  constraint collection_accounts_id_organization unique (id, organization_id),
  constraint collection_accounts_clearing_fk
    foreign key (clearing_account_id, organization_id)
    references ledger_accounts (id, organization_id),
  constraint collection_accounts_merchant_fk
    foreign key (merchant_account_id, organization_id)
    references ledger_accounts (id, organization_id)
);

create index collection_accounts_organization_id_created_at_idx
  on collection_accounts (organization_id, created_at);

create table collections (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  collection_account_id uuid not null,
  provider_event_id varchar(128) not null,
  amount_kobo bigint not null,
  journal_id uuid not null,
  created_at timestamptz not null,
  constraint collections_amount_positive check (amount_kobo > 0),
  constraint collections_event_per_organization unique (organization_id, provider_event_id),
  constraint collections_account_fk
    foreign key (collection_account_id, organization_id)
    references collection_accounts (id, organization_id),
  constraint collections_journal_fk
    foreign key (journal_id, organization_id)
    references journals (id, organization_id)
);

create index collections_organization_id_created_at_idx
  on collections (organization_id, created_at);
