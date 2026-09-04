-- CRM core: tags, sources, pipelines, companies, contacts, leads
create table tags (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(48) not null,
    color varchar(9),
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_tags_org_name unique (organization_id, name)
);
create index ix_tags_org on tags (organization_id);

create table lead_sources (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    key varchar(32) not null,
    name varchar(64) not null,
    description varchar(255),
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_sources_org_key unique (organization_id, key)
);
create index ix_sources_org on lead_sources (organization_id);

create table pipelines (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(80) not null,
    description varchar(255),
    is_default boolean not null default false,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_pipelines_org on pipelines (organization_id);

create table pipeline_stages (
    id uuid primary key,
    pipeline_id uuid not null references pipelines(id) on delete cascade,
    name varchar(80) not null,
    position integer not null,
    type varchar(8) not null,
    probability integer not null default 0
);
create index ix_stages_pipeline on pipeline_stages (pipeline_id);

create table companies (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(160) not null,
    website varchar(255),
    industry varchar(80),
    description varchar(2000),
    phone varchar(32),
    email varchar(255),
    country varchar(64),
    city varchar(64),
    state varchar(64),
    address varchar(255),
    linkedin varchar(255),
    company_size varchar(32),
    annual_revenue varchar(32),
    owner_id uuid references users(id),
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_companies_org_name on companies (organization_id, deleted_at, name);
create index ix_companies_org_created on companies (organization_id, deleted_at, created_at);
create index ix_companies_org_website on companies (organization_id, lower(website));
create index ix_companies_org_name_lower on companies (organization_id, lower(name));

create table company_tags (
    company_id uuid not null references companies(id) on delete cascade,
    tag_id uuid not null references tags(id),
    primary key (company_id, tag_id)
);
create index ix_ct_company on company_tags (company_id);
create index ix_ct_tag on company_tags (tag_id);

create table contacts (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    company_id uuid references companies(id),
    first_name varchar(80) not null,
    last_name varchar(80) not null,
    job_title varchar(80),
    email varchar(255),
    secondary_email varchar(255),
    phone varchar(32),
    whatsapp varchar(32),
    linkedin varchar(255),
    owner_id uuid references users(id),
    is_primary boolean not null default false,
    notes varchar(2000),
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_contacts_org_company on contacts (organization_id, company_id);
create index ix_contacts_org_created on contacts (organization_id, deleted_at, created_at);
create index ix_contacts_org_email on contacts (organization_id, lower(email));

create table leads (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    business_name varchar(160) not null,
    first_name varchar(80),
    last_name varchar(80),
    job_title varchar(80),
    company_id uuid references companies(id),
    contact_id uuid references contacts(id),
    email varchar(255),
    secondary_email varchar(255),
    phone varchar(32),
    whatsapp varchar(32),
    website varchar(255),
    linkedin varchar(255),
    country varchar(64),
    state varchar(64),
    city varchar(64),
    address varchar(255),
    timezone varchar(64),
    industry varchar(80),
    business_type varchar(64),
    company_size varchar(32),
    employees_count integer,
    revenue_range varchar(32),
    custom_fields jsonb not null default '{}',
    status varchar(16) not null,
    score integer not null default 0,
    source_id uuid references lead_sources(id),
    pipeline_id uuid references pipelines(id),
    stage_id uuid references pipeline_stages(id),
    assigned_user_id uuid references users(id),
    last_contacted_at timestamptz,
    next_followup_at timestamptz,
    notes varchar(2000),
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_leads_org_status on leads (organization_id, deleted_at, status);
create index ix_leads_org_assignee on leads (organization_id, deleted_at, assigned_user_id);
create index ix_leads_org_stage on leads (organization_id, deleted_at, stage_id);
create index ix_leads_org_created on leads (organization_id, deleted_at, created_at desc);
create index ix_leads_org_followup on leads (organization_id, deleted_at, next_followup_at);
create index ix_leads_org_contacted on leads (organization_id, deleted_at, last_contacted_at);
create index ix_leads_org_score on leads (organization_id, deleted_at, score desc);
create index ix_leads_org_email on leads (organization_id, lower(email));
create index ix_leads_org_phone on leads (organization_id, phone);
create index ix_leads_org_website on leads (organization_id, lower(website));
create index ix_leads_org_linkedin on leads (organization_id, lower(linkedin));
create index ix_leads_org_business_name on leads (organization_id, lower(business_name));
create index ix_leads_custom_fields on leads using gin (custom_fields jsonb_path_ops);

create table lead_tags (
    lead_id uuid not null references leads(id) on delete cascade,
    tag_id uuid not null references tags(id),
    primary key (lead_id, tag_id)
);
create index ix_lt_lead on lead_tags (lead_id);
create index ix_lt_tag on lead_tags (tag_id);

create table lead_stage_history (
    id uuid primary key,
    lead_id uuid not null references leads(id) on delete cascade,
    organization_id uuid not null,
    from_stage_id uuid references pipeline_stages(id),
    to_stage_id uuid not null references pipeline_stages(id),
    changed_by uuid,
    entered_at timestamptz not null,
    left_at timestamptz,
    duration_seconds bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_lsh_lead on lead_stage_history (lead_id, entered_at);
create index ix_lsh_stage on lead_stage_history (to_stage_id);

create table saved_views (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(80) not null,
    owner_id uuid not null,
    is_shared boolean not null default false,
    filters jsonb not null,
    sort varchar(64),
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_views_org_owner on saved_views (organization_id, owner_id);

create table scoring_rules (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    criterion varchar(32) not null,
    operand varchar(255),
    points integer not null,
    label varchar(80) not null,
    active boolean not null default true,
    position integer not null default 0,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_scoring_org on scoring_rules (organization_id);

create table custom_field_defs (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    key varchar(48) not null,
    label varchar(80) not null,
    type varchar(16) not null,
    position integer not null default 0,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_cfd_org_key unique (organization_id, key)
);
create index ix_cfd_org on custom_field_defs (organization_id);

create table custom_field_options (
    def_id uuid not null references custom_field_defs(id) on delete cascade,
    option_value varchar(64)
);
create index ix_cfo_def on custom_field_options (def_id);
