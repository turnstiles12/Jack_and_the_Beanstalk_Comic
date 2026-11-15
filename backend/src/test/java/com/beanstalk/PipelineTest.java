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
      for (Story story : Stories.ALL) {
        List<Storyboard.Page> all = story.allPages();
        assertEquals(20, all.size(), story.id());
        for (int i = 0; i < 20; i++) assertEquals(i + 1, all.get(i).number(), story.id());
        for (Storyboard.Page p : all) {
            assertTrue(p.prompt().endsWith(Storyboard.STYLE), story.id() + " page " + p.number());
            assertEquals(1, p.prompt().split(java.util.regex.Pattern.quote(Storyboard.STYLE), -1).length - 1);
            assertFalse(p.prompt().toLowerCase().matches(".*\\b(text|caption|letters|sign saying)\\b.*"),
                    "prompt asks for text on " + story.id() + " page " + p.number());
        }
      }
        assertEquals("Modern 3D animated feature — rounded forms, large expressive eyes, soft rim light.",
                Storyboard.STYLE);
    }

    /** Both stories are one template: the same page numbers, text zones and a story text on every page 1-19. */
    @Test
    void storiesShareOneLayoutTemplate() {
        assertEquals(2, Stories.ALL.size());
        List<Storyboard.Page> jack = Stories.JACK.allPages(), red = Stories.RED.allPages();
        for (int i = 0; i < 20; i++) {
            assertEquals(jack.get(i).zone(), red.get(i).zone(), "text zone differs on page " + (i + 1));
            assertEquals(jack.get(i).text() == null, red.get(i).text() == null, "text presence differs on page " + (i + 1));
        }
        for (Storyboard.Page p : Stories.RED.pages()) assertFalse(p.text().isBlank());
        assertNull(Stories.find("nope"));
        assertSame(Stories.JACK, Stories.find(""));
        assertSame(Stories.RED, Stories.find("red"));
    }

    @Test
    void softenerCalmsScenesButNeverTouchesTextOrStyle() {
        for (Story story : Stories.ALL) {
            for (Storyboard.Page p : story.allPages()) {
                for (int level = 0; level <= 3; level++) {
                    Storyboard.Page s = Softener.soften(p, level);
                    assertEquals(p.text(), s.text());
                    assertEquals(p.zone(), s.zone());
                    assertTrue(s.prompt().endsWith(Storyboard.STYLE));
                }
                String soft = Softener.soften(p, 2).prompt().toLowerCase();
                assertFalse(soft.contains("axe") || soft.contains("toothy") || soft.contains("fierce"), story.id() + " p" + p.number());
            }
        }
        assertTrue(Softener.isFlagged(new RuntimeException("Cloudflare HTTP 400: AiError: Your output has been flagged.")));
        assertFalse(Softener.isFlagged(new RuntimeException("Cloudflare HTTP 429")));
    }

    @Test
    void pngIsConvertedToJpegAndJpegIsLeftAlone() throws Exception {
        BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(im, "png", png);
        assertFalse(Jpegs.isJpeg(png.toByteArray()));
        byte[] jpg = Jpegs.ensure(png.toByteArray());
        assertTrue(Jpegs.isJpeg(jpg));
        assertSame(jpg, Jpegs.ensure(jpg));
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
        for (Story story : Stories.ALL) {
            byte[] pdf = new PdfComposer().compose(imgs, "Mason", "I love dinosaurs and soccer. Favorite food: pizza! 😀", story);
            Files.write(Path.of("target/test-book-" + story.id() + ".pdf"), pdf);
            try (PDDocument d = Loader.loadPDF(pdf)) {
                assertEquals(20, d.getNumberOfPages(), story.id());
                for (int i = 0; i < 19; i++) {
                    PDFTextStripper s = new PDFTextStripper();
                    s.setStartPage(i + 1); s.setEndPage(i + 1);
                    String t = s.getText(d).replaceAll("\\s+", " ").trim();
                    assertEquals(story.pages().get(i).text(), t, story.id() + " page " + (i + 1));
                }
                PDFTextStripper s = new PDFTextStripper(); s.setStartPage(20); s.setEndPage(20);
                String t = s.getText(d);
                assertTrue(t.contains("Mason") && t.contains("pizza"), story.id());
            }
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
