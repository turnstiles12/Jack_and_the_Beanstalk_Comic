package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
 * OpenAI Images "edits" endpoint: the child's photo is sent as the input image on EVERY call and the
 * model paints a brand-new scene around that child. input_fidelity=high asks the model to preserve
 * facial features of the input. (OpenAI has no seed parameter, so consistency comes from the
 * identical reference + identical fixed instruction + FaceVerifier retries.)
 */
@Component
@ConditionalOnProperty(name = "beanstalk.image.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiImageGenerator implements ImageGenerator {

    /** Fixed prefix, identical on every call. The fixed STYLE sentence stays last, untouched. */
    static final String REFERENCE_INSTRUCTION =
            "Create a brand-new storybook illustration. The child in the provided photo is the character Jack: "
          + "keep his exact face, hair and skin tone recognisable. Do not reuse the photo's background or clothing.";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${beanstalk.openai.key:}") String apiKey;
    @Value("${beanstalk.openai.base-url:https://api.openai.com/v1}") String baseUrl;
    @Value("${beanstalk.openai.model:gpt-image-1.5}") String model;
    @Value("${beanstalk.openai.size:1024x1536}") String size;
    @Value("${beanstalk.openai.quality:medium}") String quality;
    /** "high" for gpt-image-1 / 1.5. Set blank for models that reject the parameter (e.g. gpt-image-2). */
    @Value("${beanstalk.openai.input-fidelity:high}") String inputFidelity;

    @Override
    public byte[] generate(Storyboard.Page page, byte[] referenceJpeg, String looks, long seed) throws Exception {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY is not set");

        String boundary = "----beanstalk" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "model", model);
        field(body, boundary, "prompt", REFERENCE_INSTRUCTION + " " + page.prompt(looks));
        field(body, boundary, "size", size);
        field(body, boundary, "quality", quality);
        field(body, boundary, "n", "1");
        field(body, boundary, "output_format", "jpeg");
        if (inputFidelity != null && !inputFidelity.isBlank()) field(body, boundary, "input_fidelity", inputFidelity);
        file(body, boundary, "image", "reference.jpg", "image/jpeg", referenceJpeg);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/images/edits"))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new RuntimeException("OpenAI HTTP " + res.statusCode() + ": " + errorMessage(res.body()));
        }
        String b64 = json.readTree(res.body()).path("data").path(0).path("b64_json").asText("");
        if (b64.isEmpty()) throw new RuntimeException("OpenAI returned no image data");
        return Base64.getDecoder().decode(b64);
    }

    private String errorMessage(String raw) {
        try {
            JsonNode m = json.readTree(raw).path("error").path("message");
            return m.isMissingNode() ? raw : m.asText();
        } catch (Exception e) {
            return raw;
        }
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
