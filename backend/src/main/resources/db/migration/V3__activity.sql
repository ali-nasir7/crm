-- Activities, calls, tasks, meetings
create table activities (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    type varchar(24) not null,
    lead_id uuid references leads(id),
    company_id uuid references companies(id),
    contact_id uuid references contacts(id),
    deal_id uuid,
    client_id uuid,
    actor_id uuid,
    subject varchar(160),
    body text,
    metadata jsonb,
    occurred_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_act_org_lead on activities (organization_id, lead_id, occurred_at desc);
create index ix_act_org_created on activities (organization_id, created_at);
create index ix_act_org_type on activities (organization_id, type);

create table calls (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    lead_id uuid references leads(id),
    company_id uuid references companies(id),
    user_id uuid not null,
    direction varchar(12) not null,
    occurred_at timestamptz not null,
    duration_seconds integer,
    outcome varchar(24) not null,
    notes text,
    next_action varchar(255),
    follow_up_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_calls_org_created on calls (organization_id, created_at);
create index ix_calls_org_user on calls (organization_id, user_id, created_at);
create index ix_calls_lead on calls (lead_id);

create table tasks (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    title varchar(160) not null,
    description text,
    lead_id uuid references leads(id),
    company_id uuid references companies(id),
    contact_id uuid references contacts(id),
    task_type varchar(24) not null,
    assigned_user_id uuid not null,
    due_at timestamptz not null,
    priority varchar(8) not null,
    status varchar(12) not null,
    completed_at timestamptz,
    completion_note varchar(1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_tasks_org_assignee_status on tasks (organization_id, assigned_user_id, status);
create index ix_tasks_org_due on tasks (organization_id, due_at);

create table meetings (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    title varchar(160) not null,
    lead_id uuid references leads(id),
    company_id uuid references companies(id),
    owner_id uuid not null,
    participants jsonb,
    start_at timestamptz not null,
    duration_minutes integer not null default 30,
    meeting_link varchar(500),
    location varchar(255),
    notes text,
    status varchar(12) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_meetings_org_start on meetings (organization_id, start_at);
create index ix_meetings_lead on meetings (lead_id);
