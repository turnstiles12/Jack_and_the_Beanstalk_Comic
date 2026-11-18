package com.beanstalk;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a finished (screen) book PDF into the two files Lulu prints from:
 * <ul>
 *   <li><b>interior</b>: every page enlarged slightly so the art runs past the trim, at trim size plus 0.125 in
 *       bleed on every side (6 x 9 in trim = 6.25 x 9.25 in pages);</li>
 *   <li><b>cover</b>: one spread at the size Lulu's cover-dimensions endpoint asks for (back | spine | front),
 *       front = page 1's illustration under the title, back = a plain panel.</li>
 * </ul>
 */
@Component
public class PrintFiles {

    public static final float BLEED = 9f;   // 0.125 in, in points
    private static final float SAFE = 36f;  // keep cover text 0.5 in inside the trim

    /** Trim size in points from a Lulu pod_package_id, which starts with e.g. "0600X0900" (6.00 x 9.00 in). */
    public static float[] trimPt(String podPackageId) {
        if (podPackageId == null || !podPackageId.matches("\\d{4}X\\d{4}.*"))
            throw new IllegalArgumentException("Unexpected pod_package_id: " + podPackageId);
        float w = Integer.parseInt(podPackageId.substring(0, 4)) / 100f * 72f;
        float h = Integer.parseInt(podPackageId.substring(5, 9)) / 100f * 72f;
        return new float[] { w, h };
    }

    /** Every page of {@code bookPdf} scaled to cover trim + bleed (centred; the overflow falls in the bleed). */
    public byte[] interior(byte[] bookPdf, float trimW, float trimH) throws IOException {
        float pw = trimW + 2 * BLEED, ph = trimH + 2 * BLEED;
        try (PDDocument src = Loader.loadPDF(bookPdf); PDDocument out = new PDDocument()) {
            LayerUtility layers = new LayerUtility(out);
            for (int i = 0; i < src.getNumberOfPages(); i++) {
                PDRectangle box = src.getPage(i).getMediaBox();
                PDFormXObject form = layers.importPageAsForm(src, i);
                PDPage page = new PDPage(new PDRectangle(pw, ph));
                page.setTrimBox(new PDRectangle(BLEED, BLEED, trimW, trimH));
                page.setBleedBox(new PDRectangle(pw, ph));
                out.addPage(page);
                float s = Math.max(pw / box.getWidth(), ph / box.getHeight());
                try (PDPageContentStream cs = new PDPageContentStream(out, page)) {
                    cs.saveGraphicsState();
                    cs.transform(new Matrix(s, 0, 0, s,
                            (pw - box.getWidth() * s) / 2f - box.getLowerLeftX() * s,
                            (ph - box.getHeight() * s) / 2f - box.getLowerLeftY() * s));
                    cs.drawForm(form);
                    cs.restoreGraphicsState();
                }
            }
            return save(out);
        }
    }

