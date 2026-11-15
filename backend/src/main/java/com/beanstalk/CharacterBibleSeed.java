package com.beanstalk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Writes the shared character-bible template into Postgres. The text comes from {@link Storyboard}, so the
 * storyboard stays the single source of truth for the defaults. Re-running is harmless: rows that already exist
 * (including any edits made to them) are left alone.
 *
 * Images start empty. "jack" is the illustrated Jack, "child" is the account's uploaded reference photo
 * (filled per account, image_source = 'user_photo'), the rest are generated or uploaded later.
 */
final class CharacterBibleSeed {

    private CharacterBibleSeed() {}

    static final String TEMPLATE_ID = "00000000-0000-0000-0000-00000000b1b1";

    private record Entry(String slug, String name, String role, String description, String outfit) {}

    private static final Entry[] ENTRIES = {
        new Entry("jack", "Jack", "Hero",
            Storyboard.JACK,
            "rust-orange tunic, brown leather vest, tan trousers, small green cap"),
        new Entry("child", "The child (uploaded photo)", "Reference for Jack's face",
            "The child whose photo was uploaded. Jack takes this child's face, hair and skin tone: "
          + "the {LOOKS} part of Jack's description is filled from this reference.",
            ""),
        new Entry("mother", "Jack's mother", "Supporting",
            "Jack's kind mother, a warm, tired-eyed woman in a patched blue dress.",
            "patched blue dress"),
        new Entry("old-man", "The old man", "Supporting",
            "A kindly old man with a weathered face who trades the magic beans.",
            "patched cloak"),
        new Entry("giant", "The giant", "Antagonist",
            "A huge friendly-grumpy giant with a bushy beard, enormous hands and a booming voice.",
            ""),
        new Entry("milky-white", "Milky-White", "Animal",
            "The family's white-and-brown cow.",
            ""),
        new Entry("golden-hen", "The golden hen", "Animal",
            "A plump hen that lays shining golden eggs.",
            ""),
        new Entry("golden-harp", "The golden harp", "Magic object",
            "A small magical golden harp with a gentle glowing face that sings by itself.",
            "")
    };

    static void seedTemplate(Connection c) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO character_bibles (id, owner, is_template, title, style, negative_prompt, reference_instruction,"
              + " created_at, updated_at) VALUES (?::uuid, NULL, true, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, TEMPLATE_ID);
            ps.setString(2, "Jack and the Beanstalk: template");
            ps.setString(3, Storyboard.STYLE);
            ps.setString(4, Storyboard.NEGATIVE);
            ps.setString(5, CloudflareRefImageGenerator.REFERENCE_INSTRUCTION);
            ps.setLong(6, now);
            ps.setLong(7, now);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO character_bible_entries (id, bible_id, slug, display_name, role, description, outfit,"
              + " image_source, sort_order, updated_at) VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)"
              + " ON CONFLICT (bible_id, slug) DO NOTHING")) {
            int order = 0;
            for (Entry e : ENTRIES) {
                ps.setString(1, TEMPLATE_ID);
                ps.setString(2, e.slug());
                ps.setString(3, e.name());
                ps.setString(4, e.role());
                ps.setString(5, e.description());
                ps.setString(6, e.outfit());
                ps.setString(7, "none");
                ps.setInt(8, order++);
                ps.setLong(9, now);
                ps.executeUpdate();
            }
        }
    }
}
