package com.crm.modules.proposals.service;

import com.crm.modules.proposals.domain.Proposal;
import com.crm.modules.proposals.domain.ProposalItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Pure total calculation shared by the API DTO and the PDF renderer. */
public final class ProposalTotals {
    private ProposalTotals() {}

    public record Totals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxPercent, BigDecimal taxAmount, BigDecimal total) {}

    public static Totals of(Proposal p, List<ProposalItem> items) {
        BigDecimal subtotal = items.stream()
            .map(i -> i.getUnitPrice().multiply(i.getQuantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = p.getDiscountPercent() == null ? BigDecimal.ZERO
            : subtotal.multiply(p.getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal tax = p.getTaxPercent() == null ? BigDecimal.ZERO
            : taxable.multiply(p.getTaxPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Totals(subtotal.setScale(2, RoundingMode.HALF_UP), discount,
            p.getTaxPercent() == null ? BigDecimal.ZERO : p.getTaxPercent(), tax,
            taxable.add(tax).setScale(2, RoundingMode.HALF_UP));
    }
}
