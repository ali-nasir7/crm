-- Platform & tenancy foundation
create table organizations (
    id uuid primary key,
    name varchar(120) not null,
    slug varchar(80) not null,
    status varchar(16) not null,
    settings jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_org_slug unique (slug)
);

create table users (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    email varchar(255) not null,
    password_hash varchar(100) not null,
    first_name varchar(80) not null,
    last_name varchar(80) not null,
    job_title varchar(80),
    phone varchar(32),
    status varchar(16) not null,
    super_admin boolean not null default false,
    timezone varchar(64),
    daily_targets jsonb,
    last_login_at timestamptz,
    failed_login_attempts integer not null default 0,
    locked_until timestamptz,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create unique index uk_users_email on users (lower(email)) where deleted_at is null;
create index ix_users_org on users (organization_id);

create table permissions (
    id uuid primary key,
    key varchar(64) not null unique,
    name varchar(255) not null,
    category varchar(32),
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);

create table roles (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    key varchar(48) not null,
    name varchar(64) not null,
    description varchar(255),
    data_scope varchar(8) not null,
    is_system boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_roles_org_key unique (organization_id, key)
);
create index ix_roles_org on roles (organization_id);

create table role_permissions (
    role_id uuid not null references roles(id) on delete cascade,
    permission_id uuid not null references permissions(id),
    primary key (role_id, permission_id)
);
create index ix_rp_role on role_permissions (role_id);
create index ix_rp_perm on role_permissions (permission_id);

create table user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role_id uuid not null references roles(id),
    primary key (user_id, role_id)
);
create index ix_ur_user on user_roles (user_id);
create index ix_ur_role on user_roles (role_id);

create table teams (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(80) not null,
    description varchar(255),
    manager_id uuid references users(id),
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_teams_org_name unique (organization_id, name)
);
create index ix_teams_org on teams (organization_id);

create table team_members (
    team_id uuid not null references teams(id) on delete cascade,
    user_id uuid not null references users(id),
    primary key (team_id, user_id)
);
create index ix_tm_team on team_members (team_id);
create index ix_tm_user on team_members (user_id);

create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    ip varchar(64),
    user_agent varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_rt_hash unique (token_hash)
);
create index ix_rt_user on refresh_tokens (user_id);

create table notifications (
    id uuid primary key,
    organization_id uuid not null,
    user_id uuid not null,
    type varchar(40) not null,
    title varchar(160) not null,
    body text,
    entity_type varchar(32),
    entity_id uuid,
    read_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_notif_user on notifications (user_id, created_at);
create index ix_notif_org on notifications (organization_id);

create table audit_logs (
    id uuid primary key,
    organization_id uuid not null,
    actor_id uuid,
    actor_email varchar(255),
    action varchar(48) not null,
    entity_type varchar(32),
    entity_id uuid,
    entity_label varchar(255),
    old_values jsonb,
    new_values jsonb,
    ip varchar(64),
    user_agent varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index ix_audit_org_created on audit_logs (organization_id, created_at);
create index ix_audit_entity on audit_logs (organization_id, entity_type, entity_id);
create index ix_audit_actor on audit_logs (actor_id);

create table counters (
    id uuid primary key,
    organization_id uuid not null,
    counter_key varchar(48) not null,
    value bigint not null default 0,
    constraint uk_counters_org_key unique (organization_id, counter_key)
);
