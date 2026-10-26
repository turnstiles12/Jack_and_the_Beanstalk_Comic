package com.beanstalk;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class BookController {

    private final BookService books;

    public BookController(BookService books) { this.books = books; }

    @GetMapping("/api/health")
    public Map<String, String> health() { return Map.of("status", "ok"); }

    @PostMapping(path = "/api/books", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(@RequestParam("photo") MultipartFile photo,
                                    @RequestParam("name") String name,
                                    @RequestParam(value = "details", defaultValue = "") String details) {
        name = name.strip();
        details = details.strip();
        if (name.isEmpty() || name.length() > 30)
            return ResponseEntity.badRequest().body(Map.of("error", "Name must be 1-30 characters."));
        if (details.length() > 400)
            return ResponseEntity.badRequest().body(Map.of("error", "Details must be 400 characters or fewer."));
        try {
            BookService.Job job = books.start(photo.getBytes(), name, details);
            return ResponseEntity.accepted().body(Map.of("id", job.id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not read the photo."));
        }
    }

    @GetMapping("/api/books/{id}")
    public ResponseEntity<?> status(@PathVariable String id) {
        BookService.Job j = books.get(id);
        if (j == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "state", j.state.name(),
                "pagesDone", j.pagesDone,
                "totalPages", 20,
                "error", j.error == null ? "" : j.error));
    }

    @GetMapping("/api/books/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String id,
                                      @RequestParam(value = "name", defaultValue = "My") String name) {
        BookService.Job j = books.get(id);
        if (j == null || j.state != BookService.State.DONE) return ResponseEntity.notFound().build();
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Jack-and-the-Beanstalk-" + safe + ".pdf\"")
                .body(j.pdf);
    }
}
