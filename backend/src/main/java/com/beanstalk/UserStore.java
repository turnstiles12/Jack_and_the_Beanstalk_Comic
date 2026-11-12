package com.beanstalk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

/** Account records: one row per Google account, created on first sign-in and refreshed on every sign-in. */
@Service
public class UserStore {

    private static final Logger log = LoggerFactory.getLogger(UserStore.class);
    private final DataSource db;

    public UserStore(DataSource db) { this.db = db; }

    public void upsert(String sub, String email, String name, String picture) {
        long now = System.currentTimeMillis();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users (sub, email, name, picture, created_at, last_login_at) VALUES (?, ?, ?, ?, ?, ?) "
                   + "ON CONFLICT (sub) DO UPDATE SET email = EXCLUDED.email, name = EXCLUDED.name, "
                   + "picture = EXCLUDED.picture, last_login_at = EXCLUDED.last_login_at")) {
            ps.setString(1, sub);
            ps.setString(2, email == null ? "" : email);
            ps.setString(3, name == null ? "" : name);
            ps.setString(4, picture == null ? "" : picture);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("Could not save account record: {}", e.toString());   // never block a sign-in on this
        }
    }
}
