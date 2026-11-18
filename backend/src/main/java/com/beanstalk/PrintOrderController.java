package com.beanstalk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

/**
 * Printed copies of a finished book, printed and shipped by Lulu, for one flat price (default $24.99).
 *
 * <ol>
 *   <li>{@code POST /api/print-orders} - the customer picks a saved book and gives a shipping address. Lulu quotes
 *       print + shipping + tax to that address; if it costs more than {@code beanstalk.print.max-lulu-cost-cents}
 *       the order is refused (so the shop never loses money on one). Otherwise a Stripe Checkout for the flat price.</li>
 *   <li>{@code POST /api/print-orders/{id}/confirm} - back from Stripe: check the payment, build the print PDFs
 *       ({@link PrintFiles}) and create the Lulu print job. Lulu fetches the PDFs from
 *       {@code /api/print-files/{token}/...}, the only part of this API that needs no sign-in.</li>
 *   <li>{@code GET /api/print-orders} - the account's orders with Lulu's latest status and tracking link.</li>
 * </ol>
 */
@RestController
public class PrintOrderController {

    private static final Logger log = LoggerFactory.getLogger(PrintOrderController.class);
    private static final SecureRandom RNG = new SecureRandom();
    private static final Set<String> FINAL = Set.of("SHIPPED", "CANCELED", "REJECTED");

    private final LuluClient lulu;
    private final PrintFiles files;
    private final PrintOrderStore orders;
    private final BookStore books;
    private final StoryTemplateStore templates;
    private final StripePayments stripe;

    @Value("${beanstalk.print.price-cents:2499}") int priceCents;
    @Value("${beanstalk.print.max-lulu-cost-cents:2390}") int maxLuluCostCents;
    @Value("${beanstalk.app-base-url:}") String appBaseUrl;

    public PrintOrderController(LuluClient lulu, PrintFiles files, PrintOrderStore orders, BookStore books,
                                StoryTemplateStore templates, StripePayments stripe) {
        this.lulu = lulu; this.files = files; this.orders = orders; this.books = books;
        this.templates = templates; this.stripe = stripe;
    }

    @GetMapping("/api/print/config")
    public Map<String, Object> config() {
        return Map.of("enabled", lulu.configured() && (stripe.configured() || stripe.paymentsDisabled()),
                "priceCents", priceCents, "sandbox", lulu.sandbox());
    }

    /** Step 1: quote with Lulu, save the order unpaid, return a Stripe Checkout URL. */
    @PostMapping("/api/print-orders")
    public ResponseEntity<?> create(@RequestAttribute(AuthFilter.USER) AuthSession user,
                                    @RequestBody Map<String, Object> body) {
        if (!lulu.configured())
            return error(503, "Printed copies are not set up on the server yet.");
        if (!stripe.paymentsDisabled() && !stripe.configured())
            return error(503, "Payments are not set up on the server yet.");
        String bookId = str(body.get("bookId"));
        Optional<BookStore.Meta> book = books.meta(user.sub(), bookId);
        if (book.isEmpty()) return error(404, "We couldn't find that book on your bookshelf.");

        LuluClient.Address to;
        try {
            to = address(body.get("address"), user.email());
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        }
        String base = baseUrl(str(body.get("origin")));
        if (base == null) return error(400, "Could not work out the site address.");
        if (base.matches("https?://(localhost|127\\.0\\.0\\.1|\\[::1\\])(:\\d+)?"))
            return error(503, "Printed copies need the site's public address, because the printer downloads the book "
                    + "from it. Set APP_BASE_URL to the public https address.");

        LuluClient.Quote quote;
        try {
            quote = lulu.quote(to, pageCount(user.sub(), bookId));
        } catch (LuluClient.LuluException e) {
            if (e.status == 400) return error(400, "Lulu could not use that address: " + e.getMessage());
            log.warn("Lulu quote failed: {}", e.getMessage());
            return error(502, "Could not get a shipping quote from the printer. Please try again.");
        } catch (Exception e) {
            log.warn("Lulu quote failed: {}", e.toString());
            return error(502, "Could not get a shipping quote from the printer. Please try again.");
        }
        if (!"USD".equalsIgnoreCase(quote.currency()) || quote.cents() > maxLuluCostCents) {
            log.info("Print quote {} {} to {} is over the limit", quote.total(), quote.currency(), to.countryCode());
            return error(422, "Sorry, we can't print and ship a copy to that address for "
                    + dollars(priceCents) + ". Try a different address.");
        }

        String id = UUID.randomUUID().toString();
        try {
            orders.create(id, user.sub(), bookId, to, priceCents, (int) quote.cents(), token());
            if (stripe.paymentsDisabled()) {                     // local development only
                return ResponseEntity.ok(submit(user.sub(), id, base));
            }
            StripePayments.Checkout c = stripe.createCheckout(id, user.sub(), priceCents,
                    "Printed copy: " + title(book.get()) + " starring " + book.get().name(),
                    base + "/?print=" + id + "&session_id={CHECKOUT_SESSION_ID}",
                    base + "/?print_canceled=1");
            return ResponseEntity.accepted().body(Map.of("id", id, "checkoutUrl", c.url()));
        } catch (Exception e) {
            log.warn("Could not start print checkout: {}", e.toString());
            return error(502, "Could not start checkout. Please try again.");
        }
    }

