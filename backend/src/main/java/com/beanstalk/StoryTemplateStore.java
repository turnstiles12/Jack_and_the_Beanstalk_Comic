package com.beanstalk;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * The story templates in Postgres: one row per story in story_templates and one row per page in
 * story_template_pages (scene, quiet zone, story text, text zone, and the pre-drawn page art). Every scene keeps a {LOOKS} slot where the hero's
 * appearance goes, so making a book is only ever "put this person into the stored template": nothing about the
 * story itself is invented at generation time.
 *
 * The built-in stories in {@link Stories} are the seed. On start each one is written to the database if it is
 * missing, or if its built-in definition has changed since it was last written (seed_hash). A template edited
 * directly in the database is therefore kept until the matching Java storyboard is next changed.
 * Book generation and the API read templates from here, never from the Java constants.
 */
@Service
public class StoryTemplateStore {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StoryTemplateStore.class);

    private final DataSource db;
    /** Templates as last read from the database, in display order. Small and read on every request, so kept in memory. */
    private volatile List<Story> templates = List.of();

    public StoryTemplateStore(DataSource db) {
        this.db = db;
        try (Connection c = db.getConnection()) {
            seed(c, Stories.ALL);
        } catch (Exception e) {
            log.error("Could not write the built-in story templates to the database", e);
        }
        reload();
    }

    /** Every stored template; the first is the default. */
    public List<Story> all() { return templates; }

    /** @return the stored template with this id, or null if unknown. A blank id means the default story. */
    public Story find(String id) {
        List<Story> all = templates;
        if (id == null || id.isBlank()) return all.isEmpty() ? null : all.get(0);
        for (Story s : all) if (s.id().equals(id.strip())) return s;
        return null;
    }

    /** Re-reads every template from the database. A story whose rows cannot be read keeps its built-in definition. */
    public void reload() {
        List<Story> loaded = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, title FROM story_templates ORDER BY sort_order, id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString(1);
                try {
                    loaded.add(load(c, id, rs.getString(2)));
                } catch (Exception e) {
                    log.error("Story template {} in the database is unusable", id, e);
                    Story builtIn = Stories.find(id);
                    if (builtIn != null) loaded.add(builtIn);
                }
            }
        } catch (Exception e) {
            log.error("Could not read the story templates from the database", e);
        }
        if (loaded.isEmpty()) {
            log.error("No story templates could be read from the database; using the built-in ones");
            loaded.addAll(Stories.ALL);
        }
        templates = List.copyOf(loaded);
        log.info("Loaded {} story templates from the database: {}", templates.size(), templates.stream().map(Story::id).toList());
    }

    private static Story load(Connection c, String id, String title) throws SQLException {
        List<Storyboard.Page> pages = new ArrayList<>();
        Storyboard.Page aboutMe = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT number, title, scene, quiet, story_text, zone FROM story_template_pages WHERE story_id = ? ORDER BY number")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Storyboard.Page p = new Storyboard.Page(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), Storyboard.Zone.valueOf(rs.getString(6)));
                    if (p.number() == Story.PAGE_COUNT) aboutMe = p; else pages.add(p);
                }
            }
        }
        if (aboutMe == null) throw new IllegalStateException("page " + Story.PAGE_COUNT + " (About Me) is missing");
        for (int i = 0; i < pages.size(); i++)
            if (pages.get(i).number() != i + 1) throw new IllegalStateException("page " + (i + 1) + " is missing");
        return new Story(id, title, pages, aboutMe);     // also checks there are exactly 19 story pages
    }

    /** Writes each built-in story that is missing from the database or whose built-in definition has changed. */
    static void seed(Connection c, List<Story> builtIn) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            int order = 0;
            for (Story s : builtIn) {
                String hash = seedHash(s);
                if (!hash.equals(storedHash(c, s.id()))) write(c, s, order, hash);
                order++;
            }
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    private static String storedHash(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT seed_hash FROM story_templates WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    private static void write(Connection c, Story s, int order, String hash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO story_templates (id, title, sort_order, seed_hash, updated_at) VALUES (?, ?, ?, ?, ?)"
              + " ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, sort_order = EXCLUDED.sort_order,"
              + " seed_hash = EXCLUDED.seed_hash, updated_at = EXCLUDED.updated_at")) {
            ps.setString(1, s.id());
            ps.setString(2, s.title());
            ps.setInt(3, order);
            ps.setString(4, hash);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM story_template_pages WHERE story_id = ? AND number > ?")) {
            ps.setString(1, s.id());
            ps.setInt(2, Story.PAGE_COUNT);
            ps.executeUpdate();
        }
        // A page whose picture-relevant wording (scene, quiet zone) is unchanged keeps its pre-drawn art; a changed
        // page loses it, so TemplateArtPainter draws it again to match.
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO story_template_pages (story_id, number, title, scene, quiet, story_text, zone)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (story_id, number) DO UPDATE SET"
              + " art = CASE WHEN story_template_pages.scene = EXCLUDED.scene AND story_template_pages.quiet = EXCLUDED.quiet"
              + "            THEN story_template_pages.art ELSE NULL END,"
              + " title = EXCLUDED.title, scene = EXCLUDED.scene, quiet = EXCLUDED.quiet,"
              + " story_text = EXCLUDED.story_text, zone = EXCLUDED.zone")) {
            for (Storyboard.Page p : s.allPages()) {
                ps.setString(1, s.id());
                ps.setInt(2, p.number());
                ps.setString(3, p.title());
                ps.setString(4, p.scene());          // still contains the {LOOKS} slot for the hero's appearance
                ps.setString(5, p.quiet());
                ps.setString(6, p.text());           // null on the About Me page
                ps.setString(7, p.zone().name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** The pre-drawn art (JPEG) of one template page, or empty if it has not been drawn yet or cannot be read. */
    public Optional<byte[]> art(String storyId, int number) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT art FROM story_template_pages WHERE story_id = ? AND number = ? AND art IS NOT NULL")) {
            ps.setString(1, storyId);
            ps.setInt(2, number);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getBytes(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            log.error("Could not read the template art of {} page {}", storyId, number, e);
            return Optional.empty();
        }
    }

    /** Stores the pre-drawn art of one template page. */
    public void saveArt(String storyId, int number, byte[] jpeg) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE story_template_pages SET art = ? WHERE story_id = ? AND number = ?")) {
            ps.setBytes(1, jpeg);
            ps.setString(2, storyId);
            ps.setInt(3, number);
            if (ps.executeUpdate() == 0) throw new SQLException("No template page " + storyId + "/" + number);
        }
    }

    /** Page numbers of a story that have no pre-drawn art yet. */
    public List<Integer> pagesWithoutArt(String storyId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT number FROM story_template_pages WHERE story_id = ? AND art IS NULL ORDER BY number")) {
            ps.setString(1, storyId);
            List<Integer> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getInt(1)); }
            return out;
        }
    }

    /** Fingerprint of a built-in story, so a changed Java storyboard replaces the stored copy on the next start. */
    static String seedHash(Story s) {
        try {
            String all = s.id() + "\n" + s.title() + "\n" + s.allPages();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
