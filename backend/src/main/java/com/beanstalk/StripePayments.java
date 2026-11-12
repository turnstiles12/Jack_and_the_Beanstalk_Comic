package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stripe Checkout over Stripe's plain REST API (no SDK). One checkout session = one book:
 * the book job id is stored as the session's client_reference_id, and the book only starts
 * generating after {@link #verifyPaid} sees the session as paid for the right amount.
 */
@Service
public class StripePayments {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${beanstalk.stripe.secret-key:}") String secretKey;
    @Value("${beanstalk.stripe.price-cents:99}") int priceCents;
    /** Explicit escape hatch for local development. Missing Stripe config otherwise blocks book creation. */
    @Value("${beanstalk.stripe.payments-disabled:false}") boolean disabled;
    @Value("${beanstalk.stripe.base-url:https://api.stripe.com}") String baseUrl;

    public boolean paymentsDisabled() { return disabled; }
    public boolean configured() { return secretKey != null && !secretKey.isBlank(); }
    public int priceCents() { return priceCents; }

    public record Checkout(String sessionId, String url) {}

    public Checkout createCheckout(String jobId, String owner, String origin) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("mode", "payment");
        f.put("line_items[0][quantity]", "1");
        f.put("line_items[0][price_data][currency]", "usd");
        f.put("line_items[0][price_data][unit_amount]", String.valueOf(priceCents));
        f.put("line_items[0][price_data][product_data][name]", "Personalized \"Jack and the Beanstalk\" storybook");
        f.put("client_reference_id", jobId);
        f.put("metadata[owner]", owner);
        f.put("success_url", origin + "/?book=" + jobId + "&session_id={CHECKOUT_SESSION_ID}");
        f.put("cancel_url", origin + "/?canceled=1");
        JsonNode n = call(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/checkout/sessions"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(f))));
        return new Checkout(n.path("id").asText(), n.path("url").asText());
    }

    /** True only if this Stripe session is fully paid, for this book and this account, at the right price. */
    public boolean verifyPaid(String sessionId, String jobId, String owner) throws Exception {
        if (sessionId == null || !sessionId.matches("cs_[A-Za-z0-9_]+")) return false;
        JsonNode n = call(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/checkout/sessions/" + sessionId)).GET());
        return "paid".equals(n.path("payment_status").asText())
                && jobId.equals(n.path("client_reference_id").asText())
                && owner.equals(n.path("metadata").path("owner").asText())
                && n.path("amount_total").asInt(-1) == priceCents
                && "usd".equals(n.path("currency").asText());
    }

    private JsonNode call(HttpRequest.Builder req) throws Exception {
        if (!configured()) throw new IllegalStateException("Payments are not set up on the server yet.");
        String auth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> res = http.send(
                req.header("Authorization", "Basic " + auth).timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode n = json.readTree(res.body());
        if (res.statusCode() / 100 != 2)
            throw new IllegalStateException("Stripe error: " + n.path("error").path("message").asText("HTTP " + res.statusCode()));
        return n;
    }

    private static String form(Map<String, String> f) {
        return f.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
