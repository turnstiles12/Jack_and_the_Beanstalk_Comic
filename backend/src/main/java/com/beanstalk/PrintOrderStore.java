package com.beanstalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Printed-copy orders in Postgres. Like {@link BookStore}, every account-facing query filters on the owner.
 *
 * Status flow: AWAITING_PAYMENT -> SUBMITTING -> SUBMITTED (sent to Lulu; lulu_status then follows Lulu),
 * or SUBMITTING -> FAILED if Lulu refused it after the customer paid (confirm again to retry).
 */
@Service
public class PrintOrderStore {

    public static final String AWAITING_PAYMENT = "AWAITING_PAYMENT";
    public static final String SUBMITTING = "SUBMITTING";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String FAILED = "FAILED";

    public record Order(String id, String owner, String bookId, String status, LuluClient.Address address,
                        int priceCents, int quoteCents, String fileToken, Long luluJobId, String luluStatus,
                        String trackingUrl, String error, long createdAt) {}

    private static final String COLS = "id::text, owner, book_id::text, status, address::text, price_cents, quote_cents,"
            + " file_token, lulu_job_id, lulu_status, tracking_url, error, created_at";

    private final DataSource db;
    private final ObjectMapper json = new ObjectMapper();

    public PrintOrderStore(DataSource db) { this.db = db; }

    public void create(String id, String owner, String bookId, LuluClient.Address address, int priceCents,
                       int quoteCents, String fileToken) throws Exception {
        long now = System.currentTimeMillis();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO print_orders (id, owner, book_id, status, address,"
                     + " price_cents, quote_cents, file_token, created_at, updated_at)"
                     + " VALUES (?::uuid, ?, ?::uuid, ?, ?::jsonb, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, owner);
            ps.setString(3, bookId);
            ps.setString(4, AWAITING_PAYMENT);
            ps.setString(5, json.writeValueAsString(address));
            ps.setInt(6, priceCents);
            ps.setInt(7, quoteCents);
            ps.setString(8, fileToken);
            ps.setLong(9, now);
            ps.setLong(10, now);
            ps.executeUpdate();
        }
    }

    public Optional<Order> get(String owner, String id) {
        if (!uuid(id)) return Optional.empty();
        return one("SELECT " + COLS + " FROM print_orders WHERE owner = ? AND id = ?::uuid", owner, id);
    }

    /** Orders that have been paid for or are being paid for, newest first (abandoned checkouts are left out). */
    public List<Order> list(String owner) {
        List<Order> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM print_orders WHERE owner = ?"
                     + " AND status <> ? ORDER BY created_at DESC")) {
            ps.setString(1, owner);
            ps.setString(2, AWAITING_PAYMENT);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(read(rs)); }
        } catch (Exception e) {
            return List.of();
        }
        return out;
    }

    /**
     * Moves an order from one of {@code from} to SUBMITTING. Only one caller can win, so a double confirm
     * (two tabs, a reload) never orders two copies.
     */
    public boolean claim(String owner, String id, String... from) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE print_orders SET status = ?, error = '', updated_at = ?"
                     + " WHERE owner = ? AND id = ?::uuid AND status = ANY (?)")) {
            ps.setString(1, SUBMITTING);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, owner);
            ps.setString(4, id);
            ps.setArray(5, c.createArrayOf("text", from));
            return ps.executeUpdate() == 1;
        }
    }

    public void saveFiles(String id, byte[] interior, byte[] cover) throws SQLException {
        update("UPDATE print_orders SET interior = ?, cover = ?, updated_at = ? WHERE id = ?::uuid",
                interior, cover, System.currentTimeMillis(), id);
    }

    public void submitted(String id, LuluClient.Job job) throws SQLException {
        update("UPDATE print_orders SET status = ?, lulu_job_id = ?, lulu_status = ?, tracking_url = ?, error = '',"
                + " updated_at = ? WHERE id = ?::uuid", SUBMITTED, job.id(), job.status(), job.trackingUrl(),
                System.currentTimeMillis(), id);
    }

    public void failed(String id, String error) throws SQLException {
        update("UPDATE print_orders SET status = ?, error = ?, updated_at = ? WHERE id = ?::uuid",
                FAILED, error.length() > 500 ? error.substring(0, 500) : error, System.currentTimeMillis(), id);
    }

    public void luluStatus(String id, LuluClient.Job job) throws SQLException {
        update("UPDATE print_orders SET lulu_status = ?, tracking_url = ?, updated_at = ? WHERE id = ?::uuid",
                job.status(), job.trackingUrl(), System.currentTimeMillis(), id);
    }

    /** A print PDF by its secret token, for Lulu's downloader (no account involved). The column name is a fixed literal. */
    public Optional<byte[]> file(String token, boolean interior) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{32,64}")) return Optional.empty();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + (interior ? "interior" : "cover")
                     + " FROM print_orders WHERE file_token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getBytes(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- helpers

    private Optional<Order> one(String sql, Object... args) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(read(rs)) : Optional.empty(); }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void update(String sql, Object... args) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    private Order read(ResultSet rs) throws Exception {
        long job = rs.getLong(9);
        return new Order(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                json.readValue(rs.getString(5), LuluClient.Address.class), rs.getInt(6), rs.getInt(7), rs.getString(8),
                rs.wasNull() ? null : job, rs.getString(10), rs.getString(11), rs.getString(12), rs.getLong(13));
    }

    private static boolean uuid(String id) {
        return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
