package com.beanstalk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.Statement;

/**
 * The Postgres (Neon) database that holds accounts, finished books, cost totals, the character bible and the story templates.
 * DATABASE_URL is the connection string Neon gives you (postgresql://user:pass@host/db?sslmode=require);
 * a jdbc:postgresql:// URL also works. The tables are created on first start.
 */
@Configuration
public class Db {

    @Bean
    DataSource dataSource(@Value("${beanstalk.database.url:}") String url) throws Exception {
        if (url == null || url.isBlank())
            throw new IllegalStateException("DATABASE_URL is not set. Point it at the Neon Postgres connection string.");
        DataSource ds = build(url.strip());
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String ddl : SCHEMA) s.execute(ddl);
            CharacterBibleSeed.seedTemplate(c);
        }
        return ds;
    }

    static DataSource build(String url) {
        DataSourceBuilder<?> b = DataSourceBuilder.create();
        if (url.startsWith("jdbc:")) return b.url(url).build();
        URI u = URI.create(url.replaceFirst("^postgres(ql)?://", "postgresql://"));
        String user = null, pass = null;
        if (u.getUserInfo() != null) {
            String[] up = u.getUserInfo().split(":", 2);
            user = up[0];
            pass = up.length > 1 ? up[1] : null;
        }
        String jdbc = "jdbc:postgresql://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "")
                + u.getRawPath() + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
        b.url(jdbc);
        if (user != null) b.username(user);
        if (pass != null) b.password(pass);
        return b.build();
    }

    static final String[] SCHEMA = {
        "CREATE TABLE IF NOT EXISTS users (sub text PRIMARY KEY, email text NOT NULL DEFAULT '', name text NOT NULL DEFAULT '',"
            + " picture text NOT NULL DEFAULT '', created_at bigint NOT NULL, last_login_at bigint NOT NULL)",
        "CREATE TABLE IF NOT EXISTS books (id uuid PRIMARY KEY, owner text NOT NULL, name text NOT NULL, created_at bigint NOT NULL,"
            + " pdf bytea NOT NULL, thumb bytea)",
        "CREATE INDEX IF NOT EXISTS books_owner_created ON books (owner, created_at DESC)",
        // Which built-in story a saved book uses (older rows are all Jack).
        "ALTER TABLE books ADD COLUMN IF NOT EXISTS story text NOT NULL DEFAULT 'jack'",
        "CREATE TABLE IF NOT EXISTS cost_stats (id int PRIMARY KEY CHECK (id = 1), books bigint NOT NULL DEFAULT 0,"
            + " total_usd double precision NOT NULL DEFAULT 0)",
        "INSERT INTO cost_stats (id) VALUES (1) ON CONFLICT DO NOTHING",

        // Character bible: one shared template (is_template) plus an optional per-account copy that holds the
        // account's own reference photo and edits. owner is the Google account id, like books.owner.
        "CREATE TABLE IF NOT EXISTS character_bibles (id uuid PRIMARY KEY, owner text, is_template boolean NOT NULL DEFAULT false,"
            + " title text NOT NULL, style text NOT NULL, negative_prompt text NOT NULL, reference_instruction text NOT NULL,"
            + " created_at bigint NOT NULL, updated_at bigint NOT NULL,"
            + " CHECK ((is_template AND owner IS NULL) OR (NOT is_template AND owner IS NOT NULL)))",
        "CREATE UNIQUE INDEX IF NOT EXISTS character_bibles_one_template ON character_bibles (is_template) WHERE is_template",
        "CREATE UNIQUE INDEX IF NOT EXISTS character_bibles_one_per_owner ON character_bibles (owner) WHERE owner IS NOT NULL",
        "CREATE TABLE IF NOT EXISTS character_bible_entries (id uuid PRIMARY KEY, bible_id uuid NOT NULL"
            + " REFERENCES character_bibles (id) ON DELETE CASCADE, slug text NOT NULL, display_name text NOT NULL,"
            + " role text NOT NULL DEFAULT '', description text NOT NULL, outfit text NOT NULL DEFAULT '',"
            + " image bytea, image_mime text, image_source text NOT NULL DEFAULT 'none'"
            + " CHECK (image_source IN ('none', 'user_photo', 'generated', 'uploaded')),"
            + " sort_order int NOT NULL DEFAULT 0, updated_at bigint NOT NULL, UNIQUE (bible_id, slug))",

        // Story templates: every built-in story and its 20 pages (StoryTemplateStore). Each scene keeps a {LOOKS} slot
        // for the hero, so a book is made by putting one person into these stored pages.
        "CREATE TABLE IF NOT EXISTS story_templates (id text PRIMARY KEY, title text NOT NULL, sort_order int NOT NULL DEFAULT 0,"
            + " seed_hash text NOT NULL DEFAULT '', updated_at bigint NOT NULL)",
        "CREATE TABLE IF NOT EXISTS story_template_pages (story_id text NOT NULL REFERENCES story_templates (id) ON DELETE CASCADE,"
            + " number int NOT NULL, title text NOT NULL, scene text NOT NULL, quiet text NOT NULL, story_text text,"
            + " zone text NOT NULL CHECK (zone IN ('TOP', 'BOTTOM')), PRIMARY KEY (story_id, number))",
        // Pre-drawn page art (JPEG) with a stand-in hero. A book edits the person into this picture instead of
        // drawing the page from nothing. NULL until TemplateArtPainter has drawn it.
        "ALTER TABLE story_template_pages ADD COLUMN IF NOT EXISTS art bytea"
    };
}
