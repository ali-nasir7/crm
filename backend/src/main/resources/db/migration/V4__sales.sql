-- Deals, proposals, clients, documents
create table deals (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    title varchar(160) not null,
    lead_id uuid references leads(id),
    company_id uuid references companies(id),
    contact_id uuid references contacts(id),
    owner_id uuid not null,
    pipeline_id uuid references pipelines(id),
    stage_id uuid references pipeline_stages(id),
    amount numeric(16,2),
    currency varchar(8),
    probability integer not null default 0,
    expected_close_date timestamptz,
    closed_at timestamptz,
    status varchar(8) not null,
    lost_reason varchar(255),
    products jsonb,
    notes text,
    client_id uuid,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_deals_org_stage on deals (organization_id, stage_id);
create index ix_deals_org_owner on deals (organization_id, owner_id, status);
create index ix_deals_org_created on deals (organization_id, created_at);

create table proposals (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    proposal_number varchar(24) not null,
    lead_id uuid references leads(id),
    deal_id uuid references deals(id),
    company_id uuid references companies(id),
    contact_id uuid references contacts(id),
    title varchar(160) not null,
    description text,
    status varchar(12) not null,
    currency varchar(8),
    discount_percent numeric(5,2),
    tax_percent numeric(5,2),
    valid_until timestamptz,
    terms text,
    sent_at timestamptz,
    viewed_at timestamptz,
    decided_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_proposals_org_created on proposals (organization_id, created_at);
create index ix_proposals_lead on proposals (lead_id);

create table proposal_items (
    id uuid primary key,
    proposal_id uuid not null references proposals(id) on delete cascade,
    name varchar(160) not null,
    description text,
    quantity numeric(12,2) not null default 1,
    unit_price numeric(16,2) not null default 0,
    position integer not null default 0
);
create index ix_pitems_proposal on proposal_items (proposal_id);

create table clients (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    company_id uuid not null references companies(id),
    primary_contact_id uuid references contacts(id),
    account_manager_id uuid references users(id),
    status varchar(16) not null,
    lifetime_value numeric(16,2),
    converted_from_lead_id uuid references leads(id),
    converted_at timestamptz not null,
    notes text,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_clients_org on clients (organization_id, deleted_at, status);
create index ix_clients_company on clients (company_id);

create table documents (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(255) not null,
    file_name varchar(255) not null,
    content_type varchar(128),
    size_bytes bigint not null,
    storage_key varchar(500) not null,
    lead_id uuid references leads(id),
    company_id uuid references companies(id),
    deal_id uuid references deals(id),
    proposal_id uuid references proposals(id),
    client_id uuid references clients(id),
    uploaded_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_docs_org on documents (organization_id, created_at);
create index ix_docs_lead on documents (lead_id);
create index ix_docs_client on documents (client_id);
