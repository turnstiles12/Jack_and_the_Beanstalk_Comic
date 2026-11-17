package com.beanstalk;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** All /api/books endpoints are behind {@link AuthFilter}; every call is scoped to the signed-in Google account. */
@RestController
public class BookController {

    private final BookService books;
    private final BookStore store;
    private final StripePayments stripe;
    private final CostTracker costs;
    private final StoryTemplateStore templates;   // the stories as stored in the database

    @org.springframework.beans.factory.annotation.Value("${beanstalk.app-base-url:}") String appBaseUrl;

    public BookController(BookService books, BookStore store, StripePayments stripe, CostTracker costs,
                          StoryTemplateStore templates) {
        this.books = books; this.store = store; this.stripe = stripe; this.costs = costs; this.templates = templates;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() { return Map.of("status", "ok"); }

    /** Lets the UI show only the inputs the active image provider actually needs. */
    @GetMapping("/api/config")
    public Map<String, Object> config() {
        boolean photo = books.usesPhoto();
        List<Map<String, String>> stories = templates.all().stream()
                .map(s -> Map.of("id", s.id(), "title", s.title())).toList();
        return Map.of("usesPhoto", photo, "needsAppearance", !photo,
                "stories", stories,
                "priceCents", stripe.priceCents(),
                "avgCostCents", Math.round(costs.averageUsd() * 10000) / 100.0,   // e.g. 3.0 = $0.03
                "booksMade", costs.books());
    }

    /**
     * Step 1 of buying a book: validate the form, hold it (unpaid) in memory and return a Stripe Checkout URL.
     * Generation only starts in {@link #confirm}, after Stripe says the session is paid.
     */
    @PostMapping(path = "/api/books", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(@RequestAttribute(AuthFilter.USER) AuthSession user,
                                    @RequestParam(value = "photo", required = false) MultipartFile photo,
                                    @RequestParam("name") String name,
                                    @RequestParam(value = "details", defaultValue = "") String details,
                                    @RequestParam(value = "appearance", defaultValue = "") String appearance,
                                    @RequestParam(value = "origin", defaultValue = "") String origin,
                                    @RequestParam(value = "story", defaultValue = "") String storyId) {
        Story story = templates.find(storyId);
        if (story == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Please choose one of the built-in stories."));
        if (!stripe.paymentsDisabled() && !stripe.configured())
            return ResponseEntity.status(503).body(Map.of("error", "Payments are not set up on the server yet."));
        appearance = appearance.strip();
        if (appearance.length() > 150)
            return ResponseEntity.badRequest().body(Map.of("error", "Appearance must be 150 characters or fewer."));
        name = name.strip();
        details = details.strip();
        if (name.isEmpty() || name.length() > 30)
            return ResponseEntity.badRequest().body(Map.of("error", "Name must be 1-30 characters."));
        if (details.length() > 400)
            return ResponseEntity.badRequest().body(Map.of("error", "Details must be 400 characters or fewer."));
        String base = appBaseUrl.isBlank() ? origin.strip() : appBaseUrl.strip();
        base = base.replaceAll("/+$", "");
        try {
            BookService.Job job = books.prepare(user.sub(), photo == null ? null : photo.getBytes(), name, details, appearance, story);
            if (stripe.paymentsDisabled()) {            // local development only (STRIPE_PAYMENTS_DISABLED=true)
                books.begin(job);
                return ResponseEntity.accepted().body(Map.of("id", job.id));
            }
            if (!base.matches("https?://[A-Za-z0-9.:\\[\\]-]+"))
                return ResponseEntity.badRequest().body(Map.of("error", "Could not work out the site address."));
            StripePayments.Checkout c = stripe.createCheckout(job.id, user.sub(), base);
            return ResponseEntity.accepted().body(Map.of("id", job.id, "checkoutUrl", c.url()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("error", "Could not start checkout. Please try again."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not read the photo."));
        }
    }

    /** Step 2: the browser comes back from Stripe with a session id; start the book only if it is really paid. */
    @PostMapping("/api/books/{id}/confirm")
    public ResponseEntity<?> confirm(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String id,
                                     @RequestBody Map<String, String> body) {
        BookService.Job job = books.get(user.sub(), id);
        if (job == null)
            return ResponseEntity.status(404).body(Map.of("error",
                    "We couldn't find that book (the server may have restarted). If you were charged, contact us for a refund."));
        try {
            if (!stripe.verifyPaid(body.get("sessionId"), job.id, user.sub()))
                return ResponseEntity.status(402).body(Map.of("error", "Payment has not been completed."));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "Could not verify payment. Please try again."));
        }
        books.begin(job);
        return ResponseEntity.ok(Map.of("id", job.id));
    }

    /** The signed-in user's saved books, newest first. */
    @GetMapping("/api/books")
    public List<Map<String, Object>> list(@RequestAttribute(AuthFilter.USER) AuthSession user) {
        return store.list(user.sub()).stream()
                .<Map<String, Object>>map(m -> Map.of("id", m.id(), "name", m.name(), "createdAt", m.createdAt(),
                        "story", m.story(), "storyTitle", storyTitle(m.story())))
                .toList();
    }

    @GetMapping("/api/books/{id}")
    public ResponseEntity<?> status(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String id) {
        BookService.Job j = books.get(user.sub(), id);
        if (j == null) {
            // Not in memory any more (swept or restarted) - but it may already be saved on the account.
            if (store.meta(user.sub(), id).isPresent())
                return ResponseEntity.ok(Map.of("state", "DONE", "pagesDone", Story.PAGE_COUNT,
                        "totalPages", Story.PAGE_COUNT, "elapsedMs", 0, "error", ""));
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "state", j.state.name(),
                "pagesDone", j.pagesDone,
                "totalPages", Story.PAGE_COUNT,
                "elapsedMs", j.elapsedMs(),
                "story", j.story.id(),
                "donePages", j.donePages(),
                "error", j.error == null ? "" : j.error));
    }

    /** The pre-drawn template art of one story page (stand-in hero, no personal data), if it has been drawn. */
    @GetMapping("/api/stories/{story}/pages/{number}/art")
    public ResponseEntity<byte[]> templateArt(@PathVariable String story, @PathVariable int number) {
        return templates.art(story, number)
                .map(b -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                        .body(b))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** A finished page of a book that is still being made (or was just made); gone once the job leaves memory. */
    @GetMapping("/api/books/{id}/pages/{number}/thumb")
    public ResponseEntity<byte[]> pageThumb(@RequestAttribute(AuthFilter.USER) AuthSession user,
                                            @PathVariable String id, @PathVariable int number) {
        BookService.Job j = books.get(user.sub(), id);
        byte[] thumb = j == null ? null : j.pageThumb(number);
        if (thumb == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.MINUTES).cachePrivate())   // a finished page never changes
                .body(thumb);
    }

    @GetMapping("/api/books/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String id) {
        byte[] data = null;
        String name = "My";
        Story story = Stories.JACK;
        Optional<BookStore.Meta> meta = store.meta(user.sub(), id);
        if (meta.isPresent()) {
            data = store.pdf(user.sub(), id).orElse(null);
            name = meta.get().name();
            Story saved = templates.find(meta.get().story());
            if (saved != null) story = saved;
        } else {
            BookService.Job j = books.get(user.sub(), id);      // saved-to-disk failed: still served from memory
            if (j != null && j.state == BookService.State.DONE && j.pdf != null) { data = j.pdf; name = j.name; story = j.story; }
        }
        if (data == null) return ResponseEntity.notFound().build();
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + story.fileSlug() + "-" + safe + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(data);
    }

    private String storyTitle(String id) {
        Story s = templates.find(id);
        return s == null ? "" : s.title();
    }

    @GetMapping("/api/books/{id}/thumb")
    public ResponseEntity<byte[]> thumb(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String id) {
        return store.thumb(user.sub(), id)
                .map(b -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                        .body(b))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/books/{id}")
    public ResponseEntity<?> delete(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String id) {
        return store.delete(user.sub(), id)
                ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.notFound().build();
    }
}
