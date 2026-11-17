package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * fal.ai FLUX.2 edit endpoints (flash / turbo / klein): a faster, cheaper alternative to {@link FalImageGenerator}
 * (flux-pulid). Opt in with IMAGE_PROVIDER=fal-flux2. The photo goes in as reference image 1 on every page, with the
 * same seed and the same fixed instruction; there is no PuLID face lock, so likeness is close but not guaranteed.
 *
 * Template art: this engine can also draw a page with a stand-in hero (text-to-image) and, for a book, edit the person
 * into that stored picture (photo = image 1, template page = image 2), so the scenery and the other characters are
 * the same in every book.
 *
 * Safety filter: fal only honours enable_safety_checker=false for authorised accounts. Otherwise every page is
 * checked and a flagged page comes back as an all-black image (has_nsfw_concepts may or may not be set). Both cases
 * are reported as "flagged", so {@link BookService} retries the page with gentler wording ({@link Softener}) instead
 * of putting a black page in the book.
 */
@Component
@ConditionalOnProperty(name = "beanstalk.image.provider", havingValue = "fal-flux2")
public class FalFlux2ImageGenerator implements ImageGenerator {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FalFlux2ImageGenerator.class);
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${beanstalk.fal.key:}") private String apiKey;
    @Value("${beanstalk.fal.flux2-endpoint}") private String endpoint;
    @Value("${beanstalk.image.width}") private int width;
    @Value("${beanstalk.image.height}") private int height;
    @Value("${beanstalk.fal.timeout-seconds:40}") private int timeoutSeconds;
    @Value("${beanstalk.fal.ref-max-side:768}") private int refMaxSide;
    /** fal.ai caps concurrent requests per account. Shared by every book being made at once. */
    @Value("${beanstalk.fal.max-concurrent:10}") private int maxConcurrent;
    private volatile Semaphore slots;

    private Semaphore slots() {
        Semaphore s = slots;
        if (s == null) synchronized (this) { if ((s = slots) == null) slots = s = new Semaphore(Math.max(1, maxConcurrent), true); }
        return s;
    }

    /** Fixed prefix when a person is edited into pre-drawn template art. Image 1 = the photo, image 2 = the template page. */
    static final String TEMPLATE_INSTRUCTION =
            "Image 2 is a finished page of a comic book. Recreate image 2 exactly: the same composition, background, "
          + "lighting, poses, clothing and every other character. Change only the hero: give the hero the face, hair "
          + "and skin tone of the person in image 1, clearly recognisable, drawn in the same art style as image 2. "
          + "Do not copy the background or clothing of image 1. The scene, for reference:";

    @Override
    public boolean supportsTemplateArt() { return true; }

    /** A page drawn from scratch: the photo is the only reference image. */
    @Override
    public byte[] generate(Storyboard.Page page, byte[] referenceJpeg, String looks, long seed) throws Exception {
        if (referenceJpeg == null) throw new IllegalArgumentException("fal FLUX.2 edit needs the uploaded photo");
        return call(endpoint, CloudflareRefImageGenerator.REFERENCE_INSTRUCTION + " " + page.prompt(looks),
                List.of(photoUri(referenceJpeg)), seed, "page " + page.number());
    }

    /** The person is edited into the stored template picture of this page; everything else in the picture is kept. */
    @Override
    public byte[] generateFromTemplate(Storyboard.Page page, byte[] templateArt, byte[] referenceJpeg, String looks, long seed)
            throws Exception {
        if (referenceJpeg == null) throw new IllegalArgumentException("fal FLUX.2 edit needs the uploaded photo");
        if (templateArt == null) return generate(page, referenceJpeg, looks, seed);
        return call(endpoint, TEMPLATE_INSTRUCTION + " " + page.prompt(looks),
                List.of(photoUri(referenceJpeg), dataUri(templateArt)), seed, "page " + page.number() + " (from template)");
    }

    /** Template art: text only, on the matching text-to-image endpoint (the edit endpoint without "/edit"). */
    @Override
    public byte[] drawTemplate(Storyboard.Page page, long seed) throws Exception {
        String textEndpoint = endpoint.replaceFirst("/edit/?$", "");
        return call(textEndpoint, page.prompt(Storyboard.TEMPLATE_LOOKS), List.of(), seed, "template page " + page.number());
    }

    /** Same bytes on every page, so every call sees the identical reference. */
    private String photoUri(byte[] referenceJpeg) throws Exception {
        return dataUri(CloudflareRefImageGenerator.shrink(referenceJpeg, refMaxSide));
    }

    private static String dataUri(byte[] jpeg) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }

    private byte[] call(String url0, String prompt, List<String> imageUris, long seed, String what) throws Exception {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("FAL_KEY environment variable is not set");

        // Only fields that every FLUX.2 endpoint (flash, turbo, klein, dev) accepts, so the endpoint can be swapped.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);
        if (!imageUris.isEmpty()) body.put("image_urls", imageUris);
        body.put("image_size", Map.of("width", width, "height", height));
        body.put("seed", seed);
        body.put("num_images", 1);
        body.put("output_format", "jpeg");                  // embedded in the PDF as is, no re-encode
        body.put("enable_safety_checker", false);           // only honoured if fal has authorised the account

        HttpRequest req = HttpRequest.newBuilder(URI.create(url0))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

        // Never exceed fal's concurrent-request cap; if it still answers 429, wait and try again (not a failed attempt).
        HttpResponse<String> res;
        long sent;
        int busy = 0;
        while (true) {
            Semaphore gate = slots();
            gate.acquire();
            sent = System.currentTimeMillis();
            try { res = http.send(req, HttpResponse.BodyHandlers.ofString()); }
            finally { gate.release(); }
            if (res.statusCode() != 429 || ++busy > 8) break;
            Thread.sleep(1000L + 700L * busy);
        }
        if (res.statusCode() / 100 != 2) {
            // Input-side rejection (HTTP 422 content_policy_violation): softer wording is the only thing worth retrying.
            if (res.body() != null && res.body().contains("content_policy_violation"))
                throw new RuntimeException("Image flagged by fal.ai content policy: " + res.body());
            throw new RuntimeException("fal.ai HTTP " + res.statusCode() + ": " + res.body());
        }
        JsonNode out = json.readTree(res.body());
        log.info("fal FLUX.2 {}: {} ms, model timings {}", what, System.currentTimeMillis() - sent, out.path("timings"));
        if (out.path("has_nsfw_concepts").path(0).asBoolean(false)) {
            throw new RuntimeException("Image flagged by safety checker; retry");
        }
        String url = out.path("images").path(0).path("url").asText();
        if (url.isEmpty()) throw new RuntimeException("No image URL in response: " + res.body());

        HttpResponse<byte[]> img = http.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(1)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (img.statusCode() / 100 != 2) throw new RuntimeException("Image download HTTP " + img.statusCode());
        if (isBlank(img.body())) throw new RuntimeException("Image flagged by safety checker (black image returned); retry");
        return img.body();
    }

    /** True for the all-black placeholder fal returns in place of a flagged image. A dark night scene is far brighter than this. */
    static boolean isBlank(byte[] image) {
        try {
            BufferedImage im = ImageIO.read(new ByteArrayInputStream(image));
            if (im == null) return false;
            long sum = 0;
            int n = 0;
            int stepX = Math.max(1, im.getWidth() / 32), stepY = Math.max(1, im.getHeight() / 32);
            for (int y = 0; y < im.getHeight(); y += stepY) {
                for (int x = 0; x < im.getWidth(); x += stepX) {
                    int rgb = im.getRGB(x, y);
                    sum += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                    n += 3;
                }
            }
            return n > 0 && sum / (double) n < 4.0;      // average channel value under 4 of 255
        } catch (Exception e) {
            return false;
        }
    }
}
