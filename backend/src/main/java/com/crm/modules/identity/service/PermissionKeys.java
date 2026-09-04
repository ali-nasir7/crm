package com.crm.modules.identity.service;

import java.util.List;

/** Canonical permission keys. Seeded into `permissions` by V7 migration. */
public final class PermissionKeys {
    private PermissionKeys() {}

    public static final String LEAD_VIEW = "LEAD_VIEW", LEAD_CREATE = "LEAD_CREATE", LEAD_UPDATE = "LEAD_UPDATE",
        LEAD_DELETE = "LEAD_DELETE", LEAD_ASSIGN = "LEAD_ASSIGN", LEAD_EXPORT = "LEAD_EXPORT",
        LEAD_CONVERT = "LEAD_CONVERT", LEAD_IMPORT = "LEAD_IMPORT", IMPORT_VIEW = "IMPORT_VIEW";

    public static final String COMPANY_VIEW = "COMPANY_VIEW", COMPANY_CREATE = "COMPANY_CREATE",
        COMPANY_UPDATE = "COMPANY_UPDATE", COMPANY_DELETE = "COMPANY_DELETE";

    public static final String CONTACT_VIEW = "CONTACT_VIEW", CONTACT_CREATE = "CONTACT_CREATE",
        CONTACT_UPDATE = "CONTACT_UPDATE", CONTACT_DELETE = "CONTACT_DELETE";

    public static final String CALL_VIEW = "CALL_VIEW", CALL_CREATE = "CALL_CREATE";

    public static final String TASK_VIEW = "TASK_VIEW", TASK_CREATE = "TASK_CREATE",
        TASK_UPDATE = "TASK_UPDATE", TASK_DELETE = "TASK_DELETE";

    public static final String MEETING_VIEW = "MEETING_VIEW", MEETING_CREATE = "MEETING_CREATE",
        MEETING_UPDATE = "MEETING_UPDATE", MEETING_DELETE = "MEETING_DELETE";

    public static final String EMAIL_VIEW = "EMAIL_VIEW", EMAIL_SEND = "EMAIL_SEND",
        EMAIL_ACCOUNT_VIEW = "EMAIL_ACCOUNT_VIEW", EMAIL_ACCOUNT_CREATE = "EMAIL_ACCOUNT_CREATE",
        EMAIL_ACCOUNT_UPDATE = "EMAIL_ACCOUNT_UPDATE", EMAIL_ACCOUNT_DELETE = "EMAIL_ACCOUNT_DELETE";

    public static final String TEMPLATE_VIEW = "TEMPLATE_VIEW", TEMPLATE_CREATE = "TEMPLATE_CREATE",
        TEMPLATE_UPDATE = "TEMPLATE_UPDATE", TEMPLATE_DELETE = "TEMPLATE_DELETE";

    public static final String CAMPAIGN_VIEW = "CAMPAIGN_VIEW", CAMPAIGN_CREATE = "CAMPAIGN_CREATE",
        CAMPAIGN_UPDATE = "CAMPAIGN_UPDATE", CAMPAIGN_SEND = "CAMPAIGN_SEND";

    public static final String DEAL_VIEW = "DEAL_VIEW", DEAL_CREATE = "DEAL_CREATE",
        DEAL_UPDATE = "DEAL_UPDATE", DEAL_DELETE = "DEAL_DELETE";

    public static final String PROPOSAL_VIEW = "PROPOSAL_VIEW", PROPOSAL_CREATE = "PROPOSAL_CREATE",
        PROPOSAL_UPDATE = "PROPOSAL_UPDATE", PROPOSAL_DELETE = "PROPOSAL_DELETE", PROPOSAL_SEND = "PROPOSAL_SEND";

    public static final String CLIENT_VIEW = "CLIENT_VIEW", CLIENT_UPDATE = "CLIENT_UPDATE";

    public static final String DOCUMENT_VIEW = "DOCUMENT_VIEW", DOCUMENT_CREATE = "DOCUMENT_CREATE",
        DOCUMENT_DELETE = "DOCUMENT_DELETE";

    public static final String PIPELINE_VIEW = "PIPELINE_VIEW", PIPELINE_UPDATE = "PIPELINE_UPDATE";

