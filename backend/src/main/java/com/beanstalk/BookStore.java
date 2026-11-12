package com.beanstalk;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finished books in Postgres, keyed by the signed-in Google account's stable id.
 * Every query filters on the owner, so one account can never read another's book.
 */
@Service
public class BookStore {

    public record Meta(String id, String name, long createdAt) {}

    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final DataSource db;

    public BookStore(DataSource db) { this.db = db; }

    public void save(String owner, String id, String name, byte[] pdf, byte[] thumbJpeg) throws SQLException {
        requireId(id);
        requireOwner(owner);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO books (id, owner, name, created_at, pdf, thumb) VALUES (?::uuid, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, owner);
            ps.setString(3, name);
            ps.setLong(4, System.currentTimeMillis());
            ps.setBytes(5, pdf);
            ps.setBytes(6, thumbJpeg);
            ps.executeUpdate();
        }
    }

    public List<Meta> list(String owner) {
        requireOwner(owner);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id::text, name, created_at FROM books WHERE owner = ? ORDER BY created_at DESC")) {
            ps.setString(1, owner);
            List<Meta> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Meta(rs.getString(1), rs.getString(2), rs.getLong(3)));
            }
            return out;
        } catch (SQLException e) {
            return List.of();
        }
    }

    public Optional<Meta> meta(String owner, String id) {
        requireOwner(owner);
        if (!validId(id)) return Optional.empty();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id::text, name, created_at FROM books WHERE owner = ? AND id = ?::uuid")) {
            ps.setString(1, owner);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Meta(rs.getString(1), rs.getString(2), rs.getLong(3))) : Optional.empty();
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> pdf(String owner, String id) { return column(owner, id, "pdf"); }

    public Optional<byte[]> thumb(String owner, String id) { return column(owner, id, "thumb"); }

    public boolean delete(String owner, String id) {
        requireOwner(owner);
        if (!validId(id)) return false;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM books WHERE owner = ? AND id = ?::uuid")) {
            ps.setString(1, owner);
            ps.setString(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private Optional<byte[]> column(String owner, String id, String col) {   // col is a fixed literal, never user input
        requireOwner(owner);
        if (!validId(id)) return Optional.empty();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + col + " FROM books WHERE owner = ? AND id = ?::uuid")) {
            ps.setString(1, owner);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getBytes(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private static void requireOwner(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("No owner");
    }

    private static boolean validId(String id) { return id != null && ID.matcher(id).matches(); }

    private static void requireId(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Bad book id");
    }
}
