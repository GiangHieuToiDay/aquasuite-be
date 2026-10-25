package com.aquasuite.ims.module.sales.internal.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.aquasuite.ims.module.sales.internal.calculator.QuotationConstants.MONEY_SCALE;
import static com.aquasuite.ims.module.sales.internal.calculator.QuotationConstants.PERCENT_SCALE;
import static com.aquasuite.ims.module.sales.internal.calculator.QuotationConstants.ROUNDING_MODE;

public class QuotationCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public record CalculationLine(
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountRate,
            BigDecimal unitCost,
            boolean isComponent
    ) {}

    public record LineCalculated(
            BigDecimal discountAmount,
            BigDecimal lineTotal,
            BigDecimal lineProfit
    ) {}

    public record QuotationTotals(
            List<LineCalculated> lineTotals,
            BigDecimal listPriceTotal,
            BigDecimal totalDiscount,
            BigDecimal netAmount,
            BigDecimal vatRate,
            BigDecimal vatAmount,
            BigDecimal shippingFee,
            BigDecimal grandTotal,
            BigDecimal grossProfit,
            BigDecimal marginPercent
    ) {}

    public QuotationTotals calculate(
            List<CalculationLine> lines,
            BigDecimal vatRate,
            BigDecimal shippingFee
    ) {
        BigDecimal safeVatRate = (vatRate != null) ? vatRate : BigDecimal.ZERO;
        BigDecimal safeShippingFee = (shippingFee != null) ? shippingFee.setScale(MONEY_SCALE, ROUNDING_MODE) : BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);

        if (lines == null || lines.isEmpty()) {
            return new QuotationTotals(
                    Collections.emptyList(),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE),
                    safeVatRate,
                    BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE),
                    safeShippingFee,
                    safeShippingFee,
                    BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE),
                    BigDecimal.ZERO.setScale(PERCENT_SCALE, ROUNDING_MODE)
            );
        }

        List<LineCalculated> calculatedLines = new ArrayList<>(lines.size());

        BigDecimal sumListPrice = BigDecimal.ZERO;
        BigDecimal sumDiscount = BigDecimal.ZERO;
        BigDecimal sumCost = BigDecimal.ZERO;

        for (CalculationLine line : lines) {
            BigDecimal qty = (line.quantity() != null) ? line.quantity() : BigDecimal.ZERO;
            BigDecimal price = (line.unitPrice() != null) ? line.unitPrice() : BigDecimal.ZERO;
            BigDecimal rate = (line.discountRate() != null) ? line.discountRate() : BigDecimal.ZERO;
            BigDecimal cost = (line.unitCost() != null) ? line.unitCost() : BigDecimal.ZERO;

            // 1. Tính chiết khấu dòng: quantity * unitPrice * discountRate / 100
            BigDecimal grossLine = qty.multiply(price);
            BigDecimal discountAmount = grossLine.multiply(rate)
                    .divide(ONE_HUNDRED, MONEY_SCALE, ROUNDING_MODE);

            // 2. Tính thành tiền dòng: quantity * unitPrice - discountAmount
            BigDecimal lineTotal = grossLine.setScale(MONEY_SCALE, ROUNDING_MODE).subtract(discountAmount);

            // 3. Tính lợi nhuận dòng: lineTotal - (quantity * unitCost)
            BigDecimal lineCost = qty.multiply(cost).setScale(MONEY_SCALE, ROUNDING_MODE);
            BigDecimal lineProfit = lineTotal.subtract(lineCost);

            calculatedLines.add(new LineCalculated(discountAmount, lineTotal, lineProfit));

            // Chỉ cộng vào tổng báo giá nếu là dòng gốc (!isComponent)
            if (!line.isComponent()) {
                sumListPrice = sumListPrice.add(grossLine.setScale(MONEY_SCALE, ROUNDING_MODE));
                sumDiscount = sumDiscount.add(discountAmount);
                sumCost = sumCost.add(lineCost);
            }
        }

        BigDecimal netAmount = sumListPrice.subtract(sumDiscount);
        BigDecimal vatAmount = netAmount.multiply(safeVatRate)
                .divide(ONE_HUNDRED, MONEY_SCALE, ROUNDING_MODE);
        BigDecimal grandTotal = netAmount.add(vatAmount).add(safeShippingFee);
        BigDecimal grossProfit = netAmount.subtract(sumCost);

        BigDecimal marginPercent = BigDecimal.ZERO.setScale(PERCENT_SCALE, ROUNDING_MODE);
        if (netAmount.compareTo(BigDecimal.ZERO) > 0) {
            marginPercent = grossProfit.multiply(ONE_HUNDRED)
                    .divide(netAmount, PERCENT_SCALE, ROUNDING_MODE);
        }

        return new QuotationTotals(
                calculatedLines,
                sumListPrice,
                sumDiscount,
                netAmount,
                safeVatRate,
                vatAmount,
                safeShippingFee,
                grandTotal,
                grossProfit,
                marginPercent
        );
    }
}
