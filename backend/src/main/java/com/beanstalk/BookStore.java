package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finished books on disk, one folder per Google account (named by a hash of the account's stable id,
 * never the e-mail). On Render this folder must be a persistent disk or books vanish on redeploy.
 * Every lookup takes the owner, so one account can never read another's book.
 */
@Service
public class BookStore {

    public record Meta(String id, String name, long createdAt) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path root;

    public BookStore(@Value("${beanstalk.storage.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    /** The .json file is written last: a book is listed only once its PDF is fully on disk. */
    public void save(String owner, String id, String name, byte[] pdf, byte[] thumbJpeg) throws IOException {
        requireId(id);
        Path d = userDir(owner);
        Files.createDirectories(d);
        write(d.resolve(id + ".pdf"), pdf);
        if (thumbJpeg != null) write(d.resolve(id + ".jpg"), thumbJpeg);
        write(d.resolve(id + ".json"), JSON.writeValueAsBytes(
                Map.of("id", id, "name", name, "createdAt", System.currentTimeMillis())));
    }

    public List<Meta> list(String owner) {
        Path d = userDir(owner);
        if (!Files.isDirectory(d)) return List.of();
        List<Meta> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(d)) {
            s.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> readMeta(p).ifPresent(out::add));
        } catch (IOException e) {
            return List.of();
        }
        out.sort(Comparator.comparingLong(Meta::createdAt).reversed());
        return out;
    }

    public Optional<Meta> meta(String owner, String id) {
        if (!validId(id)) return Optional.empty();
        return readMeta(userDir(owner).resolve(id + ".json"));
    }

    public Optional<byte[]> pdf(String owner, String id) { return file(owner, id, ".pdf"); }

    public Optional<byte[]> thumb(String owner, String id) { return file(owner, id, ".jpg"); }

    public boolean delete(String owner, String id) {
        if (meta(owner, id).isEmpty()) return false;
        Path d = userDir(owner);
        try {
            Files.deleteIfExists(d.resolve(id + ".json"));   // un-list first
            Files.deleteIfExists(d.resolve(id + ".pdf"));
            Files.deleteIfExists(d.resolve(id + ".jpg"));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ---- helpers ----

    private Optional<byte[]> file(String owner, String id, String ext) {
        if (meta(owner, id).isEmpty()) return Optional.empty();
        try {
            Path p = userDir(owner).resolve(id + ext);
            return Files.isRegularFile(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<Meta> readMeta(Path p) {
        try {
            if (!Files.isRegularFile(p)) return Optional.empty();
            JsonNode n = JSON.readTree(Files.readAllBytes(p));
            String id = n.path("id").asText("");
            if (!validId(id)) return Optional.empty();
            return Optional.of(new Meta(id, n.path("name").asText(""), n.path("createdAt").asLong(0)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Path userDir(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("No owner");
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(owner.getBytes(StandardCharsets.UTF_8));
            return root.resolve(HexFormat.of().formatHex(h));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Path target, byte[] data) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean validId(String id) { return id != null && ID.matcher(id).matches(); }

    private static void requireId(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Bad book id");
    }
}
