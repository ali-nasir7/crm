-- V12: schema/permission alignment fixes
--
-- 1) email_templates.soft delete: the EmailTemplate entity carries
--    @SQLRestriction("deleted_at IS NULL") but V5 created the table with only
--    archived_at. Every Hibernate SELECT on email_templates therefore failed with
--    "column et1_0.deleted_at does not exist" (GET /api/v1/email-templates 500).
--    Add the column for BOTH fresh and existing databases (idempotent).
alter table email_templates add column if not exists deleted_at timestamptz;

-- 2) CSV lead import for SALES_MANAGER and SALES_REP system roles.
--    RoleFactory now grants these on new organizations; this backfills existing
--    organizations so UI and backend authorization stay in agreement.
--    MANAGER: + LEAD_IMPORT (already had IMPORT_VIEW)
--    REP:     + LEAD_IMPORT and IMPORT_VIEW (sees import history too)
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.key = 'LEAD_IMPORT'
where r.is_system = true and r.key in ('SALES_MANAGER', 'SALES_REP')
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.key = 'IMPORT_VIEW'
where r.is_system = true and r.key = 'SALES_REP'
on conflict do nothing;
