-- Hibernate schema validation expects the standard audit columns on every entity
-- extending BaseEntity. Add them (nullable, backfill-free) to the child tables.
alter table campaign_steps add column if not exists created_at timestamptz;
alter table campaign_steps add column if not exists updated_at timestamptz;
alter table campaign_steps add column if not exists created_by uuid;
alter table campaign_steps add column if not exists updated_by uuid;

alter table counters add column if not exists created_at timestamptz;
alter table counters add column if not exists updated_at timestamptz;
alter table counters add column if not exists created_by uuid;
alter table counters add column if not exists updated_by uuid;

alter table pipeline_stages add column if not exists created_at timestamptz;
alter table pipeline_stages add column if not exists updated_at timestamptz;
alter table pipeline_stages add column if not exists created_by uuid;
alter table pipeline_stages add column if not exists updated_by uuid;

alter table proposal_items add column if not exists created_at timestamptz;
alter table proposal_items add column if not exists updated_at timestamptz;
alter table proposal_items add column if not exists created_by uuid;
alter table proposal_items add column if not exists updated_by uuid;
