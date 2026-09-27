package com.clubhub.certificate;

import com.clubhub.event.QrCodes;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Renders one certificate as an A4 landscape PDF with a QR code linking to its public verification page. */
@Component
public class CertificatePdfRenderer {

    // certificates are read by people in India: dates shown in IST, even though storage is UTC
    static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public byte[] render(Certificate certificate, String clubName, String verifyUrl) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rectangle page = PageSize.A4.rotate();
        Document document = new Document(page, 60, 60, 70, 60);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.addTitle(certificate.getTitle() + " - " + certificate.getRecipientName());
        document.addCreator("ClubHub");
        document.open();

        drawBorder(writer.getDirectContent(), page);

        document.add(centered(clubName.toUpperCase(Locale.ENGLISH), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16), 30));
        document.add(centered(certificate.getTitle(), FontFactory.getFont(FontFactory.TIMES_BOLD, 34), 24));
        document.add(centered("This is to certify that", FontFactory.getFont(FontFactory.TIMES_ITALIC, 16), 12));
        document.add(centered(certificate.getRecipientName(), FontFactory.getFont(FontFactory.TIMES_BOLD, 28), 12));
        document.add(centered(certificate.getDescription(), FontFactory.getFont(FontFactory.TIMES_ROMAN, 16), 30));
        document.add(centered("Issued on " + DATE.format(certificate.getIssuedAt().atZone(DISPLAY_ZONE)),
                FontFactory.getFont(FontFactory.HELVETICA, 12), 4));
        document.add(centered("Certificate ID: " + certificate.getId(), FontFactory.getFont(FontFactory.COURIER, 9), 2));
        document.add(centered("Verify at " + verifyUrl, FontFactory.getFont(FontFactory.HELVETICA, 8), 0));

        document.add(qrCode(verifyUrl, page));

        document.close();
        return out.toByteArray();
    }

    private static Image qrCode(String verifyUrl, Rectangle page) {
        try {
            Image qr = Image.getInstance(QrCodes.png(verifyUrl, 300));
            qr.scaleAbsolute(90, 90);
            qr.setAbsolutePosition(page.getWidth() - 60 - 90, 50);
            return qr;
        } catch (IOException e) {
            throw new UncheckedIOException(e); // in-memory PNG: can't really happen
        }
    }

    private static Paragraph centered(String text, Font font, float spacingAfter) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private static void drawBorder(PdfContentByte canvas, Rectangle page) {
        canvas.setLineWidth(3f);
        canvas.rectangle(25, 25, page.getWidth() - 50, page.getHeight() - 50);
        canvas.stroke();
        canvas.setLineWidth(0.8f);
        canvas.rectangle(33, 33, page.getWidth() - 66, page.getHeight() - 66);
        canvas.stroke();
    }
}
