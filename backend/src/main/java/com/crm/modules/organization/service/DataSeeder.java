package com.crm.modules.organization.service;

import com.crm.config.CrmProperties;
import com.crm.modules.email.domain.EmailTemplate;
import com.crm.modules.email.repo.EmailTemplateRepository;
import com.crm.modules.identity.domain.Permission;
import com.crm.modules.identity.domain.Role;
import com.crm.modules.identity.domain.User;
import com.crm.modules.identity.repo.PermissionRepository;
import com.crm.modules.identity.repo.RoleRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.identity.service.RoleFactory;
import com.crm.modules.leads.domain.CustomFieldDef;
import com.crm.modules.leads.domain.LeadSource;
import com.crm.modules.leads.domain.ScoringRule;
import com.crm.modules.leads.domain.Tag;
import com.crm.modules.leads.repo.CustomFieldDefRepository;
import com.crm.modules.leads.repo.LeadSourceRepository;
import com.crm.modules.leads.repo.ScoringRuleRepository;
import com.crm.modules.leads.repo.TagRepository;
import com.crm.modules.organization.domain.Organization;
import com.crm.modules.organization.repo.OrganizationRepository;
import com.crm.modules.pipeline.domain.Pipeline;
import com.crm.modules.pipeline.repo.PipelineRepository;
import com.crm.modules.pipeline.repo.PipelineStageRepository;
import com.crm.modules.pipeline.domain.PipelineStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Idempotent dev/bootstrap seeder (profile `dev` / CRM_SEED=true). Creates the tenant, system roles,
 * admin user, default 14-stage pipeline, lead sources, tags, scoring rules, clinic-niche custom
 * fields, starter templates and two example automation rules.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final CrmProperties props;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;
    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final LeadSourceRepository sources;
    private final TagRepository tags;
    private final ScoringRuleRepository scoringRules;
    private final CustomFieldDefRepository customFields;
    private final EmailTemplateRepository templates;
    private final com.crm.modules.automation.repo.AutomationRuleRepository automations;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            log.info("[seed] crm.seed.enabled=false — skipping bootstrap data (no demo accounts will exist)");
            return;
        }
        if (users.count() > 0) {
            log.info("[seed] skipped — database already contains users");
            return;
        }
        log.info("[seed] bootstrapping initial data (org, roles, demo users, pipeline, sources, tags, scoring, fields, templates, automations)...");
        try {
            seed();
        } catch (Exception e) {
            // Keep the API alive but mark the transaction rollback-only so a partial seed never persists —
            // the next start with a clean volume re-seeds from scratch.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("[seed] SEEDING FAILED — demo accounts were NOT created, so logins will fail. "
                + "Read the stack trace below for the cause, then reset and restart: docker compose down -v && docker compose up --build", e);
            return;
        }
        log.info("[seed] complete — logins: {} / manager@nexuscrm.local / rep@nexuscrm.local (passwords per README)", props.seed().adminEmail());
    }

    private void seed() {
        // 1. Organization
        Organization org = new Organization();
        org.setName(props.seed().orgName());
        org.setSlug(props.seed().orgName().toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        org.setSettings(null);
        organizations.save(org);

        // 2. System roles for the org
        List<Permission> allPermissions = permissions.findAll();
        if (allPermissions.isEmpty()) {
            log.warn("Permission catalogue is empty — V7 migration should have seeded it");
        }
        List<Role> systemRoles = RoleFactory.createSystemRoles(org.getId(), allPermissions);
        roles.saveAll(systemRoles);
        Role adminRole = systemRoles.stream().filter(r -> "ADMIN".equals(r.getKey())).findFirst().orElseThrow();
        Role managerRole = systemRoles.stream().filter(r -> "SALES_MANAGER".equals(r.getKey())).findFirst().orElseThrow();
        Role repRole = systemRoles.stream().filter(r -> "SALES_REP".equals(r.getKey())).findFirst().orElseThrow();

        // 3. Admin user
        User admin = new User();
        admin.setOrganizationId(org.getId());
        admin.setEmail(props.seed().adminEmail().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(props.seed().adminPassword()));
        admin.setFirstName("System");
        admin.setLastName("Administrator");
        admin.setJobTitle("Administrator");
        admin.setSuperAdmin(true);
        admin.setRoles(new java.util.HashSet<>(List.of(adminRole)));
        admin.setDailyTargets(Map.of("calls", 20, "emails", 50, "meetings", 5));
        users.save(admin);

        User manager = new User();
        manager.setOrganizationId(org.getId());
        manager.setEmail("manager@nexuscrm.local");
        manager.setPasswordHash(passwordEncoder.encode("Manager123!"));
        manager.setFirstName("Sara");
        manager.setLastName("Sales");
        manager.setJobTitle("Sales Manager");
        manager.setRoles(new java.util.HashSet<>(List.of(managerRole)));
        users.save(manager);

        User rep = new User();
        rep.setOrganizationId(org.getId());
        rep.setEmail("rep@nexuscrm.local");
        rep.setPasswordHash(passwordEncoder.encode("Rep12345!"));
        rep.setFirstName("Ali");
        rep.setLastName("Raza");
        rep.setJobTitle("Sales Representative");
        rep.setRoles(new java.util.HashSet<>(List.of(repRole)));
        rep.setDailyTargets(Map.of("calls", 20, "emails", 50, "meetings", 5));
        users.save(rep);

        com.crm.modules.identity.domain.Team team = new com.crm.modules.identity.domain.Team();
        team.setOrganizationId(org.getId());
        team.setName("Sales Team");
        team.setDescription("Default sales team");
        team.setManagerId(manager.getId());
        team.getMembers().addAll(java.util.Set.of(manager, rep));
        // save via repository (declared lazily to avoid constructor churn)
        teamRepo.save(team);

        // 4. Default pipeline with the 14 spec stages
        Pipeline pipeline = new Pipeline();
        pipeline.setOrganizationId(org.getId());
        pipeline.setName("Default Sales Pipeline");
        pipeline.setDescription("Default 14-stage sales pipeline");
        pipeline.setDefault(true);
        pipelines.save(pipeline);

        List<String[]> stageDefs = List.of(
            new String[]{"New Lead", "OPEN", "5"}, new String[]{"Qualified", "OPEN", "10"},
            new String[]{"Assigned", "OPEN", "10"}, new String[]{"Contacted", "OPEN", "15"},
            new String[]{"Connected", "OPEN", "20"}, new String[]{"Interested", "OPEN", "25"},
            new String[]{"Discovery Call", "OPEN", "35"}, new String[]{"Demo", "OPEN", "45"},
            new String[]{"Offer Sent", "OPEN", "55"}, new String[]{"Proposal Sent", "OPEN", "65"},
            new String[]{"Negotiation", "OPEN", "75"}, new String[]{"Won", "WON", "100"},
            new String[]{"Lost", "LOST", "0"}, new String[]{"Nurture", "OPEN", "10"});
        int pos = 0;
        for (String[] def : stageDefs) {
            PipelineStage s = new PipelineStage();
            s.setPipelineId(pipeline.getId());
            s.setName(def[0]);
            s.setType(def[1]);
            s.setProbability(Integer.parseInt(def[2]));
            s.setPosition(pos++);
            stages.save(s);
        }

        // 5. Lead sources
        List<String[]> sourceDefs = List.of(
            new String[]{"EXCEL_IMPORT", "Excel Import"}, new String[]{"WEBSITE", "Website"},
            new String[]{"LINKEDIN", "LinkedIn"}, new String[]{"COLD_OUTREACH", "Cold Outreach"},
            new String[]{"REFERRAL", "Referral"}, new String[]{"ADVERTISEMENT", "Advertisement"},
            new String[]{"MANUAL_ENTRY", "Manual Entry"}, new String[]{"API", "API"},
            new String[]{"CAMPAIGN", "Campaign"}, new String[]{"OTHER", "Other"});
        for (String[] def : sourceDefs) {
            LeadSource s = new LeadSource();
            s.setOrganizationId(org.getId());
            s.setKey(def[0]);
            s.setName(def[1]);
            sources.save(s);
        }

        // 6. Starter tags
        for (String name : List.of("High Value", "Follow Up", "Decision Maker", "Hot Lead", "Enterprise")) {
            Tag t = new Tag();
            t.setOrganizationId(org.getId());
            t.setName(name);
            tags.save(t);
        }

        // 7. Default scoring rules (§13)
        List<Object[]> scoreDefs = List.of(
            new Object[]{"HAS_EMAIL", null, 10, "Verified email present"},
            new Object[]{"HAS_PHONE", null, 10, "Phone available"},
            new Object[]{"DECISION_MAKER_TITLE", null, 15, "Decision maker identified"},
            new Object[]{"INDUSTRY_IN", "medical clinic, healthcare, longevity, wellness", 20, "Target industry"},
            new Object[]{"COUNTRY_IN", "united arab emirates, dubai, usa, united states, saudi arabia", 15, "Target location"},
            new Object[]{"HAS_WEBSITE", null, 10, "Website available"},
            new Object[]{"STATUS_IS", "INTERESTED", 20, "Replied / interested"},
            new Object[]{"CUSTOM_FIELD_IS", "services=IV Therapy", 10, "Offers IV therapy"});
        int scorePos = 0;
        for (Object[] def : scoreDefs) {
            ScoringRule r = new ScoringRule();
            r.setOrganizationId(org.getId());
            r.setCriterion((String) def[0]);
            r.setOperand((String) def[1]);
            r.setPoints((Integer) def[2]);
            r.setLabel((String) def[3]);
            r.setActive(true);
            r.setPosition(scorePos++);
            scoringRules.save(r);
        }

        // 8. Clinic-niche custom field definitions (configurable per org)
        List<Object[]> fieldDefs = List.of(
            new Object[]{"clinic_type", "Clinic Type", "SELECT", List.of("Medspa", "Longevity Clinic", "IV Bar", "Dermatology", "Regenerative", "Multi-disciplinary")},
            new Object[]{"services", "Services Offered", "MULTI_SELECT", List.of("IV Therapy", "Peptide Therapy", "Regenerative Medicine", "PRP", "Exosomes", "Longevity Programs", "Aesthetics")},
            new Object[]{"locations_count", "Number of Locations", "NUMBER", List.of()},
            new Object[]{"doctor_owner", "Doctor/Owner", "TEXT", List.of()},
            new Object[]{"website_quality", "Website Quality", "SELECT", List.of("Excellent", "Good", "Average", "Poor")},
            new Object[]{"existing_software", "Existing Software", "TEXT", List.of()},
            new Object[]{"crm_used", "CRM Currently Used", "TEXT", List.of()},
            new Object[]{"estimated_opportunity", "Estimated Opportunity", "NUMBER", List.of()},
            new Object[]{"pain_points", "Pain Points", "TEXT", List.of()},
            new Object[]{"automation_opportunity", "Automation Opportunities", "TEXT", List.of()});
        int fieldPos = 0;
        for (Object[] def : fieldDefs) {
            CustomFieldDef def2 = new CustomFieldDef();
            def2.setOrganizationId(org.getId());
            def2.setKey((String) def[0]);
            def2.setLabel((String) def[1]);
            def2.setType((String) def[2]);
            @SuppressWarnings("unchecked")
            List<String> opts = (List<String>) def[3];
            def2.setOptions(opts);
            def2.setPosition(fieldPos++);
            customFields.save(def2);
        }

        // 9. Starter email templates
        createTemplate(org.getId(), admin.getId(), "Initial Outreach",
            "Quick idea for {{companyName}}",
            """
                <p>Hi {{firstName}},</p>
                <p>I noticed {{companyName}} in {{city}} — impressed by what you're building in the {{industry}} space.</p>
                <p>We help practices like yours stay on top of every lead and follow-up without adding admin work.</p>
                <p>Open to a 15-minute call this week?</p>
                <p>Best regards</p>""",
            "Hi {{firstName}}, I noticed {{companyName}} in {{city}}. Open to a 15-minute call this week?");
        createTemplate(org.getId(), admin.getId(), "Follow-up 2 days",
            "Following up — {{companyName}}",
            """
                <p>Hi {{firstName}},</p>
                <p>Just following up on my last note. If improving lead follow-up at {{companyName}} is a priority this quarter, I'd love to show you a 15-minute walkthrough.</p>
                <p>Best regards</p>""",
            "Hi {{firstName}}, following up on my last note. Open to a short walkthrough?");
        createTemplate(org.getId(), admin.getId(), "Final Break-up",
            "Should I close your file, {{firstName}}?",
            """
                <p>Hi {{firstName}},</p>
                <p>I haven't heard back, which usually means the timing isn't right — totally understandable.</p>
                <p>I'll stop reaching out for now, but if lead follow-up ever becomes a pain point at {{companyName}}, reply to this email and I'll pick it straight back up.</p>
                <p>Best regards</p>""",
            "Hi {{firstName}}, I'll stop reaching out for now — reply anytime to pick this back up.");

        // 10. Example automation rules
        com.crm.modules.automation.domain.AutomationRule r1 = new com.crm.modules.automation.domain.AutomationRule();
        r1.setOrganizationId(org.getId());
        r1.setCreatedBy(admin.getId());
        r1.setName("Call logged with no answer → follow-up task in 2 days");
        r1.setTrigger(com.crm.modules.automation.domain.AutomationRule.Trigger.CALL_LOGGED);
        r1.setConditions(Map.of("outcome", "NO_ANSWER"));
        r1.setAction(com.crm.modules.automation.domain.AutomationRule.Action.CREATE_TASK);
        r1.setActionConfig(Map.of("title", "Retry call (no answer)", "dueInDays", 2, "priority", "MEDIUM"));
        r1.setActive(true);
        automations.save(r1);

        com.crm.modules.automation.domain.AutomationRule r2 = new com.crm.modules.automation.domain.AutomationRule();
        r2.setOrganizationId(org.getId());
        r2.setCreatedBy(admin.getId());
        r2.setName("Lead enters Contacted → set 3-day no-reply reminder");
        r2.setTrigger(com.crm.modules.automation.domain.AutomationRule.Trigger.LEAD_STAGE_CHANGED);
        r2.setConditions(Map.of("toStageName", "Contacted"));
        r2.setAction(com.crm.modules.automation.domain.AutomationRule.Action.CREATE_TASK);
        r2.setActionConfig(Map.of("title", "Check for reply", "dueInDays", 3, "priority", "LOW"));
        r2.setActive(true);
        automations.save(r2);

        log.info("Seeding complete. Admin login: {} / (set via CRM_ADMIN_PASSWORD)", admin.getEmail());
    }

    private void createTemplate(UUID orgId, UUID userId, String name, String subject, String html, String text) {
        EmailTemplate t = new EmailTemplate();
        t.setOrganizationId(orgId);
        t.setCreatedBy(userId);
        t.setName(name);
        t.setSubject(subject);
        t.setBodyHtml(html);
        t.setBodyText(text);
        t.setCategory("OUTREACH");
        templates.save(t);
    }

    private final com.crm.modules.identity.repo.TeamRepository teamRepo;
}
