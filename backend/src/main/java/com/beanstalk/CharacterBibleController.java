package com.beanstalk;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The signed-in account's character bible. All of /api/** is behind {@link AuthFilter}; every call is scoped to the account. */
@RestController
public class CharacterBibleController {

    private final CharacterBibleStore bible;

    public CharacterBibleController(CharacterBibleStore bible) { this.bible = bible; }

    /** The account's characters (or the shared template's until the account edits something). */
    @GetMapping("/api/bible")
    public ResponseEntity<?> list(@RequestAttribute(AuthFilter.USER) AuthSession user) {
        try {
            List<Map<String, Object>> out = bible.entries(user.sub()).stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("slug", e.slug());
                m.put("displayName", e.displayName());
                m.put("role", e.role());
                m.put("description", e.description());
                m.put("outfit", e.outfit());
                m.put("hasImage", e.hasImage());
                m.put("imageSource", e.imageSource());
                return m;
            }).toList();
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not load the character bible."));
        }
    }

    /** Edit a character's text. Body: displayName, role, description, outfit. */
    @PutMapping("/api/bible/{slug}")
    public ResponseEntity<?> update(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String slug,
                                    @RequestBody Map<String, String> body) {
        try {
            boolean ok = bible.updateEntry(user.sub(), slug, body.get("displayName"), body.get("role"),
                    body.get("description"), body.get("outfit"));
            return ok ? ResponseEntity.ok(Map.of("ok", true)) : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not save the character."));
        }
    }

    @GetMapping("/api/bible/{slug}/image")
    public ResponseEntity<byte[]> image(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String slug) {
        try {
            return bible.image(user.sub(), slug)
                    .map(i -> ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(i.mime()))
                            .cacheControl(CacheControl.noStore().cachePrivate())
                            .body(i.bytes()))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Upload a picture for a character (JPG or PNG; re-encoded to a clean JPEG). */
    @PutMapping(path = "/api/bible/{slug}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> setImage(@RequestAttribute(AuthFilter.USER) AuthSession user, @PathVariable String slug,
                                      @RequestParam("image") MultipartFile image) {
        try {
            byte[] jpeg = BookService.normalisePhoto(image.getBytes());
            // The "child" entry is the account's own photo; everything else is a character picture they uploaded.
            String source = CharacterBibleStore.CHILD.equals(slug) ? "user_photo" : "uploaded";
            bible.setImage(user.sub(), slug, jpeg, "image/jpeg", source);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not save the picture."));
        }
    }

    /** Forget the stored photo of the child (the rest of the bible stays). */
    @DeleteMapping("/api/bible/photo")
    public ResponseEntity<?> deletePhoto(@RequestAttribute(AuthFilter.USER) AuthSession user) {
        try {
            return bible.deleteUserPhoto(user.sub()) ? ResponseEntity.ok(Map.of("ok", true)) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not delete the photo."));
        }
    }
}
