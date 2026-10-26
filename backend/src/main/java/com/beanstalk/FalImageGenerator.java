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

/**
 * fal.ai "flux-pulid": FLUX with PuLID identity injection. The SAME reference image goes into
 * every call (reference_image_url), with the SAME seed, id_weight, size and step count.
 * Only `prompt` differs per page, and the style sentence inside it is the fixed Storyboard.STYLE.
 */
@Component
@ConditionalOnProperty(name = "beanstalk.image.provider", havingValue = "fal")
public class FalImageGenerator implements ImageGenerator {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${beanstalk.fal.key:}") private String apiKey;
    @Value("${beanstalk.fal.endpoint}") private String endpoint;
    @Value("${beanstalk.image.width}") private int width;
    @Value("${beanstalk.image.height}") private int height;
    @Value("${beanstalk.image.steps}") private int steps;
    @Value("${beanstalk.image.id-weight}") private double idWeight;

    @Override
    public byte[] generate(Storyboard.Page page, byte[] referenceJpeg, long seed) throws Exception {
        String referenceDataUri = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(referenceJpeg);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("FAL_KEY environment variable is not set");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", page.prompt());                 // scene + quiet zone + fixed STYLE (last)
        body.put("negative_prompt", Storyboard.NEGATIVE);   // no-text guard lives here only
        body.put("reference_image_url", referenceDataUri);  // identical on all 20 calls
        body.put("image_size", Map.of("width", width, "height", height));
        body.put("num_inference_steps", steps);
        body.put("guidance_scale", 4);
        body.put("true_cfg", 1.5);                          // >1 activates the negative prompt
        body.put("id_weight", idWeight);
        body.put("seed", seed);
        body.put("max_sequence_length", "256");             // scene prompts are long

        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMinutes(4))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new RuntimeException("fal.ai HTTP " + res.statusCode() + ": " + res.body());
        }
        JsonNode out = json.readTree(res.body());
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
