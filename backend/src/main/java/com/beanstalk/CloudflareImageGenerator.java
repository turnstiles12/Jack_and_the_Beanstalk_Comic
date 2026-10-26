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
import java.util.Base64;

/**
 * Cloudflare Workers AI, @cf/black-forest-labs/flux-1-schnell.
 *
 * LIMITATION (by the model's design): FLUX.1 schnell is TEXT-to-image. It accepts only prompt/steps/seed;
 * it cannot take a reference photo, so it cannot guarantee the same face. Identity is approximated by
 * (a) a typed appearance description injected into every prompt via {LOOKS}, (b) one fixed seed for the
 * whole book, (c) a fixed outfit, and (d) the identical STYLE sentence. Output is square 1024x1024
 * (no size option), so PdfComposer makes square pages.
 */
@Component
@ConditionalOnProperty(name = "beanstalk.image.provider", havingValue = "cloudflare")
public class CloudflareImageGenerator implements ImageGenerator {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${beanstalk.cloudflare.account-id:}") String accountId;
    @Value("${beanstalk.cloudflare.token:}") String token;
    @Value("${beanstalk.cloudflare.base-url:https://api.cloudflare.com/client/v4}") String baseUrl;
    @Value("${beanstalk.cloudflare.model:@cf/black-forest-labs/flux-1-schnell}") String model;
    @Value("${beanstalk.cloudflare.steps:8}") int steps;   // model maximum is 8

    @Override
    public boolean usesPhoto() { return false; }

    @Override
    public byte[] generate(Storyboard.Page page, byte[] referenceJpeg, String looks, long seed) throws Exception {
        if (accountId.isBlank() || token.isBlank())
            throw new IllegalStateException("CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN must be set");

        String prompt = page.prompt(looks);
        if (prompt.length() > 2048) throw new IllegalStateException("Prompt exceeds 2048 chars on page " + page.number());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);   // the model has no negative_prompt; scenes avoid text-bearing objects instead
        body.put("steps", steps);
        body.put("seed", seed);       // same base seed for every page

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/accounts/" + accountId + "/ai/run/" + model))
                .timeout(Duration.ofMinutes(3))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode out;
        try { out = json.readTree(res.body()); } catch (Exception e) { out = null; }
        if (res.statusCode() / 100 != 2 || out == null) {
            String msg = out == null ? res.body() : out.path("errors").path(0).path("message").asText(res.body());
            throw new RuntimeException("Cloudflare HTTP " + res.statusCode() + ": " + msg);
        }
        // REST wraps the model output as {"result":{"image":"<base64>"},"success":true}
        String b64 = out.path("result").path("image").asText(out.path("image").asText(""));
        if (b64.isEmpty()) throw new RuntimeException("Cloudflare returned no image: " + res.body());
        return Base64.getDecoder().decode(b64);
    }
}
