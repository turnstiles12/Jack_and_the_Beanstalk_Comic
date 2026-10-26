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

    @Test
    void everyPromptEndsWithTheExactStyleString() {
        List<Storyboard.Page> all = new ArrayList<>(Storyboard.PAGES);
        all.add(Storyboard.ABOUT_ME_PORTRAIT);
        assertEquals(20, all.size());
        for (int i = 0; i < 20; i++) assertEquals(i + 1, all.get(i).number());
        for (Storyboard.Page p : all) {
            assertTrue(p.prompt().endsWith(Storyboard.STYLE), "page " + p.number());
            assertEquals(1, p.prompt().split(java.util.regex.Pattern.quote(Storyboard.STYLE), -1).length - 1);
            assertFalse(p.prompt().toLowerCase().matches(".*\\b(text|caption|letters|sign saying)\\b.*"),
                    "prompt asks for text on page " + p.number());
        }
        assertEquals("Modern 3D animated feature — rounded forms, large expressive eyes, soft rim light.",
                Storyboard.STYLE);
    }

    @Test
    void requiredScenesExist() {
        String all = Storyboard.PAGES.toString().toLowerCase();
        assertTrue(all.contains("planting the magic seeds"));
        assertTrue(all.contains("climbing the giant beanstalk"));
    }

    @Test
    void composesExactly20PagesWithExactText() throws Exception {
        List<byte[]> imgs = new ArrayList<>();
        for (int i = 0; i < 20; i++) imgs.add(fake(i));
        byte[] pdf = new PdfComposer().compose(imgs, "Mason", "I love dinosaurs and soccer. Favorite food: pizza! 😀");
        Files.write(Path.of("target/test-book.pdf"), pdf);
        try (PDDocument d = Loader.loadPDF(pdf)) {
            assertEquals(20, d.getNumberOfPages());
            for (int i = 0; i < 3; i++) {
                PDFTextStripper s = new PDFTextStripper();
                s.setStartPage(i + 1); s.setEndPage(i + 1);
                String t = s.getText(d).replaceAll("\\s+", " ").trim();
                assertEquals(Storyboard.PAGES.get(i).text(), t);
            }
            PDFTextStripper s = new PDFTextStripper(); s.setStartPage(20); s.setEndPage(20);
            String t = s.getText(d);
            assertTrue(t.contains("Mason") && t.contains("pizza"));
        }
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
