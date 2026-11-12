package com.beanstalk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Tracks what each finished book really cost to make: every image-API call and every identity-check
 * call the book needed (including retries), priced at the provider's per-call rate. Running totals are
 * kept in the database, so "average cost per book" survives restarts.
 * Providers don't return a dollar figure per call, so the rates are configuration
 * (beanstalk.cost.*), not billed amounts.
 */
@Service
public class CostTracker {

    private static final Logger log = LoggerFactory.getLogger(CostTracker.class);

    private final DataSource db;
    private final double imageCall;
    private final double verifyCall;
    private final double estimate;

    public CostTracker(DataSource db,
                       @Value("${beanstalk.image.provider}") String provider,
                       @Value("${beanstalk.cost.image-call-usd:}") String imageCallOverride,
                       @Value("${beanstalk.cost.verify-call-usd:0.002}") double verifyCall) {
        this.db = db;
        this.imageCall = imageCallOverride.isBlank() ? defaultImageRate(provider) : Double.parseDouble(imageCallOverride);
        this.verifyCall = verifyCall;
        this.estimate = (Storyboard.PAGES.size() + 1) * this.imageCall;     // 20 pages + About-me portrait
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

    /** Adds one finished book that used the given number of billable calls (atomic in the database). */
    public void recordBook(int imageCalls, int verifyCalls) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE cost_stats SET books = books + 1, total_usd = total_usd + ? WHERE id = 1")) {
            ps.setDouble(1, imageCalls * imageCall + verifyCalls * verifyCall);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Could not save cost stats: {}", e.toString());
        }
    }

    /** Average cost of one book in USD: measured once books exist, otherwise an estimate from the rate card. */
    public double averageUsd() {
        double[] t = totals();
        return t[0] > 0 ? t[1] / t[0] : estimate;
    }

    public long books() { return (long) totals()[0]; }

    private double[] totals() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT books, total_usd FROM cost_stats WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return new double[]{rs.getLong(1), rs.getDouble(2)};
        } catch (SQLException e) {
            log.warn("Could not read cost stats: {}", e.toString());
        }
        return new double[]{0, 0};
    }
}