    /** Step 2: back from Stripe. Only a paid order is sent to Lulu, and only once. A FAILED order can be retried. */
    @PostMapping("/api/print-orders/{id}/confirm")
    public ResponseEntity<?> confirm(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String id,
                                     @RequestBody Map<String, String> body) {
        Optional<PrintOrderStore.Order> o = orders.get(user.sub(), id);
        if (o.isEmpty()) return error(404, "We couldn't find that order.");
        PrintOrderStore.Order order = o.get();
        if (!order.status().equals(PrintOrderStore.AWAITING_PAYMENT) && !order.status().equals(PrintOrderStore.FAILED))
            return ResponseEntity.ok(view(order));                // already sent (or being sent): nothing to do
        String base = baseUrl(body.get("origin"));
        if (base == null) return error(400, "Could not work out the site address.");
        // FAILED is only reachable after a verified payment (or with payments disabled), so a retry skips Stripe.
        if (order.status().equals(PrintOrderStore.AWAITING_PAYMENT)) {
            try {
                if (!stripe.verifyPaid(body.get("sessionId"), id, user.sub(), order.priceCents()))
                    return error(402, "Payment has not been completed.");
            } catch (Exception e) {
                return error(502, "Could not verify payment. Please try again.");
            }
        }
        return ResponseEntity.ok(submit(user.sub(), id, base));
    }

    /** The account's printed-copy orders, with Lulu's status refreshed for the ones still on their way. */
    @GetMapping("/api/print-orders")
    public List<Map<String, Object>> list(@RequestAttribute(AuthFilter.USER) AuthSession user) {
        List<Map<String, Object>> out = new ArrayList<>();
        int refreshed = 0;
        for (PrintOrderStore.Order o : orders.list(user.sub())) {
            if (o.luluJobId() != null && !FINAL.contains(o.luluStatus()) && refreshed < 5 && lulu.configured()) {
                refreshed++;
                try {
                    LuluClient.Job j = lulu.job(o.luluJobId());
                    orders.luluStatus(o.id(), j);
                    o = orders.get(user.sub(), o.id()).orElse(o);
                } catch (Exception e) {
                    log.debug("Lulu status refresh failed for {}: {}", o.id(), e.toString());
                }
            }
            out.add(view(o));
        }
        return out;
    }

    /** Lulu's downloader fetches the print files here. The 43-character random token is the only key. */
    @GetMapping("/api/print-files/{token}/{file}")
    public ResponseEntity<byte[]> printFile(@PathVariable String token, @PathVariable String file) {
        boolean interior = file.equals("interior.pdf");
        if (!interior && !file.equals("cover.pdf")) return ResponseEntity.notFound().build();
        return orders.file(token, interior)
                .map(b -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .header("X-Robots-Tag", "noindex")
                        .body(b))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------- submitting to Lulu

    private Map<String, Object> submit(String owner, String id, String base) {
        try {
            if (!orders.claim(owner, id, PrintOrderStore.AWAITING_PAYMENT, PrintOrderStore.FAILED))
                return orders.get(owner, id).map(this::view).orElse(Map.of("id", id));
        } catch (Exception e) {
            log.warn("Could not claim print order {}: {}", id, e.toString());
            return Map.of("id", id, "status", PrintOrderStore.FAILED, "error", "Please try again.");
        }
        PrintOrderStore.Order order = orders.get(owner, id).orElseThrow();
        try {
            BookStore.Meta meta = books.meta(owner, order.bookId())
                    .orElseThrow(() -> new IllegalStateException("The book was deleted before it could be printed."));
            byte[] pdf = books.pdf(owner, order.bookId()).orElseThrow(() -> new IllegalStateException("Book PDF is missing."));
            float[] trim = PrintFiles.trimPt(lulu.podPackageId());
            byte[] interior = files.interior(pdf, trim[0], trim[1]);
            int pages = pageCountOf(interior);
            float[] cover = lulu.coverSizePt(pages);
            String title = title(meta);
            byte[] coverPdf = files.cover(pdf, cover[0], cover[1], trim[0], title, meta.name());
            orders.saveFiles(id, interior, coverPdf);

            String fileBase = base + "/api/print-files/" + order.fileToken() + "/";
            LuluClient.Job job = lulu.createPrintJob(id, title + " starring " + meta.name(), order.address(),
                    fileBase + "interior.pdf", fileBase + "cover.pdf");
            orders.submitted(id, job);
            log.info("Print order {} sent to Lulu as job {} ({})", id, job.id(), job.status());
        } catch (Exception e) {
            log.warn("Print order {} could not be sent to Lulu: {}", id, e.toString());
            try { orders.failed(id, e.getMessage() == null ? e.toString() : e.getMessage()); }
            catch (Exception ignored) { /* already logged */ }
        }
        return orders.get(owner, id).map(this::view).orElse(Map.of("id", id));
    }

    private Map<String, Object> view(PrintOrderStore.Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id());
        m.put("bookId", o.bookId());
        m.put("status", o.status());
        m.put("luluStatus", o.luluStatus());
        m.put("trackingUrl", o.trackingUrl());
        m.put("error", o.status().equals(PrintOrderStore.FAILED)
                ? "The printer could not accept this order (" + o.error() + "). You have been charged; try again or contact us."
                : "");
        m.put("shipTo", o.address().name() + ", " + o.address().city() + ", " + o.address().countryCode());
        m.put("priceCents", o.priceCents());
        m.put("createdAt", o.createdAt());
        return m;
    }

