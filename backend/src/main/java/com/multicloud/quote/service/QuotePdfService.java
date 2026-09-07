package com.multicloud.quote.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.multicloud.quote.dto.response.QuoteItemResponse;
import com.multicloud.quote.dto.response.QuoteResponse;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 견적 결과를 고객 제안용 PDF 견적서로 렌더링한다.
 *
 * <p>한글 출력은 openpdf-fonts-extra가 제공하는 CJK 폰트 메트릭(HYSMyeongJo-Medium)을 사용한다.
 * 폰트를 임베드하지 않으므로 PDF 용량이 작고 폰트 파일을 저장소에 동봉할 필요가 없다.
 */
@Service
public class QuotePdfService {

    private static final String CJK_FONT = "HYSMyeongJo-Medium";
    private static final String CJK_ENCODING = "UniKS-UCS2-H";

    private static final Color HEADER_BG = new Color(0x1F, 0x29, 0x37);
    private static final Color BEST_BG = new Color(0xEC, 0xFD, 0xF5);
    private static final Color BORDER = new Color(0xD1, 0xD5, 0xDB);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);

    private static final DateTimeFormatter CREATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BaseFont baseFont;

    public QuotePdfService() {
        try {
            this.baseFont = BaseFont.createFont(CJK_FONT, CJK_ENCODING, BaseFont.NOT_EMBEDDED);
        } catch (IOException e) {
            throw new IllegalStateException("PDF 한글 폰트를 초기화하지 못했습니다.", e);
        }
    }

    public byte[] render(QuoteResponse quote) {
        Document document = new Document(PageSize.A4, 42, 42, 48, 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("멀티클라우드 견적서 - " + quote.workloadName());
            document.open();

            document.add(title("멀티클라우드 견적서"));
            document.add(subtitle("견적번호 Q-%d · 생성일 %s"
                    .formatted(quote.quoteId(), quote.createdAt().format(CREATED_AT_FORMAT))));
            document.add(spacer(18));

            document.add(sectionHeading("워크로드 스펙"));
            document.add(specTable(quote));
            document.add(spacer(18));

            document.add(sectionHeading("벤더별 월 비용 비교 (USD)"));
            document.add(comparisonTable(quote));
            document.add(spacer(12));

            savingsSummary(quote).ifPresent(document::add);

            document.add(spacer(24));
            document.add(footer());
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("견적서 PDF 생성에 실패했습니다. quoteId=" + quote.quoteId(), e);
        }

        return out.toByteArray();
    }

    /** 한글 파일명이 깨지지 않도록 ASCII 파일명을 쓰고, 본문에 워크로드명을 담는다. */
    public String fileName(QuoteResponse quote) {
        return "quote-%d-%s.pdf".formatted(
                quote.quoteId(), quote.createdAt().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    private Paragraph title(String text) {
        Paragraph paragraph = new Paragraph(text, font(20, Font.BOLD, Color.BLACK));
        paragraph.setSpacingAfter(2);
        return paragraph;
    }

    private Paragraph subtitle(String text) {
        return new Paragraph(text, font(10, Font.NORMAL, MUTED));
    }

    private Paragraph sectionHeading(String text) {
        Paragraph paragraph = new Paragraph(text, font(12, Font.BOLD, Color.BLACK));
        paragraph.setSpacingAfter(6);
        return paragraph;
    }

    private Paragraph spacer(float height) {
        Paragraph paragraph = new Paragraph(" ", font(1, Font.NORMAL, Color.WHITE));
        paragraph.setSpacingAfter(height);
        return paragraph;
    }

    private PdfPTable specTable(QuoteResponse quote) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1, 2, 1, 2});
        table.setWidthPercentage(100);

        addSpecRow(table, "워크로드명", quote.workloadName(), "리전", quote.region());
        addSpecRow(table, "vCPU", quote.vcpu() + " core", "메모리", quote.memoryGb() + " GB");
        addSpecRow(table, "스토리지", quote.storageGb() + " GB", "OS", quote.os().name());
        return table;
    }

    private void addSpecRow(PdfPTable table, String label1, String value1, String label2, String value2) {
        table.addCell(labelCell(label1));
        table.addCell(valueCell(value1));
        table.addCell(labelCell(label2));
        table.addCell(valueCell(value2));
    }

    private PdfPTable comparisonTable(QuoteResponse quote) throws DocumentException {
        // 벤더 열은 "AZURE (최저가)"가 한 줄에 들어가도록 넉넉히 잡는다.
        PdfPTable table = new PdfPTable(new float[]{1.9f, 2.2f, 1.3f, 1.3f, 1.5f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        for (String header : List.of("벤더", "매칭 SKU", "컴퓨트", "스토리지", "합계 (월)")) {
            table.addCell(headerCell(header));
        }

        String cheapest = cheapestVendor(quote);
        for (QuoteItemResponse item : quote.results()) {
            boolean best = item.vendor().name().equals(cheapest);
            Color background = best ? BEST_BG : Color.WHITE;

            table.addCell(bodyCell(item.vendor().name() + (best ? " (최저가)" : ""),
                    Element.ALIGN_LEFT, background, best));
            table.addCell(bodyCell(
                    item.matchedSku() != null ? item.matchedSku() : orDash(item.note()),
                    Element.ALIGN_LEFT, background, false));
            table.addCell(bodyCell(usd(item.computeCostUsd()), Element.ALIGN_RIGHT, background, false));
            table.addCell(bodyCell(usd(item.storageCostUsd()), Element.ALIGN_RIGHT, background, false));
            table.addCell(bodyCell(usd(item.monthlyCostUsd()), Element.ALIGN_RIGHT, background, true));
        }
        return table;
    }

    private java.util.Optional<Paragraph> savingsSummary(QuoteResponse quote) {
        List<QuoteItemResponse> priced = quote.results().stream()
                .filter(item -> item.monthlyCostUsd() != null)
                .sorted(Comparator.comparing(QuoteItemResponse::monthlyCostUsd))
                .toList();
        if (priced.size() < 2) {
            return java.util.Optional.empty();
        }

        QuoteItemResponse cheapest = priced.get(0);
        QuoteItemResponse costliest = priced.get(priced.size() - 1);
        BigDecimal monthlyGap = costliest.monthlyCostUsd().subtract(cheapest.monthlyCostUsd());
        if (monthlyGap.signum() <= 0) {
            return java.util.Optional.empty();
        }

        String text = "%s 선택 시 %s 대비 월 %s, 연 %s 절감됩니다.".formatted(
                cheapest.vendor().name(), costliest.vendor().name(),
                usd(monthlyGap), usd(monthlyGap.multiply(BigDecimal.valueOf(12))));
        return java.util.Optional.of(new Paragraph(text, font(11, Font.BOLD, new Color(0x04, 0x78, 0x57))));
    }

    private Paragraph footer() {
        Paragraph paragraph = new Paragraph(
                "본 견적은 각 클라우드 벤더의 공개 가격 API에서 수집한 온디맨드 단가를 월 730시간 기준으로 "
                        + "환산한 참고용 산출물이며, 약정/할인/네트워크·부가 서비스 비용은 포함하지 않습니다. "
                        + "실제 청구액과 다를 수 있습니다.",
                font(8, Font.NORMAL, MUTED));
        paragraph.setSpacingBefore(8);
        return paragraph;
    }

    private String cheapestVendor(QuoteResponse quote) {
        return quote.results().stream()
                .filter(item -> item.monthlyCostUsd() != null)
                .min(Comparator.comparing(QuoteItemResponse::monthlyCostUsd))
                .map(item -> item.vendor().name())
                .orElse(null);
    }

    private PdfPCell labelCell(String text) {
        PdfPCell cell = cell(text, font(9, Font.NORMAL, MUTED), Element.ALIGN_LEFT);
        cell.setBackgroundColor(new Color(0xF9, 0xFA, 0xFB));
        return cell;
    }

    private PdfPCell valueCell(String text) {
        return cell(text, font(10, Font.NORMAL, Color.BLACK), Element.ALIGN_LEFT);
    }

    private PdfPCell headerCell(String text) {
        PdfPCell cell = cell(text, font(9, Font.BOLD, Color.WHITE), Element.ALIGN_LEFT);
        cell.setBackgroundColor(HEADER_BG);
        return cell;
    }

    private PdfPCell bodyCell(String text, int alignment, Color background, boolean bold) {
        PdfPCell cell = cell(text, font(10, bold ? Font.BOLD : Font.NORMAL, Color.BLACK), alignment);
        cell.setBackgroundColor(background);
        return cell;
    }

    private PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(BORDER);
        return cell;
    }

    private Font font(float size, int style, Color color) {
        return new Font(baseFont, size, style, color);
    }

    private String usd(BigDecimal value) {
        return value == null ? "-" : "$%,.2f".formatted(value);
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