    public static final String TAG_VIEW = "TAG_VIEW", TAG_UPDATE = "TAG_UPDATE";
    public static final String SOURCE_VIEW = "SOURCE_VIEW", SOURCE_UPDATE = "SOURCE_UPDATE";

    public static final String SCORING_VIEW = "SCORING_VIEW", SCORING_UPDATE = "SCORING_UPDATE";
    public static final String AUTOMATION_VIEW = "AUTOMATION_VIEW", AUTOMATION_UPDATE = "AUTOMATION_UPDATE";
    public static final String AI_USE = "AI_USE";
    public static final String REPORT_VIEW = "REPORT_VIEW";
    public static final String AUDIT_VIEW = "AUDIT_VIEW";

    public static final String USER_VIEW = "USER_VIEW", USER_CREATE = "USER_CREATE",
        USER_UPDATE = "USER_UPDATE", USER_DELETE = "USER_DELETE";

    public static final String ROLE_VIEW = "ROLE_VIEW", ROLE_CREATE = "ROLE_CREATE",
        ROLE_UPDATE = "ROLE_UPDATE", ROLE_DELETE = "ROLE_DELETE";

    public static final String TEAM_VIEW = "TEAM_VIEW", TEAM_CREATE = "TEAM_CREATE",
        TEAM_UPDATE = "TEAM_UPDATE", TEAM_DELETE = "TEAM_DELETE";

    public static final String ORG_VIEW = "ORG_VIEW", ORG_UPDATE = "ORG_UPDATE";
    public static final String SETTINGS_VIEW = "SETTINGS_VIEW", SETTINGS_UPDATE = "SETTINGS_UPDATE";

