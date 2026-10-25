package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator.CalculationLine;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator.QuotationTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationCalculatorTest {

    private QuotationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new QuotationCalculator();
    }

    @Test
    @DisplayName("5.1 - Ca thường với các mức chiết khấu khác nhau theo dòng, VAT 10%, phí vận chuyển 50,000")
    void testStandardCalculationWithDifferentDiscountsAndVat10() {
        // Dòng 1: qty = 2, unitPrice = 1,000,000, discount = 10%, cost = 600,000
        // gross = 2,000,000 -> discount = 200,000 -> lineTotal = 1,800,000 -> cost = 1,200,000 -> profit = 600,000
        CalculationLine line1 = new CalculationLine(
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(1000000),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(600000),
                false
        );

        // Dòng 2: qty = 1, unitPrice = 500,000, discount = 0%, cost = 300,000
        // gross = 500,000 -> discount = 0 -> lineTotal = 500,000 -> cost = 300,000 -> profit = 200,000
        CalculationLine line2 = new CalculationLine(
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(500000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(300000),
                false
        );

        QuotationTotals totals = calculator.calculate(
                List.of(line1, line2),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(50000)
        );

        // listPriceTotal = 2,000,000 + 500,000 = 2,500,000
        assertEquals(0, new BigDecimal("2500000.00").compareTo(totals.listPriceTotal()));
        // totalDiscount = 200,000
        assertEquals(0, new BigDecimal("200000.00").compareTo(totals.totalDiscount()));
        // netAmount = 2,500,000 - 200,000 = 2,300,000
        assertEquals(0, new BigDecimal("2300000.00").compareTo(totals.netAmount()));
        // vatAmount = 2,300,000 * 10% = 230,000
        assertEquals(0, new BigDecimal("230000.00").compareTo(totals.vatAmount()));
        // grandTotal = 2,300,000 + 230,000 + 50,000 = 2,580,000
        assertEquals(0, new BigDecimal("2580000.00").compareTo(totals.grandTotal()));
        // grossProfit = 2,300,000 - (1,200,000 + 300,000) = 800,000
        assertEquals(0, new BigDecimal("800000.00").compareTo(totals.grossProfit()));
        // marginPercent = 800,000 / 2,300,000 * 100 = 34.78%
        assertEquals(0, new BigDecimal("34.78").compareTo(totals.marginPercent()));
    }

    @Test
    @DisplayName("5.1 - Kiểm tra VAT 0% và 8%, phí vận chuyển = 0")
    void testVat0AndVat8() {
        CalculationLine line = new CalculationLine(
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1000000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(700000),
                false
        );

        // VAT 0%
        QuotationTotals totalsVat0 = calculator.calculate(List.of(line), BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(totalsVat0.vatAmount()));
        assertEquals(0, new BigDecimal("1000000.00").compareTo(totalsVat0.grandTotal()));

        // VAT 8%
        QuotationTotals totalsVat8 = calculator.calculate(List.of(line), BigDecimal.valueOf(8), BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("80000.00").compareTo(totalsVat8.vatAmount()));
        assertEquals(0, new BigDecimal("1080000.00").compareTo(totalsVat8.grandTotal()));
    }

    @Test
    @DisplayName("5.1 - Dòng combo có thành phần con không bị tính trùng lặp vào tổng báo giá")
    void testComboComponentLinesNotDoubleCounted() {
        // Dòng cha: Combo trọn gói 5,000,000
        CalculationLine comboRoot = new CalculationLine(
                BigDecimal.ONE,
                BigDecimal.valueOf(5000000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(3500000),
                false // dòng gốc
        );

        // Dòng con 1: Máy bơm (component)
        CalculationLine comp1 = new CalculationLine(
                BigDecimal.ONE,
                BigDecimal.valueOf(3000000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(2000000),
                true // dòng con
        );

        // Dòng con 2: Bộ lọc (component)
        CalculationLine comp2 = new CalculationLine(
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(1000000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(750000),
                true // dòng con
        );

        QuotationTotals totals = calculator.calculate(
                List.of(comboRoot, comp1, comp2),
                BigDecimal.valueOf(10),
                BigDecimal.ZERO
        );

        // Tổng chỉ tính trên comboRoot: 5,000,000; KHÔNG cộng thêm 3,000,000 + 2,000,000 của components
        assertEquals(0, new BigDecimal("5000000.00").compareTo(totals.listPriceTotal()));
        assertEquals(0, new BigDecimal("5000000.00").compareTo(totals.netAmount()));
        assertEquals(0, new BigDecimal("5500000.00").compareTo(totals.grandTotal()));
        assertEquals(0, new BigDecimal("1500000.00").compareTo(totals.grossProfit()));
    }

    @Test
    @DisplayName("5.1 - Làm tròn từng dòng trước khi cộng cho ra tổng khớp chính xác")
    void testPerLineRounding() {
        // Line 1: qty = 3, price = 33.333 -> gross = 99.999 -> rounded 100.00, discount 33% = 33.00 -> net = 67.00
        CalculationLine line1 = new CalculationLine(
                BigDecimal.valueOf(3),
                new BigDecimal("33.33"),
                BigDecimal.valueOf(33),
                BigDecimal.valueOf(20),
                false
        );
        // Line 2: qty = 1, price = 10.555 -> 10.56, discount 15% -> 1.58 -> net = 8.98
        CalculationLine line2 = new CalculationLine(
                BigDecimal.ONE,
                new BigDecimal("10.55"),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(5),
                false
        );

        QuotationTotals totals = calculator.calculate(List.of(line1, line2), BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal sumOfLines = totals.lineTotals().get(0).lineTotal().add(totals.lineTotals().get(1).lineTotal());
        assertEquals(0, sumOfLines.compareTo(totals.netAmount()));
    }

    @Test
    @DisplayName("5.1 - Lợi nhuận âm khi chiết khấu quá sâu (bán dưới giá vốn)")
    void testNegativeGrossProfitWhenDiscountExceedsCostMargin() {
        // Giá bán 1,000,000, Giá vốn 800,000, Chiết khấu 30% -> Giá thực bán 700,000 -> Lỗ 100,000
        CalculationLine line = new CalculationLine(
                BigDecimal.ONE,
                BigDecimal.valueOf(1000000),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(800000),
                false
        );

        QuotationTotals totals = calculator.calculate(List.of(line), BigDecimal.ZERO, BigDecimal.ZERO);

        assertTrue(totals.grossProfit().compareTo(BigDecimal.ZERO) < 0);
        assertEquals(0, new BigDecimal("-100000.00").compareTo(totals.grossProfit()));
        assertTrue(totals.marginPercent().compareTo(BigDecimal.ZERO) < 0);
        assertEquals(0, new BigDecimal("-14.29").compareTo(totals.marginPercent())); // -100k / 700k = -14.29%
    }
}
