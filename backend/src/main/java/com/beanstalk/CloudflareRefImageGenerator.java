package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Cloudflare Workers AI FLUX.2 klein (multi-reference image generation/editing).
 * The child's photo is sent as multipart field "input_image_0" on EVERY page call, together with a
 * fixed instruction + the page prompt (fixed STYLE sentence last). Model limits (per Cloudflare docs):
 * reference images must be smaller than 512x512, up to 4 of them; output width/height 256-1920;
 * steps are fixed at 4; no negative prompt and no documented seed.
 * Set CLOUDFLARE_REF_MODEL=@cf/black-forest-labs/flux-2-klein-9b for the bigger (pricier) model.
 */
@Component
@ConditionalOnProperty(name = "beanstalk.image.provider", havingValue = "cloudflare-ref")
public class CloudflareRefImageGenerator implements ImageGenerator {

    /** Fixed prefix, identical on every call. The fixed STYLE sentence stays last, untouched. */
    static final String REFERENCE_INSTRUCTION =
            "Create a brand-new storybook illustration. The child in image 1 is the character Jack: "
          + "keep his exact face, hair and skin tone recognisable. Do not reuse the background or clothing of image 1.";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${beanstalk.cloudflare.account-id:}") String accountId;
    @Value("${beanstalk.cloudflare.token:}") String token;
    @Value("${beanstalk.cloudflare.base-url:https://api.cloudflare.com/client/v4}") String baseUrl;
    @Value("${beanstalk.cloudflare.ref-model:@cf/black-forest-labs/flux-2-klein-4b}") String model;
    @Value("${beanstalk.cloudflare.ref-width:1024}") int width;
    @Value("${beanstalk.cloudflare.ref-height:1536}") int height;
    @Value("${beanstalk.cloudflare.ref-max-side:480}") int refMaxSide;

    @Override
    public byte[] generate(Storyboard.Page page, byte[] referenceJpeg, String looks, long seed) throws Exception {
        if (accountId.isBlank() || token.isBlank())
            throw new IllegalStateException("CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN must be set");

        byte[] ref = shrink(referenceJpeg, refMaxSide);   // identical bytes on every call
        String boundary = "----beanstalk" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "prompt", REFERENCE_INSTRUCTION + " " + page.prompt(looks));
        field(body, boundary, "width", String.valueOf(width));
        field(body, boundary, "height", String.valueOf(height));
        file(body, boundary, "input_image_0", "reference.jpg", "image/jpeg", ref);   // name must match exactly
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/accounts/" + accountId + "/ai/run/" + model))
                .timeout(Duration.ofMinutes(3))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode out;
        try { out = json.readTree(res.body()); } catch (Exception e) { out = null; }
        if (res.statusCode() / 100 != 2 || out == null) {
            String msg = out == null ? res.body() : out.path("errors").path(0).path("message").asText(res.body());
            throw new RuntimeException("Cloudflare HTTP " + res.statusCode() + ": " + msg);
        }
        String b64 = out.path("result").path("image").asText(out.path("image").asText(""));
        if (b64.isEmpty()) throw new RuntimeException("Cloudflare returned no image: " + res.body());
        return Base64.getDecoder().decode(b64);
    }

    /** Cloudflare requires reference images smaller than 512x512: scale down (never up) to maxSide. */
    static byte[] shrink(byte[] jpeg, int maxSide) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(jpeg));
        if (src == null) throw new IllegalArgumentException("Reference photo is not a readable image");
        double s = Math.min(1.0, (double) maxSide / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * s));
        int h = Math.max(1, (int) Math.round(src.getHeight() * s));
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, java.awt.Color.WHITE, null);
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", o);
        return o.toByteArray();
    }

    private static void field(ByteArrayOutputStream o, String b, String name, String value) throws Exception {
        o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static void file(ByteArrayOutputStream o, String b, String name, String filename, String type, byte[] data)
            throws Exception {
        o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename
                + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        o.write(data);
        o.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