    /** Ordered catalogue mirrored by the V7 Flyway migration. */
    public static List<String[]> ALL() {
        return List.of(
            new String[]{"LEAD_VIEW", "View leads", "Leads"}, new String[]{"LEAD_CREATE", "Create leads", "Leads"},
            new String[]{"LEAD_UPDATE", "Update leads", "Leads"}, new String[]{"LEAD_DELETE", "Delete leads", "Leads"},
            new String[]{"LEAD_ASSIGN", "Assign leads", "Leads"}, new String[]{"LEAD_EXPORT", "Export leads", "Leads"},
            new String[]{"LEAD_CONVERT", "Convert leads to clients", "Leads"}, new String[]{"LEAD_IMPORT", "Import leads", "Leads"},
            new String[]{"IMPORT_VIEW", "View import history", "Leads"},
            new String[]{"COMPANY_VIEW", "View companies", "Companies"}, new String[]{"COMPANY_CREATE", "Create companies", "Companies"},
            new String[]{"COMPANY_UPDATE", "Update companies", "Companies"}, new String[]{"COMPANY_DELETE", "Delete companies", "Companies"},
            new String[]{"CONTACT_VIEW", "View contacts", "Contacts"}, new String[]{"CONTACT_CREATE", "Create contacts", "Contacts"},
            new String[]{"CONTACT_UPDATE", "Update contacts", "Contacts"}, new String[]{"CONTACT_DELETE", "Delete contacts", "Contacts"},
            new String[]{"CALL_VIEW", "View calls", "Calls"}, new String[]{"CALL_CREATE", "Log calls", "Calls"},
            new String[]{"TASK_VIEW", "View tasks", "Tasks"}, new String[]{"TASK_CREATE", "Create tasks", "Tasks"},
            new String[]{"TASK_UPDATE", "Update tasks", "Tasks"}, new String[]{"TASK_DELETE", "Delete tasks", "Tasks"},
            new String[]{"MEETING_VIEW", "View meetings", "Meetings"}, new String[]{"MEETING_CREATE", "Create meetings", "Meetings"},
            new String[]{"MEETING_UPDATE", "Update meetings", "Meetings"}, new String[]{"MEETING_DELETE", "Delete meetings", "Meetings"},
            new String[]{"EMAIL_VIEW", "View emails", "Email"}, new String[]{"EMAIL_SEND", "Send emails", "Email"},
            new String[]{"EMAIL_ACCOUNT_VIEW", "View email accounts", "Email"}, new String[]{"EMAIL_ACCOUNT_CREATE", "Connect email accounts", "Email"},
            new String[]{"EMAIL_ACCOUNT_UPDATE", "Update email accounts", "Email"}, new String[]{"EMAIL_ACCOUNT_DELETE", "Remove email accounts", "Email"},
            new String[]{"TEMPLATE_VIEW", "View email templates", "Email"}, new String[]{"TEMPLATE_CREATE", "Create email templates", "Email"},
            new String[]{"TEMPLATE_UPDATE", "Update email templates", "Email"}, new String[]{"TEMPLATE_DELETE", "Delete email templates", "Email"},
            new String[]{"CAMPAIGN_VIEW", "View campaigns", "Campaigns"}, new String[]{"CAMPAIGN_CREATE", "Create campaigns", "Campaigns"},
            new String[]{"CAMPAIGN_UPDATE", "Update campaigns", "Campaigns"}, new String[]{"CAMPAIGN_SEND", "Start/stop campaigns", "Campaigns"},
            new String[]{"DEAL_VIEW", "View deals", "Sales"}, new String[]{"DEAL_CREATE", "Create deals", "Sales"},
            new String[]{"DEAL_UPDATE", "Update deals", "Sales"}, new String[]{"DEAL_DELETE", "Delete deals", "Sales"},
            new String[]{"PROPOSAL_VIEW", "View proposals", "Sales"}, new String[]{"PROPOSAL_CREATE", "Create proposals", "Sales"},
            new String[]{"PROPOSAL_UPDATE", "Update proposals", "Sales"}, new String[]{"PROPOSAL_DELETE", "Delete proposals", "Sales"},
            new String[]{"PROPOSAL_SEND", "Send proposals", "Sales"},
            new String[]{"CLIENT_VIEW", "View clients", "Clients"}, new String[]{"CLIENT_UPDATE", "Update clients", "Clients"},
            new String[]{"DOCUMENT_VIEW", "View documents", "Documents"}, new String[]{"DOCUMENT_CREATE", "Upload documents", "Documents"},
            new String[]{"DOCUMENT_DELETE", "Delete documents", "Documents"},
            new String[]{"PIPELINE_VIEW", "View pipelines", "Configuration"}, new String[]{"PIPELINE_UPDATE", "Manage pipelines", "Configuration"},
            new String[]{"TAG_VIEW", "View tags", "Configuration"}, new String[]{"TAG_UPDATE", "Manage tags", "Configuration"},
            new String[]{"SOURCE_VIEW", "View lead sources", "Configuration"}, new String[]{"SOURCE_UPDATE", "Manage lead sources", "Configuration"},
            new String[]{"SCORING_VIEW", "View scoring rules", "Configuration"}, new String[]{"SCORING_UPDATE", "Manage scoring rules", "Configuration"},
            new String[]{"AUTOMATION_VIEW", "View automations", "Configuration"}, new String[]{"AUTOMATION_UPDATE", "Manage automations", "Configuration"},
            new String[]{"AI_USE", "Use AI assistant", "AI"}, new String[]{"REPORT_VIEW", "View dashboards & reports", "Analytics"},
            new String[]{"AUDIT_VIEW", "View audit logs", "Administration"},
            new String[]{"USER_VIEW", "View users", "Administration"}, new String[]{"USER_CREATE", "Create users", "Administration"},
            new String[]{"USER_UPDATE", "Update users", "Administration"}, new String[]{"USER_DELETE", "Delete users", "Administration"},
            new String[]{"ROLE_VIEW", "View roles", "Administration"}, new String[]{"ROLE_CREATE", "Create roles", "Administration"},
            new String[]{"ROLE_UPDATE", "Update roles", "Administration"}, new String[]{"ROLE_DELETE", "Delete roles", "Administration"},
            new String[]{"TEAM_VIEW", "View teams", "Administration"}, new String[]{"TEAM_CREATE", "Create teams", "Administration"},
            new String[]{"TEAM_UPDATE", "Update teams", "Administration"}, new String[]{"TEAM_DELETE", "Delete teams", "Administration"},
            new String[]{"ORG_VIEW", "View organization", "Administration"}, new String[]{"ORG_UPDATE", "Update organization", "Administration"},
            new String[]{"SETTINGS_VIEW", "View settings", "Administration"}, new String[]{"SETTINGS_UPDATE", "Update settings", "Administration"});
    }

    public static List<String> keys() { return ALL().stream().map(a -> a[0]).toList(); }
}
