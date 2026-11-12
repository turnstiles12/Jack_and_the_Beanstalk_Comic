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
 * The Postgres (Neon) database that holds accounts, finished books and cost totals.
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
        "CREATE TABLE IF NOT EXISTS cost_stats (id int PRIMARY KEY CHECK (id = 1), books bigint NOT NULL DEFAULT 0,"
            + " total_usd double precision NOT NULL DEFAULT 0)",
        "INSERT INTO cost_stats (id) VALUES (1) ON CONFLICT DO NOTHING"
    };
}
