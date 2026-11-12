package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Tracks what each finished book really cost to make: every image-API call and every identity-check
 * call the book needed (including retries), priced at the provider's per-call rate. Running totals are
 * kept on disk next to the books, so "average cost per book" survives restarts.
 * Providers don't return a dollar figure per call, so the rates are configuration
 * (beanstalk.cost.*), not billed amounts.
 */
@Service
public class CostTracker {

    private static final Logger log = LoggerFactory.getLogger(CostTracker.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;
    private final double imageCall;
    private final double verifyCall;
    private final double estimate;
    private long books;
    private double totalUsd;

    public CostTracker(@Value("${beanstalk.storage.dir}") String dir,
                       @Value("${beanstalk.image.provider}") String provider,
                       @Value("${beanstalk.cost.image-call-usd:}") String imageCallOverride,
                       @Value("${beanstalk.cost.verify-call-usd:0.002}") double verifyCall) {
        this.file = Path.of(dir).toAbsolutePath().normalize().resolve("cost-stats.json");
        this.imageCall = imageCallOverride.isBlank() ? defaultImageRate(provider) : Double.parseDouble(imageCallOverride);
        this.verifyCall = verifyCall;
        this.estimate = (Storyboard.PAGES.size() + 1) * this.imageCall;     // 20 pages + About-me portrait
        load();
    }

    /** Rough public list prices per generated page; override with IMAGE_CALL_COST_USD. */
    static double defaultImageRate(String provider) {
        return switch (provider == null ? "" : provider.toLowerCase()) {
            case "openai" -> 0.05;       // gpt-image-1.5, medium, 1024x1536
            case "fal" -> 0.0025;
            case "mock" -> 0.0;
            default -> 0.0015;           // cloudflare flux-1-schnell
        };
    }

    public double imageCallUsd() { return imageCall; }

    /** Adds one finished book that used the given number of billable calls. */
    public synchronized void recordBook(int imageCalls, int verifyCalls) {
        books++;
        totalUsd += imageCalls * imageCall + verifyCalls * verifyCall;
        save();
    }

    /** Average cost of one book in USD: measured once books exist, otherwise an estimate from the rate card. */
    public synchronized double averageUsd() { return books > 0 ? totalUsd / books : estimate; }

    public synchronized long books() { return books; }

    private void load() {
        try {
            if (!Files.isRegularFile(file)) return;
            JsonNode n = JSON.readTree(Files.readAllBytes(file));
            books = n.path("books").asLong(0);
            totalUsd = n.path("totalUsd").asDouble(0);
        } catch (IOException e) {
            log.warn("Could not read cost stats {}: {}", file, e.toString());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, JSON.writeValueAsBytes(Map.of("books", books, "totalUsd", totalUsd)));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Could not save cost stats: {}", e.toString());
        }
    }
}