    // ---------------------------------------------------------------- input checks

    static LuluClient.Address address(Object raw, String accountEmail) {
        if (!(raw instanceof Map<?, ?> a)) throw new IllegalArgumentException("Please fill in the shipping address.");
        String name = field(a, "name", "Name", 1, 60);
        String street1 = field(a, "street1", "Street address", 1, 100);
        String street2 = field(a, "street2", "Apartment / suite", 0, 100);
        String city = field(a, "city", "City", 1, 60);
        String state = field(a, "stateCode", "State / province", 0, 10).toUpperCase(Locale.ROOT);
        String postcode = field(a, "postcode", "Postal code", 1, 20);
        String country = field(a, "countryCode", "Country", 2, 2).toUpperCase(Locale.ROOT);
        if (!country.matches("[A-Z]{2}")) throw new IllegalArgumentException("Country must be a 2-letter code like US.");
        if (country.equals("US") && !state.matches("[A-Z]{2}"))
            throw new IllegalArgumentException("Please enter the 2-letter state code, like NY.");
        String phone = field(a, "phoneNumber", "Phone number", 5, 25);
        if (!phone.matches("[0-9+()\\-. ]+")) throw new IllegalArgumentException("Phone number can only have digits, spaces and + ( ) - .");
        String email = field(a, "email", "Email", 0, 120);
        if (email.isEmpty()) email = accountEmail == null ? "" : accountEmail;
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
            throw new IllegalArgumentException("Please enter a valid email address.");
        return new LuluClient.Address(name, street1, street2, city, state, postcode, country, phone, email);
    }

    private static String field(Map<?, ?> a, String key, String label, int min, int max) {
        String v = str(a.get(key)).replaceAll("[\\p{Cntrl}]", " ").strip();
        if (v.length() < min) throw new IllegalArgumentException(label + (min <= 1 ? " is required." : " is too short."));
        if (v.length() > max) throw new IllegalArgumentException(label + " must be " + max + " characters or fewer.");
        return v;
    }

    /** The public address Lulu will download from: APP_BASE_URL if set, else the browser's origin. Never localhost. */
    private String baseUrl(String origin) {
        String base = (appBaseUrl.isBlank() ? (origin == null ? "" : origin) : appBaseUrl).strip().replaceAll("/+$", "");
        if (!base.matches("https?://[A-Za-z0-9.:\\[\\]-]+")) return null;
        return base;
    }

    private int pageCount(String owner, String bookId) {
        return books.pdf(owner, bookId).map(p -> {
            try { return pageCountOf(p); } catch (Exception e) { return Story.PAGE_COUNT; }
        }).orElse(Story.PAGE_COUNT);
    }

    private static int pageCountOf(byte[] pdf) throws java.io.IOException {
        try (var d = org.apache.pdfbox.Loader.loadPDF(pdf)) { return d.getNumberOfPages(); }
    }

    private String title(BookStore.Meta m) {
        Story s = templates.find(m.story());
        return s == null ? "My Comic" : s.title();
    }

    private static String token() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static String dollars(int cents) { return String.format(Locale.US, "$%d.%02d", cents / 100, cents % 100); }

    private static ResponseEntity<Map<String, String>> error(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}
