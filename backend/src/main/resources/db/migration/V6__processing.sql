-- Imports, bulk jobs, automations, AI usage log
create table import_jobs (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    file_name varchar(255) not null,
    file_type varchar(12),
    total_rows integer not null default 0,
    valid_rows integer not null default 0,
    duplicate_rows integer not null default 0,
    invalid_rows integer not null default 0,
    imported_rows integer not null default 0,
    status varchar(20) not null,
    mapping jsonb,
    duplicate_strategy varchar(20) not null,
    options jsonb,
    error_message varchar(1000),
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_imports_org on import_jobs (organization_id, created_at);

create table import_rows (
    id uuid primary key,
    job_id uuid not null references import_jobs(id) on delete cascade,
    organization_id uuid not null,
    row_number integer not null,
    raw jsonb,
    status varchar(12) not null,
    errors jsonb,
    duplicate_of_lead_id uuid,
    imported_lead_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_irows_job_status on import_rows (job_id, status);
create index ix_irows_job_row on import_rows (job_id, row_number);

create table bulk_jobs (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    job_type varchar(16) not null,
    params jsonb,
    total_count integer not null default 0,
    processed_count integer not null default 0,
    success_count integer not null default 0,
    failed_count integer not null default 0,
    status varchar(12) not null,
    error_message varchar(1000),
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_bulk_org on bulk_jobs (organization_id, created_at);

create table automations (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(120) not null,
    trigger_type varchar(24) not null,
    conditions jsonb,
    action varchar(16) not null,
    action_config jsonb,
    active boolean not null default true,
    run_count integer not null default 0,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_automations_org on automations (organization_id);

create table automation_runs (
    id uuid primary key,
    rule_id uuid not null references automations(id) on delete cascade,
    organization_id uuid not null,
    lead_id uuid,
    status varchar(12) not null,
    detail varchar(1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_autoruns_rule on automation_runs (rule_id);
create index ix_autoruns_lead on automation_runs (lead_id);

create table ai_actions (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    user_id uuid not null,
    lead_id uuid,
    use_case varchar(40) not null,
    provider varchar(24) not null,
    output jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_ai_org on ai_actions (organization_id, created_at);
