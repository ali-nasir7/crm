-- Email accounts, templates, messages, suppressions, campaigns
create table email_accounts (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    user_id uuid not null,
    provider varchar(8) not null,
    display_name varchar(120),
    email varchar(255) not null,
    smtp_host varchar(255),
    smtp_port integer,
    smtp_encryption varchar(12),
    smtp_username varchar(255),
    smtp_password_enc varchar(1000),
    status varchar(12) not null,
    verified_at timestamptz,
    daily_limit integer not null default 200,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_eaccounts_org_user on email_accounts (organization_id, user_id);

create table email_templates (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(120) not null,
    subject varchar(255) not null,
    body_html text,
    body_text text,
    category varchar(24),
    active boolean not null default true,
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_templates_org on email_templates (organization_id);

create table emails (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    account_id uuid references email_accounts(id),
    user_id uuid,
    lead_id uuid references leads(id),
    company_id uuid references companies(id),
    contact_id uuid references contacts(id),
    campaign_id uuid,
    direction varchar(8) not null,
    from_email varchar(255) not null,
    to_emails jsonb not null,
    cc_emails jsonb,
    subject varchar(255),
    body_html text,
    body_text text,
    status varchar(8) not null,
    error_message varchar(1000),
    tracking_id varchar(36) not null,
    provider_message_id varchar(255),
    sent_at timestamptz,
    opened_at timestamptz,
    open_count integer not null default 0,
    replied_at timestamptz,
    bounced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_emails_tracking unique (tracking_id)
);
create index ix_emails_org_created on emails (organization_id, created_at desc);
create index ix_emails_lead on emails (lead_id);
create index ix_emails_campaign on emails (campaign_id);

create table suppressions (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    email varchar(255) not null,
    reason varchar(16) not null,
    note varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_suppressions_org_email unique (organization_id, email)
);
create index ix_suppressions_org on suppressions (organization_id);

create table campaigns (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(120) not null,
    description varchar(1000),
    account_id uuid references email_accounts(id),
    status varchar(12) not null,
    scheduled_at timestamptz,
    total_recipients integer not null default 0,
    sent_count integer not null default 0,
    open_count integer not null default 0,
    reply_count integer not null default 0,
    bounce_count integer not null default 0,
    unsubscribe_count integer not null default 0,
    completed_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_campaigns_org on campaigns (organization_id, status);

create table campaign_steps (
    id uuid primary key,
    campaign_id uuid not null references campaigns(id) on delete cascade,
    position integer not null,
    template_id uuid not null,
    delay_days integer not null default 0
);
create index ix_csteps_campaign on campaign_steps (campaign_id);

create table campaign_recipients (
    id uuid primary key,
    campaign_id uuid not null references campaigns(id) on delete cascade,
    organization_id uuid not null,
    lead_id uuid not null,
    email varchar(255) not null,
    status varchar(16) not null,
    current_step integer,
    next_send_at timestamptz,
    last_email_id uuid,
    error_message varchar(1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_crec_worker on campaign_recipients (campaign_id, status, next_send_at);
create index ix_crec_lead on campaign_recipients (lead_id);
