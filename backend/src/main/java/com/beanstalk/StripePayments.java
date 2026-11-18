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
 * Stripe Checkout over Stripe's plain REST API (no SDK). One checkout session pays for one thing:
 * a digital book (the book job id is the session's client_reference_id) or a printed copy (the print
 * order id). Nothing is made or ordered until {@link #verifyPaid} sees the session as paid for the right amount.
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

    /** Checkout for one digital book at the book price. */
    public Checkout createCheckout(String jobId, String owner, String origin) throws Exception {
        return createCheckout(jobId, owner, priceCents, "Personalized comic book",
                origin + "/?book=" + jobId + "&session_id={CHECKOUT_SESSION_ID}",
                origin + "/?canceled=1");
    }

    /** Checkout for any one item: {@code referenceId} comes back as client_reference_id and is checked in {@link #verifyPaid}. */
    public Checkout createCheckout(String referenceId, String owner, int amountCents, String productName,
                                   String successUrl, String cancelUrl) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("mode", "payment");
        f.put("line_items[0][quantity]", "1");
        f.put("line_items[0][price_data][currency]", "usd");
        f.put("line_items[0][price_data][unit_amount]", String.valueOf(amountCents));
        f.put("line_items[0][price_data][product_data][name]", productName);
        f.put("client_reference_id", referenceId);
        f.put("metadata[owner]", owner);
        f.put("success_url", successUrl);
        f.put("cancel_url", cancelUrl);
        JsonNode n = call(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/checkout/sessions"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(f))));
        return new Checkout(n.path("id").asText(), n.path("url").asText());
    }

    /** True only if this Stripe session is fully paid, for this book and this account, at the book price. */
    public boolean verifyPaid(String sessionId, String jobId, String owner) throws Exception {
        return verifyPaid(sessionId, jobId, owner, priceCents);
    }

    /** True only if this Stripe session is fully paid, for this reference id and account, at exactly this amount. */
    public boolean verifyPaid(String sessionId, String referenceId, String owner, int amountCents) throws Exception {
        if (sessionId == null || !sessionId.matches("cs_[A-Za-z0-9_]+")) return false;
        JsonNode n = call(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/checkout/sessions/" + sessionId)).GET());
        return "paid".equals(n.path("payment_status").asText())
                && referenceId.equals(n.path("client_reference_id").asText())
                && owner.equals(n.path("metadata").path("owner").asText())
                && n.path("amount_total").asInt(-1) == amountCents
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
