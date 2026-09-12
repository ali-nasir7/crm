-- V9: Calling devices + call-state tracking, internal team chat, onboarding password flow.
-- All changes are additive: no existing column is dropped or retyped, existing APIs unchanged.

-- 1. Onboarding: users created by admin without an explicit password get a temp password
--    that must be changed before the account is usable.
alter table users add column if not exists must_change_password boolean not null default false;

-- 2. Calling devices (user-specific Android bridge endpoints)
create table if not exists calling_devices (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    user_id uuid not null references users(id),
    device_name varchar(80) not null,
    phone_number varchar(32),
    platform varchar(24),
    status varchar(16) not null default 'OFFLINE',
    last_seen_at timestamptz,
    is_default boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index if not exists ix_calling_devices_user on calling_devices (user_id);
create index if not exists ix_calling_devices_org on calling_devices (organization_id);

-- 3. Live call state on the existing calls table (legacy rows keep status ENDED)
alter table calls add column if not exists device_id uuid;
alter table calls add column if not exists status varchar(16) not null default 'ENDED';
alter table calls add column if not exists started_at timestamptz;
alter table calls add column if not exists answered_at timestamptz;
alter table calls add column if not exists ended_at timestamptz;
alter table calls add column if not exists provider_ref varchar(64);
alter table calls add column if not exists contact_id uuid;
create index if not exists ix_calls_provider_ref on calls (provider_ref);

-- 4. Internal team chat
create table if not exists conversations (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    type varchar(16) not null default 'DIRECT',
    last_message_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index if not exists ix_conversations_org on conversations (organization_id);

create table if not exists conversation_participants (
    id uuid primary key,
    conversation_id uuid not null references conversations(id) on delete cascade,
    user_id uuid not null references users(id),
    last_read_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid,
    constraint uk_conv_participant unique (conversation_id, user_id)
);
create index if not exists ix_conv_participants_user on conversation_participants (user_id);

create table if not exists chat_messages (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    conversation_id uuid not null references conversations(id) on delete cascade,
    sender_id uuid not null references users(id),
    body varchar(4000) not null,
    lead_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    created_by uuid,
    updated_by uuid
);
create index if not exists ix_chat_messages_conv on chat_messages (conversation_id, created_at);
