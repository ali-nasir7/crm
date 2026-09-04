package com.crm.modules.documents.service;

import com.crm.common.api.ApiException;
import com.crm.modules.documents.domain.Document;
import com.crm.modules.documents.repo.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "csv", "png", "jpg", "jpeg", "txt", "md", "zip");
    private static final long MAX_SIZE = 10 * 1024 * 1024;

    private final DocumentRepository documents;
    private final StorageProvider storage;

    public Document upload(UUID orgId, UUID userId, MultipartFile file, UUID leadId, UUID companyId,
                           UUID dealId, UUID proposalId, UUID clientId) {
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) throw ApiException.badRequest("File type not allowed: " + ext);
        try {
            if (file.getSize() > MAX_SIZE) throw ApiException.badRequest("File exceeds 10 MB limit");
            byte[] content = file.getBytes();
            // basic magic-byte sniffing for the common risky cases
            if (content.length > 4 && content[0] == 'M' && content[1] == 'Z') throw ApiException.badRequest("Executable files are not allowed");
            String key = storage.store(name, file.getContentType(), content);
            Document d = new Document();
            d.setOrganizationId(orgId);
            d.setName(name);
            d.setFileName(name);
            d.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            d.setSizeBytes(content.length);
            d.setStorageKey(key);
            d.setLeadId(leadId);
            d.setCompanyId(companyId);
            d.setDealId(dealId);
            d.setProposalId(proposalId);
            d.setClientId(clientId);
            d.setUploadedBy(userId);
            return documents.save(d);
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("Could not read uploaded file");
        }
    }

    public List<Document> list(UUID orgId, UUID leadId, UUID companyId, UUID dealId, UUID proposalId, UUID clientId) {
        return documents.findFiltered(orgId, leadId, companyId, dealId, proposalId, clientId);
    }

    public record Download(Document document, byte[] content) {}

    public Download download(UUID orgId, UUID id) {
        Document d = documents.findInOrg(orgId, id).orElseThrow(() -> ApiException.notFound("Document not found"));
        return new Download(d, storage.load(d.getStorageKey()));
    }

    public void delete(UUID orgId, UUID id) {
        Document d = documents.findInOrg(orgId, id).orElseThrow(() -> ApiException.notFound("Document not found"));
        storage.delete(d.getStorageKey());
        documents.delete(d);
    }
}
