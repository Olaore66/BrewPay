alter table users add column kyc_status varchar(32);

update users
set kyc_status = 'NOT_SUBMITTED'
where kyc_status is null;

alter table users alter column kyc_status set not null;

alter table users
  add constraint users_kyc_status check (
    kyc_status in ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED')
  );

create table kyc_submissions (
  id uuid primary key,
  organization_id uuid not null references organizations (id),
  user_id uuid not null references users (id),
  date_of_birth date not null,
  phone varchar(16) not null,
  identification_number varchar(11) not null,
  kyc_status varchar(32) not null,
  created_at timestamptz not null,
  constraint kyc_submissions_id_organization unique (id, organization_id),
  constraint kyc_submissions_phone check (char_length(phone) between 8 and 16),
  constraint kyc_submissions_identification check (
    identification_number ~ '^[0-9]{11}$'
  ),
  constraint kyc_submissions_kyc_status check (
    kyc_status in ('PENDING', 'APPROVED', 'REJECTED')
  )
);

create index kyc_submissions_organization_user_created_at_idx
  on kyc_submissions (organization_id, user_id, created_at);