    /**
     * The wrap-around cover. {@code coverW x coverH} is the full sheet from Lulu's cover-dimensions endpoint
     * (bleed included); the spine is whatever is left after the two trimmed covers and the outer bleed.
     */
    public byte[] cover(byte[] bookPdf, float coverW, float coverH, float trimW, String title, String heroName)
            throws IOException {
        try (PDDocument src = Loader.loadPDF(bookPdf); PDDocument out = new PDDocument()) {
            PDType0Font font;
            try (InputStream in = getClass().getResourceAsStream("/fonts/StoryFont.ttf")) {
                font = PDType0Font.load(out, in);
            }
            PDPage page = new PDPage(new PDRectangle(coverW, coverH));
            page.setTrimBox(new PDRectangle(BLEED, BLEED, coverW - 2 * BLEED, coverH - 2 * BLEED));
            out.addPage(page);

            float spine = Math.max(0, coverW - 2 * BLEED - 2 * trimW);
            float frontX = BLEED + trimW + spine;          // front cover's left trim edge
            float frontW = coverW - frontX;                // front cover incl. right bleed
            float backW = BLEED + trimW;                   // back cover incl. left bleed

            try (PDPageContentStream cs = new PDPageContentStream(out, page)) {
                // Back cover and spine: deep green panel with a short line of text.
                cs.setNonStrokingColor(0.12f, 0.31f, 0.20f);
                cs.addRect(0, 0, frontX, coverH);
                cs.fill();
                cs.setNonStrokingColor(1f, 1f, 1f);
                String back = PdfComposer.clean(font, "A personalized comic starring " + heroName);
                centred(cs, font, back, 14f, BLEED + trimW / 2f, BLEED + SAFE + 20f, trimW - 2 * SAFE);
                centred(cs, font, "Once Upon a Comic", 11f, BLEED + trimW / 2f, BLEED + SAFE, trimW - 2 * SAFE);

                // Front cover: page 1's illustration, cover-fit and clipped to the front panel.
                PDImageXObject art = src.getNumberOfPages() > 0 ? firstImage(src.getPage(0).getResources()) : null;
                if (art != null) {
                    PDImageXObject img = PDImageXObject.createFromByteArray(out, toJpeg(art), "front");
                    float s = Math.max(frontW / img.getWidth(), coverH / img.getHeight());
                    float w = img.getWidth() * s, h = img.getHeight() * s;
                    cs.saveGraphicsState();
                    cs.addRect(frontX, 0, frontW, coverH);
                    cs.clip();
                    cs.drawImage(img, frontX + (frontW - w) / 2f, (coverH - h) / 2f, w, h);
                    cs.restoreGraphicsState();
                } else {
                    cs.setNonStrokingColor(0.86f, 0.25f, 0.20f);
                    cs.addRect(frontX, 0, frontW, coverH);
                    cs.fill();
                }

                // Title panel at the top of the front cover, inside the safe area.
                float panelX = frontX + SAFE, panelW = trimW - 2 * SAFE;
                float titleSize = fit(font, PdfComposer.clean(font, title), 28f, panelW - 24f);
                String starring = PdfComposer.clean(font, "starring " + heroName);
                float starSize = fit(font, starring, 18f, panelW - 24f);
                float panelH = titleSize * 1.3f + starSize * 1.3f + 28f;
                float panelY = coverH - BLEED - SAFE - panelH;
                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(0.65f);
                cs.saveGraphicsState();
                cs.setGraphicsStateParameters(gs);
                cs.setNonStrokingColor(0.04f, 0.03f, 0.08f);
                cs.addRect(panelX, panelY, panelW, panelH);
                cs.fill();
                cs.restoreGraphicsState();
                cs.setNonStrokingColor(1f, 1f, 1f);
                float cx = panelX + panelW / 2f;
                centred(cs, font, PdfComposer.clean(font, title), titleSize, cx, panelY + panelH - 14f - titleSize, panelW);
                centred(cs, font, starring, starSize, cx, panelY + 14f, panelW);
            }
            return save(out);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The first raster image on a page (the full-bleed illustration PdfComposer draws), looking inside forms too. */
    static PDImageXObject firstImage(PDResources res) throws IOException {
        if (res == null) return null;
        List<PDFormXObject> forms = new ArrayList<>();
        for (COSName name : res.getXObjectNames()) {
            PDXObject x = res.getXObject(name);
            if (x instanceof PDImageXObject img) return img;
            if (x instanceof PDFormXObject f) forms.add(f);
        }
        for (PDFormXObject f : forms) {
            PDImageXObject img = firstImage(f.getResources());
            if (img != null) return img;
        }
        return null;
    }

    private static byte[] toJpeg(PDImageXObject img) throws IOException {
        java.awt.image.BufferedImage bi = img.getImage();
        java.awt.image.BufferedImage rgb = new java.awt.image.BufferedImage(bi.getWidth(), bi.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = rgb.createGraphics();
        g.drawImage(bi, 0, 0, null);
        g.dispose();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(rgb, "jpg", b);
        return b.toByteArray();
    }

    private static float fit(PDType0Font font, String s, float size, float maxW) throws IOException {
        while (size > 8f && width(font, s, size) > maxW) size -= 1f;
        return size;
    }

    private static float width(PDType0Font font, String s, float size) throws IOException {
        return font.getStringWidth(s) / 1000f * size;
    }

    private static void centred(PDPageContentStream cs, PDType0Font font, String s, float size, float cx, float y,
                                float maxW) throws IOException {
        size = fit(font, s, size, maxW);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(cx - width(font, s, size) / 2f, y);
        cs.showText(s);
        cs.endText();
    }

    private static byte[] save(PDDocument d) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        d.save(b);
        return b.toByteArray();
    }
}
