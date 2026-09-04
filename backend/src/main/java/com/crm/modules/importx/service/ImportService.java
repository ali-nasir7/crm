package com.crm.modules.importx.service;

import com.crm.common.api.ApiException;
import com.crm.common.api.PageResponse;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.audit.service.AuditService;
import com.crm.modules.importx.domain.ImportJob;
import com.crm.modules.importx.domain.ImportRow;
import com.crm.modules.importx.repo.ImportJobRepository;
import com.crm.modules.importx.repo.ImportRowRepository;
import com.crm.modules.leads.domain.Lead;
import com.crm.modules.leads.repo.LeadRepository;
import com.crm.modules.leads.service.LeadScoringService;
import com.crm.modules.leads.service.DuplicateDetectionService;
import com.crm.common.util.Normalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Import wizard backend:
 *  1. upload → parse → store rows → suggest column mapping (AWAITING_MAPPING)
 *  2. submit mapping → validate rows → detect duplicates (against DB and within the file)
 *  3. async processing → create leads per duplicate strategy → summary
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    public record ImportJobItem(UUID id, String fileName, String fileType, int totalRows, int validRows,
                                int duplicateRows, int invalidRows, int importedRows, String status,
                                Map<String, String> mapping, List<String> suggestedHeaders,
                                Map<String, String> suggestedMapping, String duplicateStrategy,
                                Map<String, Object> options, String errorMessage, Instant createdAt, Instant completedAt,
                                String createdByEmail) {}

    public record ImportRowItem(UUID id, int rowNumber, Map<String, Object> raw, String status,
                                Map<String, Object> errors, UUID duplicateOfLeadId, UUID importedLeadId) {}

    public record MappingRequest(Map<String, String> mapping, String duplicateStrategy, Map<String, Object> options) {}

    /** Canonical target fields (snake keys) the mapping UI can choose from. */
    public static final List<String[]> TARGET_FIELDS = List.of(
        new String[]{"business_name", "Business Name *"},
        new String[]{"first_name", "First Name"}, new String[]{"last_name", "Last Name"},
        new String[]{"job_title", "Job Title"}, new String[]{"email", "Email"},
        new String[]{"secondary_email", "Secondary Email"}, new String[]{"phone", "Phone"},
        new String[]{"whatsapp", "WhatsApp"}, new String[]{"website", "Website"},
        new String[]{"linkedin", "LinkedIn"}, new String[]{"country", "Country"},
        new String[]{"state", "State"}, new String[]{"city", "City"}, new String[]{"address", "Address"},
        new String[]{"timezone", "Timezone"}, new String[]{"industry", "Industry"},
        new String[]{"business_type", "Business Type"}, new String[]{"company_size", "Company Size"},
        new String[]{"employees_count", "Employees"}, new String[]{"revenue_range", "Revenue Range"},
        new String[]{"notes", "Notes"});

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
        Map.entry("business name", "business_name"), Map.entry("company", "business_name"),
        Map.entry("company name", "business_name"), Map.entry("business", "business_name"),
        Map.entry("clinic name", "business_name"), Map.entry("practice name", "business_name"),
        Map.entry("contact name", "contact_full_name"), Map.entry("full name", "contact_full_name"),
        Map.entry("first name", "first_name"), Map.entry("lastname", "last_name"),
        Map.entry("last name", "last_name"), Map.entry("surname", "last_name"),
        Map.entry("title", "job_title"), Map.entry("job title", "job_title"), Map.entry("position", "job_title"),
        Map.entry("role", "job_title"), Map.entry("email", "email"), Map.entry("e-mail", "email"),
        Map.entry("email address", "email"), Map.entry("work email", "email"),
        Map.entry("phone", "phone"), Map.entry("phone number", "phone"), Map.entry("mobile", "phone"),
        Map.entry("telephone", "phone"), Map.entry("tel", "phone"),
        Map.entry("whatsapp", "whatsapp"), Map.entry("website", "website"), Map.entry("url", "website"),
        Map.entry("domain", "website"), Map.entry("site", "website"),
        Map.entry("linkedin", "linkedin"), Map.entry("linkedin url", "linkedin"),
        Map.entry("country", "country"), Map.entry("city", "city"), Map.entry("state", "state"),
        Map.entry("address", "address"), Map.entry("timezone", "timezone"),
        Map.entry("industry", "industry"), Map.entry("specialty", "industry"), Map.entry("niche", "industry"),
        Map.entry("business type", "business_type"), Map.entry("company size", "company_size"),
        Map.entry("employees", "employees_count"), Map.entry("revenue", "revenue_range"),
        Map.entry("notes", "notes"), Map.entry("comments", "notes"));

    private final ImportJobRepository jobs;
    private final ImportRowRepository rows;
    private final SpreadsheetParser parser;
    private final LeadRepository leads;
    private final LeadScoringService scoring;
    private final DuplicateDetectionService duplicates;
    private final ActivityService activities;
    private final AuditService audit;
    private final com.crm.modules.pipeline.repo.LeadStageHistoryRepository stageHistory;
    private final com.crm.modules.identity.repo.UserRepository users;

    @Transactional
    public ImportJobItem upload(UUID orgId, UUID userId, String fileName, byte[] content) {
        SpreadsheetParser.Parsed parsed = parser.parse(fileName, content);
        if (parsed.rows().isEmpty()) throw ApiException.badRequest("The file contains no data rows");

        ImportJob job = new ImportJob();
        job.setOrganizationId(orgId);
        job.setFileName(fileName);
        job.setFileType(fileName.toLowerCase().endsWith(".xlsx") ? "XLSX" : "CSV");
        job.setTotalRows(parsed.rows().size());
        job.setStatus("AWAITING_MAPPING");
        jobs.save(job);

        List<ImportRow> entities = new ArrayList<>(parsed.rows().size());
        for (int i = 0; i < parsed.rows().size(); i++) {
            ImportRow r = new ImportRow();
            r.setJobId(job.getId());
            r.setOrganizationId(orgId);
            r.setRowNumber(i + 1);
            r.setRaw(parsed.rows().get(i));
            r.setStatus("PENDING");
            entities.add(r);
        }
        rows.saveAll(entities);

        var suggested = suggestMapping(parsed.headers());
        audit.log("IMPORT_UPLOAD", "IMPORT", job.getId(), fileName, null, Map.of("rows", parsed.rows().size()));
        return toItem(job, suggested);
    }

    public static Map<String, String> suggestMapping(List<String> headers) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String header : headers) {
            String alias = HEADER_ALIASES.getOrDefault(header.toLowerCase().trim(), null);
            if (alias != null && !mapping.containsValue(alias)) mapping.put(header, alias);
        }
        return mapping;
    }

    /** Phase 2: user-confirmed mapping → validate + detect duplicates. */
    @Transactional
    public ImportJobItem submitMapping(UUID orgId, UUID jobId, MappingRequest req) {
        ImportJob job = find(orgId, jobId);
        if ("PROCESSING".equals(job.getStatus())) throw ApiException.business("Import is already running");
        if (req.mapping() == null || req.mapping().isEmpty()) throw ApiException.badRequest("Mapping is required");
        if (!req.mapping().containsValue("business_name")) throw ApiException.badRequest("Map at least the Business Name column");

        job.setMapping(req.mapping());
        if (req.duplicateStrategy() != null) {
            String s = req.duplicateStrategy().toUpperCase();
            if (!List.of("SKIP", "UPDATE_EXISTING", "CREATE_ANYWAY").contains(s)) throw ApiException.badRequest("Invalid duplicate strategy");
            job.setDuplicateStrategy(s);
        }
        job.setOptions(req.options());
        job.setStatus("PROCESSING");
        jobs.save(job);
        return toItem(job, null);
    }

    /** Phase 3: async worker (also does validation & duplicate detection). */
    @Async("ioTaskExecutor")
    @Transactional
    public void process(UUID orgId, UUID jobId) {
        ImportJob job = jobs.findById(jobId).orElse(null);
        if (job == null) return;
        try {
            Map<String, String> mapping = job.getMapping();
            Map<String, Object> options = job.getOptions() == null ? Map.of() : job.getOptions();
            UUID defaultAssignee = options.get("defaultAssignee") != null
                ? UUID.fromString(options.get("defaultAssignee").toString()) : null;
            UUID defaultSourceId = options.get("defaultSourceId") != null
                ? UUID.fromString(options.get("defaultSourceId").toString()) : null;
            List<String> defaultTags = options.get("tags") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();

            int valid = 0, invalid = 0, dup = 0, imported = 0;
            Set<String> seenEmails = new HashSet<>();

            var processable = rows.findProcessable(jobId);
            for (ImportRow r : processable) {
                Map<String, String> values = applyMapping(r.getRaw(), mapping);
                Map<String, Object> errors = validate(values);
                if (!errors.isEmpty()) {
                    r.setStatus("INVALID");
                    r.setErrors(errors);
                    invalid++;
                    rows.save(r);
                    continue;
                }
                valid++;

                String email = Normalizer.email(values.get("email"));
                DuplicateDetectionService.DuplicateMatch match = null;
                if (email != null && !seenEmails.add(email)) {
                    match = new DuplicateDetectionService.DuplicateMatch("file", null, "duplicate inside file");
                } else {
                    match = duplicates.findDuplicate(orgId, values.get("email"), values.get("phone"), values.get("website"),
                        values.get("linkedin"), values.get("business_name"), null);
                }

                if (match != null && "SKIP".equals(job.getDuplicateStrategy())) {
                    r.setStatus("DUPLICATE");
                    r.setDuplicateOfLeadId(match.existingLeadId());
                    r.setErrors(Map.of("duplicate", match.field() + ": " + match.existingLabel()));
                    dup++;
                    rows.save(r);
                    continue;
                }

                try {
                    Lead lead = buildLead(orgId, values, defaultAssignee, defaultSourceId, defaultTags);
                    if (match != null && "UPDATE_EXISTING".equals(job.getDuplicateStrategy())) {
                        lead = leads.findById(match.existingLeadId()).orElse(lead);
                        if (lead.getOrganizationId() == null) lead.setOrganizationId(orgId);
                    }
                    applyValues(lead, values, defaultAssignee, defaultSourceId, defaultTags);
                    lead.setScore(scoring.score(orgId, lead));
                    leads.save(lead);
                    if (lead.getStageId() != null) {
                        com.crm.modules.pipeline.domain.LeadStageHistory h = new com.crm.modules.pipeline.domain.LeadStageHistory();
                        h.setLeadId(lead.getId());
                        h.setOrganizationId(orgId);
                        h.setToStageId(lead.getStageId());
                        h.setEnteredAt(Instant.now());
                        stageHistory.save(h);
                    }
                    r.setStatus("IMPORTED");
                    r.setImportedLeadId(lead.getId());
                    imported++;
                } catch (Exception e) {
                    r.setStatus("FAILED");
                    r.setErrors(Map.of("error", e.getMessage() == null ? "import failed" : e.getMessage()));
                }
                rows.save(r);
            }

            job.setValidRows(valid);
            job.setInvalidRows(invalid);
            job.setDuplicateRows(dup);
            job.setImportedRows(imported);
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            jobs.save(job);
            activities.record(orgId, ActivityType.IMPORT, null, "Import completed: " + job.getFileName(),
                imported + " imported, " + dup + " duplicates, " + invalid + " invalid",
                Map.of("imported", imported, "duplicates", dup, "invalid", invalid), job.getCreatedBy());
            audit.log("IMPORT_COMPLETE", "IMPORT", job.getId(), job.getFileName(), null,
                Map.of("imported", imported, "duplicates", dup, "invalid", invalid));
        } catch (Exception e) {
            log.error("Import processing failed", e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            jobs.save(job);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportJobItem> history(UUID orgId, int page, int size) {
        var result = jobs.findByOrganizationIdOrderByCreatedAtDesc(orgId, PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result.map(j -> toItem(j, null)));
    }

    @Transactional(readOnly = true)
    public ImportJobItem get(UUID orgId, UUID id) {
        return toItem(find(orgId, id), null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportRowItem> rowsView(UUID orgId, UUID jobId, String status, int page, int size) {
        find(orgId, jobId);
        var result = (status == null || status.isBlank())
            ? rows.findByJobIdOrderByRowNumberAsc(jobId, PageRequest.of(page, Math.min(size, 200)))
            : rows.findByJobIdAndStatusOrderByRowNumberAsc(jobId, status.toUpperCase(), PageRequest.of(page, Math.min(size, 200)));
        return PageResponse.of(result.map(r -> new ImportRowItem(r.getId(), r.getRowNumber(), r.getRaw(), r.getStatus(),
            r.getErrors(), r.getDuplicateOfLeadId(), r.getImportedLeadId())));
    }

    /** Downloadable CSV of error rows. */
    @Transactional(readOnly = true)
    public String errorReport(UUID orgId, UUID jobId) {
        find(orgId, jobId);
        List<ImportRow> bad = new ArrayList<>(rows.findByJobIdAndStatusOrderByRowNumberAsc(jobId, "INVALID", PageRequest.of(0, 5000)).getContent());
        bad.addAll(rows.findByJobIdAndStatusOrderByRowNumberAsc(jobId, "DUPLICATE", PageRequest.of(0, 5000)).getContent());
        bad.addAll(rows.findByJobIdAndStatusOrderByRowNumberAsc(jobId, "FAILED", PageRequest.of(0, 5000)).getContent());
        bad.sort(Comparator.comparingInt(ImportRow::getRowNumber));
        List<String> header = List.of("row", "status", "errors", "data");
        List<List<Object>> lines = bad.stream()
            .map(r -> List.<Object>of(r.getRowNumber(), r.getStatus(),
                String.valueOf(r.getErrors() == null ? "" : r.getErrors()), String.valueOf(r.getRaw())))
            .toList();
        return com.crm.common.util.CsvUtil.write(header, lines);
    }

    // ---------- mapping/validation helpers ----------

    private Map<String, String> applyMapping(Map<String, Object> raw, Map<String, String> mapping) {
        Map<String, String> out = new HashMap<>();
        mapping.forEach((column, field) -> {
            Object v = raw.get(column);
            if (v != null && !String.valueOf(v).isBlank()) {
                out.merge(field, String.valueOf(v).trim(), (a, b) -> a);
            }
        });
        // combined "Contact Name" split into first/last
        String full = out.remove("contact_full_name");
        if (full != null) {
            String[] parts = full.trim().split("\\s+", 2);
            out.putIfAbsent("first_name", parts[0]);
            if (parts.length > 1) out.putIfAbsent("last_name", parts[1]);
        }
        return out;
    }

    private Map<String, Object> validate(Map<String, String> values) {
        Map<String, Object> errors = new LinkedHashMap<>();
        if (values.get("business_name") == null) errors.put("business_name", "Business name is required");
        String email = values.get("email");
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) errors.put("email", "Invalid email format");
        if (values.get("employees_count") != null && !values.get("employees_count").matches("\\d+")) {
            errors.put("employees_count", "Must be a number");
        }
        return errors;
    }

    private Lead buildLead(UUID orgId, Map<String, String> values, UUID assignee, UUID sourceId, List<String> tags) {
        Lead lead = new Lead();
        lead.setOrganizationId(orgId);
        lead.setAssignedUserId(assignee);
        lead.setSourceId(sourceId);
        return lead;
    }

    private void applyValues(Lead lead, Map<String, String> values, UUID assignee, UUID sourceId, List<String> tags) {
        lead.setBusinessName(values.get("business_name"));
        lead.setFirstName(values.get("first_name"));
        lead.setLastName(values.get("last_name"));
        lead.setJobTitle(values.get("job_title"));
        lead.setEmail(Normalizer.email(values.get("email")));
        lead.setSecondaryEmail(Normalizer.email(values.get("secondary_email")));
        lead.setPhone(values.get("phone"));
        lead.setWhatsapp(values.get("whatsapp"));
        lead.setWebsite(values.get("website"));
        lead.setLinkedin(values.get("linkedin"));
        lead.setCountry(values.get("country"));
        lead.setState(values.get("state"));
        lead.setCity(values.get("city"));
        lead.setAddress(values.get("address"));
        lead.setTimezone(values.get("timezone"));
        lead.setIndustry(values.get("industry"));
        lead.setBusinessType(values.get("business_type"));
        lead.setCompanySize(values.get("company_size"));
        lead.setRevenueRange(values.get("revenue_range"));
        lead.setNotes(values.get("notes"));
        if (values.get("employees_count") != null) {
            try { lead.setEmployeesCount(Integer.parseInt(values.get("employees_count"))); } catch (NumberFormatException ignored) {}
        }
        if (assignee != null) lead.setAssignedUserId(assignee);
        if (sourceId != null) lead.setSourceId(sourceId);
    }

    private ImportJob find(UUID orgId, UUID id) {
        return jobs.findInOrg(orgId, id).orElseThrow(() -> ApiException.notFound("Import not found"));
    }

    public ImportJobItem toItem(ImportJob j, Map<String, String> suggestedMapping) {
        String createdByEmail = j.getCreatedBy() != null
            ? users.findById(j.getCreatedBy()).map(u -> u.getEmail()).orElse(null) : null;
        return new ImportJobItem(j.getId(), j.getFileName(), j.getFileType(), j.getTotalRows(), j.getValidRows(),
            j.getDuplicateRows(), j.getInvalidRows(), j.getImportedRows(), j.getStatus(), j.getMapping(),
            null, suggestedMapping, j.getDuplicateStrategy(), j.getOptions(), j.getErrorMessage(),
            j.getCreatedAt(), j.getCompletedAt(), createdByEmail);
    }
}
