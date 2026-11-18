package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Lulu Print API (https://api.lulu.com, sandbox https://api.sandbox.lulu.com) over plain HTTP, no SDK.
 * Auth is OAuth client credentials: the client key + secret from the Lulu developer portal buy a short-lived
 * bearer token, which is cached and refreshed shortly before it expires.
 *
 * Lulu charges the print and shipping cost to the card saved on the Lulu account; the customer pays this site
 * the flat print price through Stripe.
 */
@Service
public class LuluClient {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${beanstalk.lulu.client-key:}") String clientKey;
    @Value("${beanstalk.lulu.client-secret:}") String clientSecret;
    @Value("${beanstalk.lulu.base-url:https://api.sandbox.lulu.com}") String baseUrl;
    @Value("${beanstalk.lulu.pod-package-id:0600X0900FCSTDSS080CW444GXX}") String podPackageId;
    @Value("${beanstalk.lulu.shipping-level:MAIL}") String shippingLevel;
    @Value("${beanstalk.lulu.contact-email:}") String contactEmail;

    private String token;
    private long tokenExpiresAt;

    public boolean configured() {
        return notBlank(clientKey) && notBlank(clientSecret) && notBlank(contactEmail);
    }
    public String podPackageId() { return podPackageId; }
    public boolean sandbox() { return baseUrl.contains("sandbox"); }

    /** Where Lulu sends the book. Field names match Lulu's shipping_address object. */
    public record Address(String name, String street1, String street2, String city, String stateCode,
                          String postcode, String countryCode, String phoneNumber, String email) {
        ObjectNode toJson(ObjectMapper m) {
            ObjectNode a = m.createObjectNode();
            a.put("name", name);
            a.put("street1", street1);
            if (notBlank(street2)) a.put("street2", street2);
            a.put("city", city);
            a.put("state_code", stateCode == null ? "" : stateCode);
            a.put("postcode", postcode);
            a.put("country_code", countryCode);
            a.put("phone_number", phoneNumber);
            if (notBlank(email)) a.put("email", email);
            return a;
        }
    }

    /** Lulu's price for printing one copy and shipping it to this address, tax included. */
    public record Quote(BigDecimal total, String currency) {
        public long cents() { return total.movePointRight(2).setScale(0, java.math.RoundingMode.CEILING).longValueExact(); }
    }

    public Quote quote(Address to, int pageCount) throws Exception {
        ObjectNode body = json.createObjectNode();
        ObjectNode item = ((ArrayNode) body.putArray("line_items")).addObject();
        item.put("page_count", pageCount);
        item.put("pod_package_id", podPackageId);
        item.put("quantity", 1);
        ObjectNode addr = to.toJson(json);
        addr.remove("name");                              // the calculator only takes the location fields
        addr.remove("email");
        body.set("shipping_address", addr);
        body.put("shipping_option", shippingLevel);       // the calculator calls it shipping_option, print jobs shipping_level
        JsonNode n = send("POST", "/print-job-cost-calculations/", body);
        return new Quote(new BigDecimal(n.path("total_cost_incl_tax").asText("0")), n.path("currency").asText("USD"));
    }

    /** Required cover size in points (bleed and spine included) for this product and page count. */
    public float[] coverSizePt(int pageCount) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("pod_package_id", podPackageId);
        body.put("interior_page_count", pageCount);
        body.put("unit", "pt");
        JsonNode n = send("POST", "/cover-dimensions/", body);
        return new float[] { Float.parseFloat(n.path("width").asText()), Float.parseFloat(n.path("height").asText()) };
    }

    /** The print job as Lulu reports it: its id and current status name (CREATED, UNPAID, IN_PRODUCTION, SHIPPED...). */
    public record Job(long id, String status, String statusMessage, String trackingUrl) {
        static Job from(JsonNode n) {
            JsonNode s = n.path("status");
            String tracking = "";
            for (JsonNode li : n.path("line_items")) {
                JsonNode urls = li.path("tracking_urls");
                if (urls.isArray() && !urls.isEmpty()) { tracking = urls.get(0).asText(""); break; }
            }
            return new Job(n.path("id").asLong(), s.path("name").asText(""), s.path("message").asText(""), tracking);
        }
    }

    /**
     * Orders one printed copy. Lulu downloads both PDFs from the given public URLs itself, so they must be
     * reachable from the internet when this is called.
     */
    public Job createPrintJob(String orderId, String title, Address to, String interiorUrl, String coverUrl) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("contact_email", contactEmail);
        body.put("external_id", orderId);
        body.put("shipping_level", shippingLevel);
        body.set("shipping_address", to.toJson(json));
        ObjectNode item = ((ArrayNode) body.putArray("line_items")).addObject();
        item.put("external_id", orderId);
        item.put("title", title.length() > 255 ? title.substring(0, 255) : title);
        item.put("quantity", 1);
        ObjectNode files = item.putObject("printable_normalization");
        files.put("pod_package_id", podPackageId);
        files.putObject("interior").put("source_url", interiorUrl);
        files.putObject("cover").put("source_url", coverUrl);
        return Job.from(send("POST", "/print-jobs/", body));
    }

    public Job job(long id) throws Exception {
        return Job.from(send("GET", "/print-jobs/" + id + "/", null));
    }

    // ---------------------------------------------------------------- HTTP

    private JsonNode send(String method, String path, JsonNode body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .header("Cache-Control", "no-cache")
                .timeout(Duration.ofSeconds(40));
        b = body == null ? b.GET() : b.method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode n = res.body() == null || res.body().isBlank() ? json.createObjectNode() : json.readTree(res.body());
        if (res.statusCode() / 100 != 2) throw new LuluException(res.statusCode(), errorText(n));
        return n;
    }

    private synchronized String token() throws Exception {
        if (!configured()) throw new IllegalStateException("Printed copies are not set up on the server yet.");
        long now = System.currentTimeMillis();
        if (token != null && now < tokenExpiresAt) return token;
        String basic = Base64.getEncoder().encodeToString((clientKey + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(
                        URI.create(baseUrl + "/auth/realms/glasstree/protocol/openid-connect/token"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2)
            throw new IllegalStateException("Lulu sign-in failed (HTTP " + res.statusCode() + "). Check LULU_CLIENT_KEY / LULU_CLIENT_SECRET.");
        JsonNode n = json.readTree(res.body());
        token = n.path("access_token").asText();
        long life = Math.max(60, n.path("expires_in").asLong(300));
        tokenExpiresAt = now + (life - 30) * 1000;      // refresh 30 s early
        return token;
    }

    /** Lulu returns field errors as {"shipping_address": {"postcode": ["..."]}} or {"detail": "..."}; flatten to one line. */
    static String errorText(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isEmpty()) return "";
        if (n.has("detail")) return n.path("detail").asText();
        StringBuilder sb = new StringBuilder();
        flatten("", n, sb);
        return sb.toString();
    }

    private static void flatten(String prefix, JsonNode n, StringBuilder sb) {
        if (n.isValueNode()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(prefix.isEmpty() ? "" : prefix + ": ").append(n.asText());
        } else if (n.isArray()) {
            for (JsonNode c : n) flatten(prefix, c, sb);
        } else {
            n.fields().forEachRemaining(e -> flatten(prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(), e.getValue(), sb));
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /** A Lulu API error; {@code status} 400 means Lulu rejected the data (usually the address). */
    public static class LuluException extends Exception {
        public final int status;
        public LuluException(int status, String msg) {
            super(msg == null || msg.isBlank() ? "Lulu error (HTTP " + status + ")" : msg);
            this.status = status;
        }
    }
}
