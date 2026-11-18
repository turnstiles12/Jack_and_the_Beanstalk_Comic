package com.beanstalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrintFilesTest {

    private static byte[] book() throws Exception {
        List<byte[]> imgs = new ArrayList<>();
        for (int i = 0; i < Story.PAGE_COUNT; i++) {
            BufferedImage im = new BufferedImage(768, 1152, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            g.setColor(new Color(40 + i * 10, 120, 200 - i * 5));
            g.fillRect(0, 0, 768, 1152);
            g.dispose();
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            ImageIO.write(im, "jpg", o);
            imgs.add(o.toByteArray());
        }
        return new PdfComposer().compose(imgs, "Mason", "Likes trains", Stories.JACK);
    }

    @Test
    void trimSizeComesFromThePodPackageId() {
        float[] t = PrintFiles.trimPt("0600X0900FCSTDSS080CW444GXX");
        assertEquals(432f, t[0], 0.01);
        assertEquals(648f, t[1], 0.01);
        assertThrows(IllegalArgumentException.class, () -> PrintFiles.trimPt("bad"));
    }

    @Test
    void interiorHasEveryPageAtTrimPlusBleed() throws Exception {
        byte[] interior = new PrintFiles().interior(book(), 432f, 648f);
        try (PDDocument d = Loader.loadPDF(interior)) {
            assertEquals(Story.PAGE_COUNT, d.getNumberOfPages());
            for (var p : d.getPages()) {
                PDRectangle box = p.getMediaBox();
                assertEquals(450f, box.getWidth(), 0.01);     // 6 in + 2 x 0.125 in
                assertEquals(666f, box.getHeight(), 0.01);    // 9 in + 2 x 0.125 in
                assertEquals(432f, p.getTrimBox().getWidth(), 0.01);
            }
            // The story text is still real, selectable text after scaling.
            assertTrue(new PDFTextStripper().getText(d).contains("Mason"));
        }
    }

    @Test
    void coverIsOneSpreadAtLulusSizeWithTitleAndName() throws Exception {
        byte[] cover = new PrintFiles().cover(book(), 882f, 666f, 432f, "Jack and the Beanstalk", "Mason");
        try (PDDocument d = Loader.loadPDF(cover)) {
            assertEquals(1, d.getNumberOfPages());
            assertEquals(882f, d.getPage(0).getMediaBox().getWidth(), 0.01);
            assertEquals(666f, d.getPage(0).getMediaBox().getHeight(), 0.01);
            String text = new PDFTextStripper().getText(d);
            assertTrue(text.contains("Jack and the Beanstalk"));
            assertTrue(text.contains("starring Mason"));
            assertNotNull(PrintFiles.firstImage(d.getPage(0).getResources()), "front cover art missing");
        }
    }

    @Test
    void shippingAddressIsCheckedBeforeAnythingIsQuoted() {
        Map<String, Object> a = new HashMap<>(Map.of("name", "Ann Lee", "street1", "1 Main St", "city", "Albany",
                "stateCode", "ny", "postcode", "12207", "countryCode", "us", "phoneNumber", "+1 518 555 0100"));
        LuluClient.Address ok = PrintOrderController.address(a, "ann@example.com");
        assertEquals("NY", ok.stateCode());
        assertEquals("US", ok.countryCode());
        assertEquals("ann@example.com", ok.email());          // falls back to the account's email

        a.put("stateCode", "");
        assertThrows(IllegalArgumentException.class, () -> PrintOrderController.address(a, null));   // US needs a state
        a.put("stateCode", "NY");
        a.put("phoneNumber", "call me");
        assertThrows(IllegalArgumentException.class, () -> PrintOrderController.address(a, null));
        assertThrows(IllegalArgumentException.class, () -> PrintOrderController.address("nope", null));
    }

    @Test
    void luluFieldErrorsAreFlattenedToOneLine() throws Exception {
        var n = new ObjectMapper().readTree("{\"shipping_address\":{\"postcode\":[\"Invalid postcode\"]}}");
        assertEquals("shipping_address.postcode: Invalid postcode", LuluClient.errorText(n));
        assertEquals("Not found", LuluClient.errorText(new ObjectMapper().readTree("{\"detail\":\"Not found\"}")));
    }

    @Test
    void printFilesAreTheOnlyNewUnauthenticatedPath() {
        assertEquals("/api/print-files/abc/cover.pdf", ApiPaths.normalise("/api//print-files/abc/cover.pdf"));
    }
}
