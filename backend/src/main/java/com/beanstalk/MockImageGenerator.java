package com.beanstalk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/** No-cost placeholder art (gradient + circle) for smoke-testing the whole pipeline/deploy without API keys. */
@Component
@ConditionalOnProperty(name = "beanstalk.image.provider", havingValue = "mock")
public class MockImageGenerator implements ImageGenerator {
    @Override
    public byte[] generate(Storyboard.Page page, byte[] referenceJpeg, String looks, long seed) throws Exception {
        int i = page.number();
        BufferedImage im = new BufferedImage(1024, 1536, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(80 + i * 8, 160, 230 - i * 6), 0, 1536, new Color(40, 110 + i * 5, 50)));
        g.fillRect(0, 0, 1024, 1536);
        g.setColor(Color.ORANGE);
        g.fillOval(352, 520, 320, 320);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(im, "jpg", o);
        return o.toByteArray();
    }
}
