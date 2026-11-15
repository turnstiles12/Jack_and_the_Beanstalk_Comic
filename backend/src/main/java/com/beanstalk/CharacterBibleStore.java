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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The character bible in Postgres: one shared template plus one editable copy per signed-in account.
 * Reads fall back to the template until the account has a copy; the first write makes the copy.
 * Every query is scoped to the owner (the Google account id), so one account never sees another's bible or photo.
 */
@Service
public class CharacterBibleStore {

    public record Entry(String slug, String displayName, String role, String description, String outfit,
                        boolean hasImage, String imageSource, int sortOrder) {}

    public record Image(byte[] bytes, String mime) {}

    /** The slug of the entry that holds the account's own uploaded reference photo. */
    public static final String CHILD = "child";

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MAX_NAME = 80, MAX_TEXT = 1000, MAX_IMAGE = 8 * 1024 * 1024;

    private final DataSource db;

    public CharacterBibleStore(DataSource db) { this.db = db; }

    /** The account's bible entries, or the shared template's if the account has not made a copy yet. */
    public List<Entry> entries(String owner) throws SQLException {
        requireOwner(owner);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT e.slug, e.display_name, e.role, e.description, e.outfit, e.image IS NOT NULL, e.image_source, e.sort_order"
                   + " FROM character_bible_entries e WHERE e.bible_id = COALESCE("
                   + "   (SELECT id FROM character_bibles WHERE owner = ?),"
                   + "   (SELECT id FROM character_bibles WHERE is_template))"
                   + " ORDER BY e.sort_order")) {
            ps.setString(1, owner);
            List<Entry> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    out.add(new Entry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                            rs.getBoolean(6), rs.getString(7), rs.getInt(8)));
            }
            return out;
        }
    }

    /** An entry's picture: the account's own if it has one, otherwise the template's. */
    public Optional<Image> image(String owner, String slug) throws SQLException {
        requireOwner(owner);
        if (!validSlug(slug)) return Optional.empty();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT e.image, e.image_mime FROM character_bible_entries e WHERE e.slug = ? AND e.image IS NOT NULL"
                   + " AND e.bible_id = COALESCE((SELECT id FROM character_bibles WHERE owner = ?),"
                   + "                           (SELECT id FROM character_bibles WHERE is_template))")) {
            ps.setString(1, slug);
            ps.setString(2, owner);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Image(rs.getBytes(1), rs.getString(2))) : Optional.empty();
            }
        }
    }

    /** Stores the account's uploaded reference photo (already normalised to JPEG) as the "child" entry. */
    public void saveUserPhoto(String owner, byte[] jpeg) throws SQLException {
        setImage(owner, CHILD, jpeg, "image/jpeg", "user_photo");
    }

    /** Removes the account's stored photo. The rest of the bible is kept. */
    public boolean deleteUserPhoto(String owner) throws SQLException {
        requireOwner(owner);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE character_bible_entries SET image = NULL, image_mime = NULL, image_source = 'none', updated_at = ?"
                   + " WHERE slug = ? AND image_source = 'user_photo'"
                   + " AND bible_id = (SELECT id FROM character_bibles WHERE owner = ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, CHILD);
            ps.setString(3, owner);
            return ps.executeUpdate() > 0;
        }
    }

    /** Sets or replaces a character's picture in the account's own copy. source: generated | uploaded | user_photo. */
    public void setImage(String owner, String slug, byte[] bytes, String mime, String source) throws SQLException {
        requireOwner(owner);
        if (!validSlug(slug)) throw new IllegalArgumentException("Unknown character.");
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE) throw new IllegalArgumentException("Bad image size.");
        if (!List.of("image/jpeg", "image/png").contains(mime)) throw new IllegalArgumentException("Use a JPG or PNG.");
        if (!List.of("generated", "uploaded", "user_photo").contains(source)) throw new IllegalArgumentException("Bad image source.");
        UUID bible = ensureOwnerBible(owner);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE character_bible_entries SET image = ?, image_mime = ?, image_source = ?, updated_at = ?"
                   + " WHERE bible_id = ? AND slug = ?")) {
            ps.setBytes(1, bytes);
            ps.setString(2, mime);
            ps.setString(3, source);
            ps.setLong(4, System.currentTimeMillis());
            ps.setObject(5, bible);
            ps.setString(6, slug);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Unknown character.");
        }
    }

    /** Edits a character's text in the account's own copy; the shared template is never changed from here. */
    public boolean updateEntry(String owner, String slug, String displayName, String role, String description, String outfit)
            throws SQLException {
        requireOwner(owner);
        if (!validSlug(slug)) return false;
        displayName = limit(displayName, MAX_NAME, true);
        role = limit(role, MAX_NAME, false);
        description = limit(description, MAX_TEXT, true);
        outfit = limit(outfit, MAX_TEXT, false);
        UUID bible = ensureOwnerBible(owner);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE character_bible_entries SET display_name = ?, role = ?, description = ?, outfit = ?, updated_at = ?"
                   + " WHERE bible_id = ? AND slug = ?")) {
            ps.setString(1, displayName);
            ps.setString(2, role);
            ps.setString(3, description);
            ps.setString(4, outfit);
            ps.setLong(5, System.currentTimeMillis());
            ps.setObject(6, bible);
            ps.setString(7, slug);
            return ps.executeUpdate() > 0;
        }
    }

    /** Makes the account's copy of the template on first use (text and pictures), and returns its id. Safe to race. */
    UUID ensureOwnerBible(String owner) throws SQLException {
        requireOwner(owner);
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!exists(c, "SELECT 1 FROM character_bibles WHERE is_template")) CharacterBibleSeed.seedTemplate(c);
                UUID id = UUID.randomUUID();
                long now = System.currentTimeMillis();
                int made;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO character_bibles (id, owner, is_template, title, style, negative_prompt, reference_instruction,"
                      + " created_at, updated_at) SELECT ?, ?, false, title, style, negative_prompt, reference_instruction, ?, ?"
                      + " FROM character_bibles WHERE is_template ON CONFLICT DO NOTHING")) {
                    ps.setObject(1, id);
                    ps.setString(2, owner);
                    ps.setLong(3, now);
                    ps.setLong(4, now);
                    made = ps.executeUpdate();
                }
                if (made > 0) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO character_bible_entries (id, bible_id, slug, display_name, role, description, outfit,"
                          + " image, image_mime, image_source, sort_order, updated_at)"
                          + " SELECT gen_random_uuid(), ?, slug, display_name, role, description, outfit, image, image_mime,"
                          + " image_source, sort_order, ? FROM character_bible_entries"
                          + " WHERE bible_id = (SELECT id FROM character_bibles WHERE is_template)")) {
                        ps.setObject(1, id);
                        ps.setLong(2, now);
                        ps.executeUpdate();
                    }
                }
                UUID result;
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM character_bibles WHERE owner = ?")) {
                    ps.setString(1, owner);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        result = rs.getObject(1, UUID.class);
                    }
                }
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static boolean exists(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) { return rs.next(); }
    }

    private static String limit(String s, int max, boolean required) {
        s = s == null ? "" : s.strip();
        if (required && s.isEmpty()) throw new IllegalArgumentException("A name and a description are required.");
        if (s.length() > max) throw new IllegalArgumentException("Text must be " + max + " characters or fewer.");
        return s;
    }

    private static boolean validSlug(String s) { return s != null && s.length() <= 40 && SLUG.matcher(s).matches(); }

    private static void requireOwner(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("No owner");
    }
}
