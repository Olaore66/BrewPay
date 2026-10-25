alter table organizations add column kyc_status varchar(32);

update organizations
set kyc_status = 'NOT_SUBMITTED'
where kyc_status is null;

alter table organizations alter column kyc_status set not null;

alter table organizations
  add constraint organizations_kyc_status check (
    kyc_status in ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED')
  );

create table kyb_submissions (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  trading_name varchar(160) not null,
  business_type varchar(32) not null,
  registration_number varchar(40),
  address_line varchar(200) not null,
  state varchar(40) not null,
  category varchar(80) not null,
  kyc_status varchar(32) not null,
  created_by uuid not null references users (id),
  created_at timestamptz not null,
  constraint kyb_submissions_id_organization unique (id, organization_id),
  constraint kyb_submissions_business_type check (
    business_type in ('SOLE_PROPRIETOR', 'PARTNERSHIP', 'LIMITED_COMPANY')
  ),
  constraint kyb_submissions_kyc_status check (
    kyc_status in ('PENDING', 'APPROVED', 'REJECTED')
  ),
  constraint kyb_submissions_registration check (
    business_type = 'SOLE_PROPRIETOR'
    or char_length(registration_number) between 1 and 40
  )
);

create index kyb_submissions_organization_id_created_at_idx
  on kyb_submissions (organization_id, created_at);
