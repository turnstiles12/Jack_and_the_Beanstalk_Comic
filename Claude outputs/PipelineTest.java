package com.beanstalk;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {

    private static final List<String> EXPECTED_TEXT = List.of(
            "Jack and his mother had nothing left to sell but Milky-White.",
            "Five magic beans, said the old man. For one cow.",
            "Beans! she cried. And out the window they went.");

    @Test
    void storyboardIsExactlyThePlannedThreePages() {
        assertEquals(3, Storyboard.PAGES.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 1, Storyboard.PAGES.get(i).number());
            assertEquals(EXPECTED_TEXT.get(i), Storyboard.PAGES.get(i).text());
        }
    }

    @Test
    void everyPromptEndsWithTheExactStyleString() {
        assertEquals("Modern 3D animated feature — rounded forms, large expressive eyes, soft rim light.",
                Storyboard.STYLE);
        for (Storyboard.Page p : Storyboard.PAGES) {
            for (String looks : new String[]{Storyboard.PHOTO_LOOKS, "with short curly brown hair"}) {
                String prompt = p.prompt(looks);
                assertTrue(prompt.endsWith(Storyboard.STYLE), "page " + p.number());
                assertEquals(1, prompt.split(java.util.regex.Pattern.quote(Storyboard.STYLE), -1).length - 1);
                assertFalse(prompt.contains("{LOOKS}"));
                assertFalse(prompt.toLowerCase().matches(".*\\b(text|caption|letters|sign saying)\\b.*"),
                        "prompt asks for text on page " + p.number());
            }
        }
    }

    @Test
    void composesExactlyThreePagesWithExactText() throws Exception {
        List<byte[]> imgs = new ArrayList<>();
        for (int i = 0; i < 3; i++) imgs.add(fake(i));
        byte[] pdf = new PdfComposer().compose(imgs, "Mason");
        Files.write(Path.of("target/test-book.pdf"), pdf);
        try (PDDocument d = Loader.loadPDF(pdf)) {
            assertEquals(3, d.getNumberOfPages());
            for (int i = 0; i < 3; i++) {
                PDFTextStripper s = new PDFTextStripper();
                s.setStartPage(i + 1); s.setEndPage(i + 1);
                assertEquals(EXPECTED_TEXT.get(i), s.getText(d).replaceAll("\\s+", " ").trim());
            }
        }
    }

    @Test
    void rejectsWrongImageCount() {
        assertThrows(IllegalArgumentException.class, () -> new PdfComposer().compose(List.of(new byte[0]), "Mason"));
    }

    private static byte[] fake(int i) throws Exception {
        BufferedImage im = new BufferedImage(864, 1152, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(80 + i * 8, 160, 230 - i * 6), 0, 1152, new Color(40, 110 + i * 5, 50)));
        g.fillRect(0, 0, 864, 1152);
        g.setColor(Color.ORANGE); g.fillOval(300, 380, 260, 260);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(im, "jpg", o);
        return o.toByteArray();
    }
}
