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

    public BookController(BookService books, BookStore store) { this.books = books; this.store = store; }

    @GetMapping("/api/health")
    public Map<String, String> health() { return Map.of("status", "ok"); }

    /** Lets the UI show only the inputs the active image provider actually needs. */
    @GetMapping("/api/config")
    public Map<String, Object> config() {
        boolean photo = books.usesPhoto();
        return Map.of("usesPhoto", photo, "needsAppearance", !photo);
    }

    @PostMapping(path = "/api/books", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(@RequestAttribute(AuthFilter.USER) AuthSession user,
                                    @RequestParam(value = "photo", required = false) MultipartFile photo,
                                    @RequestParam("name") String name,
                                    @RequestParam(value = "details", defaultValue = "") String details,
                                    @RequestParam(value = "appearance", defaultValue = "") String appearance) {
        appearance = appearance.strip();
        if (appearance.length() > 150)
            return ResponseEntity.badRequest().body(Map.of("error", "Appearance must be 150 characters or fewer."));
        name = name.strip();
        details = details.strip();
        if (name.isEmpty() || name.length() > 30)
            return ResponseEntity.badRequest().body(Map.of("error", "Name must be 1-30 characters."));
        if (details.length() > 400)
            return ResponseEntity.badRequest().body(Map.of("error", "Details must be 400 characters or fewer."));
        try {
            BookService.Job job = books.start(user.sub(), photo == null ? null : photo.getBytes(), name, details, appearance);
            return ResponseEntity.accepted().body(Map.of("id", job.id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not read the photo."));
        }
    }

    /** The signed-in user's saved books, newest first. */
    @GetMapping("/api/books")
    public List<Map<String, Object>> list(@RequestAttribute(AuthFilter.USER) AuthSession user) {
        return store.list(user.sub()).stream()
                .<Map<String, Object>>map(m -> Map.of("id", m.id(), "name", m.name(), "createdAt", m.createdAt()))
                .toList();
    }

    @GetMapping("/api/books/{id}")
    public ResponseEntity<?> status(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String id) {
        BookService.Job j = books.get(user.sub(), id);
        if (j == null) {
            // Not in memory any more (swept or restarted) - but it may already be saved on the account.
            if (store.meta(user.sub(), id).isPresent())
                return ResponseEntity.ok(Map.of("state", "DONE", "pagesDone", 20, "totalPages", 20, "error", ""));
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "state", j.state.name(),
                "pagesDone", j.pagesDone,
                "totalPages", 20,
                "error", j.error == null ? "" : j.error));
    }

    @GetMapping("/api/books/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String id) {
        byte[] data = null;
        String name = "My";
        Optional<BookStore.Meta> meta = store.meta(user.sub(), id);
        if (meta.isPresent()) {
            data = store.pdf(user.sub(), id).orElse(null);
            name = meta.get().name();
        } else {
            BookService.Job j = books.get(user.sub(), id);      // saved-to-disk failed: still served from memory
            if (j != null && j.state == BookService.State.DONE && j.pdf != null) { data = j.pdf; name = j.name; }
        }
        if (data == null) return ResponseEntity.notFound().build();
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Jack-and-the-Beanstalk-" + safe + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(data);
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
