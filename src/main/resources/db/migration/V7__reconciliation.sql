create table reconciliation_runs (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  created_by uuid not null references users (id),
  created_at timestamptz not null,
  constraint reconciliation_runs_id_organization unique (id, organization_id)
);

create index reconciliation_runs_organization_id_created_at_idx
  on reconciliation_runs (organization_id, created_at);

create table reconciliation_findings (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  run_id uuid not null,
  code varchar(32) not null,
  subject_type varchar(16) not null,
  subject_id uuid,
  provider_reference varchar(128),
  provider_status varchar(16),
  our_status varchar(16),
  provider_amount_kobo bigint,
  our_amount_kobo bigint,
  created_at timestamptz not null,
  constraint reconciliation_findings_id_organization unique (id, organization_id),
  constraint reconciliation_findings_run_fk
    foreign key (run_id, organization_id)
    references reconciliation_runs (id, organization_id),
  constraint reconciliation_findings_code check (
    code in (
      'STATUS_MISMATCH',
      'AMOUNT_MISMATCH',
      'MISSING_ON_LEDGER',
      'MISSING_AT_PROVIDER'
    )
  ),
  constraint reconciliation_findings_subject_type check (
    subject_type in ('COLLECTION', 'TRANSFER')
  ),
  constraint reconciliation_findings_provider_status check (
    provider_status is null
    or provider_status in ('PENDING', 'SUCCESSFUL', 'FAILED', 'UNKNOWN')
  ),
  constraint reconciliation_findings_our_status check (
    our_status is null or our_status in ('PENDING', 'SUCCESSFUL', 'FAILED', 'UNKNOWN')
  ),
  constraint reconciliation_findings_provider_amount_positive check (
    provider_amount_kobo is null or provider_amount_kobo > 0
  ),
  constraint reconciliation_findings_our_amount_positive check (
    our_amount_kobo is null or our_amount_kobo > 0
  )
);

create unique index reconciliation_findings_subject_once
  on reconciliation_findings (organization_id, code, subject_type, subject_id)
  where subject_id is not null;

create unique index reconciliation_findings_provider_line_once
  on reconciliation_findings (organization_id, code, subject_type, provider_reference)
  where subject_id is null;

create index reconciliation_findings_organization_id_created_at_idx
  on reconciliation_findings (organization_id, created_at);
