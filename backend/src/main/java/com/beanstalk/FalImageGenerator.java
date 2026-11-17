package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * fal.ai "flux-pulid": FLUX with PuLID identity injection. The SAME reference image goes into
 * every call (reference_image_url), with the SAME seed, id_weight, size and step count.
 * Only `prompt` differs per page, and the style sentence inside it is the fixed Storyboard.STYLE.
 */
@Component
@ConditionalOnProperty(name = "beanstalk.image.provider", havingValue = "fal")
public class FalImageGenerator implements ImageGenerator {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FalImageGenerator.class);
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${beanstalk.fal.key:}") private String apiKey;
    @Value("${beanstalk.fal.endpoint}") private String endpoint;
    @Value("${beanstalk.image.width}") private int width;
    @Value("${beanstalk.image.height}") private int height;
    @Value("${beanstalk.image.steps}") private int steps;
    @Value("${beanstalk.image.id-weight}") private double idWeight;
    @Value("${beanstalk.fal.true-cfg:1.5}") private double trueCfg;
    @Value("${beanstalk.fal.timeout-seconds:40}") private int timeoutSeconds;
    @Value("${beanstalk.fal.ref-max-side:768}") private int refMaxSide;
    /** fal.ai caps concurrent requests per account (10 here). Shared by every book being made at once. */
    @Value("${beanstalk.fal.max-concurrent:10}") private int maxConcurrent;
    private volatile Semaphore slots;

    private Semaphore slots() {
        Semaphore s = slots;
        if (s == null) synchronized (this) { if ((s = slots) == null) slots = s = new Semaphore(Math.max(1, maxConcurrent), true); }
        return s;
    }

    @Override
    public byte[] generate(Storyboard.Page page, byte[] referenceJpeg, String looks, long seed) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("FAL_KEY environment variable is not set");
        }
        if (referenceJpeg == null) throw new IllegalArgumentException("fal/PuLID needs the uploaded photo");
        // Same bytes on every page, so the identity is identical across all 20 calls.
        byte[] ref = CloudflareRefImageGenerator.shrink(referenceJpeg, refMaxSide);
        String referenceDataUri = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(ref);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", page.prompt(looks));                 // scene + quiet zone + fixed STYLE (last)
        if (trueCfg > 1) body.put("negative_prompt", Storyboard.NEGATIVE);   // no-text guard lives here only
        body.put("reference_image_url", referenceDataUri);  // identical on all 20 calls
        body.put("image_size", Map.of("width", width, "height", height));
        body.put("num_inference_steps", steps);
        body.put("guidance_scale", 4);
        body.put("true_cfg", Math.max(1, trueCfg));         // >1 activates the negative prompt, but doubles the work per step
        body.put("id_weight", idWeight);
        body.put("seed", seed);
        body.put("max_sequence_length", "256");             // scene prompts are long
        body.put("enable_safety_checker", false);           // fal.ai safety checker explicitly disabled

        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

        // Never exceed fal's concurrent-request cap; if it still answers 429, wait and try again (not a failed attempt).
        HttpResponse<String> res;
        long sent = System.currentTimeMillis();
        int busy = 0;
        while (true) {
            Semaphore gate = slots();
            gate.acquire();
            try { res = http.send(req, HttpResponse.BodyHandlers.ofString()); }
            finally { gate.release(); }
            if (res.statusCode() != 429 || ++busy > 8) break;
            Thread.sleep(1000L + 700L * busy);
        }
        if (res.statusCode() / 100 != 2) {
            throw new RuntimeException("fal.ai HTTP " + res.statusCode() + ": " + res.body());
        }
        JsonNode out = json.readTree(res.body());
        log.info("fal page {}: {} ms round trip, {} steps, model timings {}", page.number(),
                System.currentTimeMillis() - sent, steps, out.path("timings"));
        if (out.path("has_nsfw_concepts").path(0).asBoolean(false)) {
            throw new RuntimeException("Image flagged by safety checker; retry");
        }
        String url = out.path("images").path(0).path("url").asText();
        if (url.isEmpty()) throw new RuntimeException("No image URL in response: " + res.body());

        HttpResponse<byte[]> img = http.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(1)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (img.statusCode() / 100 != 2) throw new RuntimeException("Image download HTTP " + img.statusCode());
        return img.body();
    }
}
