create table transfers (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  clearing_account_id uuid not null,
  merchant_account_id uuid not null,
  amount_kobo bigint not null,
  destination_account_number varchar(10) not null,
  destination_bank_code varchar(6) not null,
  status varchar(16) not null,
  provider_reference varchar(64),
  journal_id uuid,
  created_by uuid not null references users (id),
  created_at timestamptz not null,
  constraint transfers_amount_positive check (amount_kobo > 0),
  constraint transfers_status check (status in ('PENDING', 'SUCCESSFUL', 'FAILED', 'UNKNOWN')),
  constraint transfers_id_organization unique (id, organization_id),
  constraint transfers_clearing_fk
    foreign key (clearing_account_id, organization_id)
    references ledger_accounts (id, organization_id),
  constraint transfers_merchant_fk
    foreign key (merchant_account_id, organization_id)
    references ledger_accounts (id, organization_id),
  constraint transfers_journal_fk
    foreign key (journal_id, organization_id)
    references journals (id, organization_id)
);

create index transfers_organization_id_created_at_idx
  on transfers (organization_id, created_at);
