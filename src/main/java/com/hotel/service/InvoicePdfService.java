package com.hotel.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;

// Hoa don PDF: dung template pdf/invoice (cung du lieu voi trang hoa don) -> PDF A4, nhung font Be Vietnam Pro
// de hien dung tieng Viet tren moi may. Chi nhan du lieu da dung san (PaymentService.buildInvoiceData) - khong
// phu thuoc PaymentService de tranh vong phu thuoc (PaymentService goi lai lop nay khi gui email).
@Service
public class InvoicePdfService {

    private static final String FONT_FAMILY = "Be Vietnam Pro";

    private final ITemplateEngine templateEngine;

    public InvoicePdfService(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] render(Map<String, Object> invoiceData) {
        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariables(invoiceData);
        String html = templateEngine.process("pdf/invoice", context);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> font("BeVietnamPro-Regular.ttf"), FONT_FAMILY, 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> font("BeVietnamPro-Bold.ttf"), FONT_FAMILY, 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Khong tao duoc hoa don PDF", ex);
        }
    }

    // Ten file tai ve / dinh kem, VD HoaDon-HD20261016-15.pdf
    public static String fileName(String invoiceNo) {
        return "HoaDon-" + invoiceNo + ".pdf";
    }

    private static InputStream font(String file) {
        InputStream in = InvoicePdfService.class.getResourceAsStream("/fonts/" + file);
        if (in == null) {
            throw new IllegalStateException("Thieu font /fonts/" + file);
        }
        return in;
    }
}
