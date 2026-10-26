package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Optional consistency gate (beanstalk.verify.provider = none | openai | anthropic).
 * Shows a vision model the reference photo + a generated page and asks whether Jack plausibly has the same
 * face. A "no" makes BookService regenerate the page. Fails OPEN (returns true) on any API error so the
 * checker can never block a book. This is a coarse sanity check, not a biometric match.
 */
@Component
public class FaceVerifier {

    private static final Logger log = LoggerFactory.getLogger(FaceVerifier.class);
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${beanstalk.verify.provider:none}") String provider;
    @Value("${beanstalk.verify.openai-key:}") String openaiKey;
    @Value("${beanstalk.verify.openai-model:gpt-4.1-mini}") String openaiModel;
    @Value("${beanstalk.verify.anthropic-key:}") String anthropicKey;
    @Value("${beanstalk.verify.anthropic-model:claude-sonnet-4-5}") String anthropicModel;

    public boolean sameChild(byte[] referenceJpeg, byte[] pageImage, boolean pageHasOtherPeople) {
        try {
            String q = "Image 1 is a photo of a real child. Image 2 is a 3D-animated storybook illustration "
                     + "whose main boy character, 'Jack', must look like that same child (face shape, eyes, "
                     + "hair colour and style, skin tone, apparent age). "
                     + (pageHasOtherPeople ? "Other characters may appear; judge only Jack, the child. " : "")
                     + "Reply with JSON only: {\"same_child\": true} or {\"same_child\": false}.";
            String answer = switch (provider == null ? "none" : provider.toLowerCase()) {
                case "openai" -> askOpenAi(q, referenceJpeg, pageImage);
                case "anthropic" -> askAnthropic(q, referenceJpeg, pageImage);
                default -> null;
            };
            if (answer == null) return true;
            return !answer.replace(" ", "").toLowerCase().contains("\"same_child\":false");
        } catch (Exception e) {
            log.warn("Face verification skipped: {}", e.toString());
            return true;
        }
    }

    private String askOpenAi(String q, byte[] ref, byte[] page) throws Exception {
        if (openaiKey.isBlank()) return null;
        Map<String, Object> body = Map.of(
            "model", openaiModel,
            "max_tokens", 30,
            "messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "text", "text", q),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUri(ref))),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUri(page)))))));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + openaiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) { log.warn("Verifier HTTP {}: {}", res.statusCode(), res.body()); return null; }
        JsonNode t = json.readTree(res.body()).path("choices").path(0).path("message").path("content");
        return t.asText();
    }

    private String askAnthropic(String q, byte[] ref, byte[] page) throws Exception {
        if (anthropicKey.isBlank()) return null;
        Map<String, Object> body = Map.of(
            "model", anthropicModel, "max_tokens", 50,
            "messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "image", "source", Map.of("type", "base64", "media_type", mime(ref),
                        "data", Base64.getEncoder().encodeToString(ref))),
                Map.of("type", "image", "source", Map.of("type", "base64", "media_type", mime(page),
                        "data", Base64.getEncoder().encodeToString(page))),
                Map.of("type", "text", "text", q)))));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("x-api-key", anthropicKey).header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) { log.warn("Verifier HTTP {}: {}", res.statusCode(), res.body()); return null; }
        return json.readTree(res.body()).path("content").path(0).path("text").asText();
    }

    private static String dataUri(byte[] b) {
        return "data:" + mime(b) + ";base64," + Base64.getEncoder().encodeToString(b);
    }

    private static String mime(byte[] b) {
        return (b.length > 3 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') ? "image/png" : "image/jpeg";
    }
}
