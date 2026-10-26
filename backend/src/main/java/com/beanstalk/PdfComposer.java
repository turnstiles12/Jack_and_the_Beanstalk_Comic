package com.beanstalk;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the final 20-page PDF. Images are drawn full-bleed (cover-fit, centre-cropped);
 * ALL text is drawn here as real PDF text on a translucent dark panel in the page's quiet zone.
 */
@Component
public class PdfComposer {

    /** Page width in points (6 in). Height follows the generated art's aspect ratio, so images stay full-bleed uncropped. */
    public static final float PAGE_WIDTH = 432f;
    private static final float MARGIN = 26f;
    private static final float PAD = 14f;

    /** One styled paragraph for the layout engine. */
    private record Para(String text, float size, float spaceAfter) {}
    private record Line(String text, float size, float width) {}

    public byte[] compose(List<byte[]> images, String childName, String details) throws IOException {
        if (images.size() != 20) throw new IllegalArgumentException("Need exactly 20 images, got " + images.size());

        java.awt.image.BufferedImage first = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(images.get(0)));
        if (first == null) throw new IOException("Page 1 image is not a readable JPEG/PNG");
        float h = Math.max(300f, Math.min(800f, PAGE_WIDTH * first.getHeight() / first.getWidth()));
        final PDRectangle size = new PDRectangle(PAGE_WIDTH, h);

        try (PDDocument doc = new PDDocument()) {
            PDType0Font font;
            try (InputStream in = getClass().getResourceAsStream("/fonts/StoryFont.ttf")) {
                font = PDType0Font.load(doc, in);
            }
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("Jack and the Beanstalk — starring " + clean(font, childName));
            doc.setDocumentInformation(info);

            // Pages 1-19: story pages, text exactly as hard-coded in the storyboard.
            for (Storyboard.Page p : Storyboard.PAGES) {
                PDPage page = new PDPage(size);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    drawFullBleed(doc, cs, images.get(p.number() - 1), size);
                    List<Para> paras = List.of(new Para(p.text(), 22f, 0));
                    drawPanel(cs, font, paras, p.zone(), 0.30f, size);
                }
            }

            // Page 20: About Me.
            PDPage about = new PDPage(size);
            doc.addPage(about);
            try (PDPageContentStream cs = new PDPageContentStream(doc, about)) {
                drawFullBleed(doc, cs, images.get(19), size);
                List<Para> paras = new ArrayList<>();
                paras.add(new Para("ABOUT ME", 14f, 4f));
                paras.add(new Para(clean(font, childName), 34f, 8f));
                String d = clean(font, details);
                if (!d.isBlank()) paras.add(new Para(d, 16f, 0));
                drawPanel(cs, font, paras, Storyboard.Zone.BOTTOM, 0.46f, size);
            }

            if (doc.getNumberOfPages() != 20) throw new IllegalStateException("PDF must have 20 pages");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Scale to cover the whole page, centre-crop overflow. Edge to edge, no margins. */
    private void drawFullBleed(PDDocument doc, PDPageContentStream cs, byte[] img, PDRectangle size) throws IOException {
        PDImageXObject x = PDImageXObject.createFromByteArray(doc, img, "page");
        float pw = size.getWidth(), ph = size.getHeight();
        float s = Math.max(pw / x.getWidth(), ph / x.getHeight());
        float w = x.getWidth() * s, h = x.getHeight() * s;
        cs.drawImage(x, (pw - w) / 2f, (ph - h) / 2f, w, h);
    }

    /**
     * Draws a translucent dark rounded panel in the TOP or BOTTOM zone with centred white text.
     * Font size shrinks (down to 55%) until the block fits within maxHeightFrac of the page.
     */
    private void drawPanel(PDPageContentStream cs, PDType0Font font, List<Para> paras,
                           Storyboard.Zone zone, float maxHeightFrac, PDRectangle pageSize) throws IOException {
        float pw = pageSize.getWidth(), ph = pageSize.getHeight();
        float maxTextW = pw - 2 * MARGIN - 2 * PAD;
        float maxH = ph * maxHeightFrac;

        float scale = 1f;
        List<List<Line>> laid = null;
        float blockH = 0;
        for (; scale >= 0.55f; scale -= 0.05f) {
            laid = new ArrayList<>();
            blockH = 0;
            for (Para p : paras) {
                List<Line> lines = wrap(font, p.text(), p.size() * scale, maxTextW);
                laid.add(lines);
                blockH += lines.size() * p.size() * scale * 1.25f + p.spaceAfter();
            }
            if (blockH + 2 * PAD <= maxH) break;
        }
        float panelH = blockH + 2 * PAD;
        float panelW = pw - 2 * MARGIN;
        float panelX = MARGIN;
        float panelY = zone == Storyboard.Zone.BOTTOM ? MARGIN : ph - MARGIN - panelH;

        // translucent panel
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(0.62f);
        cs.saveGraphicsState();
        cs.setGraphicsStateParameters(gs);
        cs.setNonStrokingColor(0.04f, 0.03f, 0.08f);
        roundedRect(cs, panelX, panelY, panelW, panelH, 14f);
        cs.fill();
        cs.restoreGraphicsState();

        // text
        cs.setNonStrokingColor(1f, 1f, 1f);
        float y = panelY + panelH - PAD;
        for (int i = 0; i < paras.size(); i++) {
            Para p = paras.get(i);
            for (Line l : laid.get(i)) {
                float size = l.size();
                y -= size * 1.25f;
                float x = panelX + (panelW - l.width()) / 2f;
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(x, y + size * 0.25f);
                cs.showText(l.text());
                cs.endText();
            }
            y -= p.spaceAfter();
        }
    }

    private List<Line> wrap(PDType0Font font, String text, float size, float maxW) throws IOException {
        List<Line> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (width(font, trial, size) <= maxW || cur.length() == 0) {
                cur = new StringBuilder(trial);
            } else {
                lines.add(new Line(cur.toString(), size, width(font, cur.toString(), size)));
                cur = new StringBuilder(word);
            }
        }
        if (cur.length() > 0) lines.add(new Line(cur.toString(), size, width(font, cur.toString(), size)));
        return lines;
    }

    private float width(PDType0Font font, String s, float size) throws IOException {
        return font.getStringWidth(s) / 1000f * size;
    }

    /** Drops control chars / glyphs the embedded font cannot encode (emoji etc.) so PDFBox never throws. */
    static String clean(PDType0Font font, String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        s.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp)) { b.append(' '); return; }
            if (Character.isISOControl(cp)) return;
            String ch = new String(Character.toChars(cp));
            try { font.encode(ch); b.append(ch); } catch (Exception ignored) { /* skip unsupported glyph */ }
        });
        return b.toString().replaceAll(" +", " ").trim();
    }

    private void roundedRect(PDPageContentStream cs, float x, float y, float w, float h, float r) throws IOException {
        float k = 0.5523f * r;
        cs.moveTo(x + r, y);
        cs.lineTo(x + w - r, y);
        cs.curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
        cs.lineTo(x + w, y + h - r);
        cs.curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h);
        cs.lineTo(x + r, y + h);
        cs.curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r);
        cs.lineTo(x, y + r);
        cs.curveTo(x, y + r - k, x + r - k, y, x + r, y);
        cs.closePath();
    }
}
