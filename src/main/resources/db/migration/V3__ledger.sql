create table ledger_accounts (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  code varchar(32) not null,
  name varchar(160) not null,
  type varchar(32) not null,
  created_at timestamptz not null,
  constraint ledger_accounts_code_per_organization unique (organization_id, code),
  constraint ledger_accounts_id_organization unique (id, organization_id),
  constraint ledger_accounts_type check (type in ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'))
);

create index ledger_accounts_organization_id_created_at_idx
  on ledger_accounts (organization_id, created_at);

create table journals (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  description varchar(200) not null,
  currency varchar(3) not null,
  reverses_journal_id uuid,
  created_at timestamptz not null,
  created_by uuid not null references users (id),
  constraint journals_id_organization unique (id, organization_id),
  constraint journals_currency_ngn check (currency = 'NGN'),
  constraint journals_reverses_same_organization
    foreign key (reverses_journal_id, organization_id)
    references journals (id, organization_id)
);

create unique index journals_one_reversal
  on journals (reverses_journal_id)
  where reverses_journal_id is not null;

create index journals_organization_id_created_at_idx
  on journals (organization_id, created_at);

create table journal_lines (
  id uuid primary key,
  journal_id uuid not null,
  organization_id uuid not null,
  account_id uuid not null,
  direction varchar(8) not null,
  amount_kobo bigint not null,
  position integer not null,
  constraint journal_lines_amount_positive check (amount_kobo > 0),
  constraint journal_lines_direction check (direction in ('DEBIT', 'CREDIT')),
  constraint journal_lines_position_unique unique (journal_id, position),
  constraint journal_lines_journal_fk
    foreign key (journal_id, organization_id)
    references journals (id, organization_id),
  constraint journal_lines_account_fk
    foreign key (account_id, organization_id)
    references ledger_accounts (id, organization_id)
);

create index journal_lines_journal_id_idx
  on journal_lines (journal_id, organization_id);
