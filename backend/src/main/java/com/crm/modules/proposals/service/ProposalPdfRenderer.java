package com.crm.modules.proposals.service;

import com.crm.modules.companies.domain.Company;
import com.crm.modules.contacts.domain.Contact;
import com.crm.modules.proposals.domain.Proposal;
import com.crm.modules.proposals.domain.ProposalItem;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Server-side professional proposal PDF (OpenPDF). */
@Component
public class ProposalPdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);

    public byte[] render(Proposal p, List<ProposalItem> items, Company company, Contact contact) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 42, 42, 48, 48);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(30, 41, 59));
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(71, 85, 105));
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(51, 65, 85));
            Font th = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

            doc.add(new Paragraph(p.getTitle(), h1));
            doc.add(new Paragraph("Proposal " + p.getProposalNumber() + "  ·  " + DATE.format(java.time.Instant.now()), h2));
            doc.add(new Paragraph(" ", normal));

            StringBuilder meta = new StringBuilder();
            meta.append("Prepared for: ").append(company != null ? company.getName() : "Client").append("\n");
            if (contact != null) meta.append("Attention: ").append(contact.displayName()).append("\n");
            if (p.getValidUntil() != null) meta.append("Valid until: ").append(DATE.format(p.getValidUntil())).append("\n");
            doc.add(new Paragraph(meta.toString(), normal));
            doc.add(new Paragraph(" ", normal));

            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                doc.add(new Paragraph("Overview", h2));
                doc.add(new Paragraph(p.getDescription(), normal));
                doc.add(new Paragraph(" ", normal));
            }

            PdfPTable table = new PdfPTable(new float[]{4f, 1.2f, 2f, 2f});
            table.setWidthPercentage(100);
            streamHeader(table, th, "Service", "Qty", "Unit price", "Total");
            for (ProposalItem i : items) {
                table.addCell(cell(i.getName(), normal));
                table.addCell(cell(i.getQuantity().stripTrailingZeros().toPlainString(), normal));
                table.addCell(cell(i.getUnitPrice().toPlainString() + " " + p.getCurrency(), normal));
                table.addCell(cell(i.getUnitPrice().multiply(i.getQuantity()).toPlainString() + " " + p.getCurrency(), normal));
            }
            var t = ProposalTotals.of(p, items);
            table.addCell(cell("", normal));
            table.addCell(cell("", normal));
            table.addCell(cellRight("Subtotal", normal));
            table.addCell(cell(t.subtotal().toPlainString() + " " + p.getCurrency(), normal));
            if (t.discountAmount().signum() > 0) {
                table.addCell(cell("", normal)); table.addCell(cell("", normal));
                table.addCell(cellRight("Discount", normal));
                table.addCell(cell("-" + t.discountAmount().toPlainString() + " " + p.getCurrency(), normal));
            }
            if (t.taxAmount().signum() > 0) {
                table.addCell(cell("", normal)); table.addCell(cell("", normal));
                table.addCell(cellRight("Tax (" + t.taxPercent().stripTrailingZeros().toPlainString() + "%)", normal));
                table.addCell(cell(t.taxAmount().toPlainString() + " " + p.getCurrency(), normal));
            }
            table.addCell(cell("", normal)); table.addCell(cell("", normal));
            table.addCell(cellRight("Total", th));
            table.addCell(cell(t.total().toPlainString() + " " + p.getCurrency(), th));
            doc.add(table);

            if (p.getTerms() != null && !p.getTerms().isBlank()) {
                doc.add(new Paragraph(" ", normal));
                doc.add(new Paragraph("Terms", h2));
                doc.add(new Paragraph(p.getTerms(), normal));
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    private void streamHeader(PdfPTable table, Font th, String... cols) {
        for (String c : cols) {
            PdfPCell cell = new PdfPCell(new Phrase(c, th));
            cell.setBackgroundColor(new Color(30, 41, 59));
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setPadding(6);
        c.setBorderColor(new Color(226, 232, 240));
        return c;
    }

    private PdfPCell cellRight(String text, Font font) {
        PdfPCell c = cell(text, font);
        c.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
        return c;
    }
}
