package com.beanstalk;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Image providers may return PNG. PDFBox re-compresses every PNG it embeds, which is slow for 21 full-page
 * images, whereas a JPEG is embedded as is. {@link BookService} converts each page on its own worker thread,
 * so the PDF step at the end of a book stays quick and the book fits the 60-second budget.
 */
final class Jpegs {

    private Jpegs() {}

    static boolean isJpeg(byte[] b) {
        return b != null && b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    /** Returns the bytes unchanged if already a JPEG, otherwise a quality-0.92 JPEG of the decoded image. */
    static byte[] ensure(byte[] img) throws Exception {
        if (isJpeg(img)) return img;
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(img));
        if (src == null) throw new IllegalArgumentException("The image service returned something that is not an image.");
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, java.awt.Color.WHITE, null);
        g.dispose();
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.92f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            w.setOutput(ios);
            w.write(null, new IIOImage(rgb, null, null), p);
        } finally { w.dispose(); }
        return out.toByteArray();
    }
}
