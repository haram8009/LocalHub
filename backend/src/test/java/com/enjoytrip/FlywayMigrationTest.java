package com.enjoytrip;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"app.seed-demo=false", "app.tour-source=api", "app.tour-key=", "app.kakao-key="})
class FlywayMigrationTest extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String username;
    @Value("${spring.datasource.password}") String password;

    @Test void emptyDatabaseIsMigratedToV1() throws Exception {
        try (var c = dataSource.getConnection(); var s = c.createStatement();
             var rs = s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=1")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    /** A database created before Flyway (tables already exist, no history) must be adopted without data loss. */
    @Test void preFlywayDatabaseIsAdoptedWithoutDataLoss() throws Exception {
        String schema = "enjoytrip_legacy_" + UUID.randomUUID().toString().replace("-", "");
        String legacyUrl = url.replaceFirst("/enjoytrip_test_[0-9a-f]+\\?", "/" + schema + "?");
        assertThat(legacyUrl).isNotEqualTo(url);
        try (var admin = dataSource.getConnection(); var s = admin.createStatement()) {
            s.executeUpdate("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        try {
            try (var c = DriverManager.getConnection(legacyUrl, username, password)) {
                // 이전 방식(schema-mysql.sql)으로 만든 DB를 흉내 냅니다.
                ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/mysql/V1__baseline.sql"));
                c.createStatement().executeUpdate("INSERT INTO members(id,password_hash,name,email,created_at,credential_version) "
                    + "VALUES('legacy','hash','기존','legacy@example.com',NOW(6),0)");
            }
            var flyway = Flyway.configure().dataSource(legacyUrl, username, password)
                .locations("classpath:db/migration/mysql").baselineOnMigrate(true).baselineVersion("0").load();
            assertThat(flyway.migrate().success).isTrue();
            try (var c = DriverManager.getConnection(legacyUrl, username, password); var s = c.createStatement()) {
                var members = s.executeQuery("SELECT COUNT(*) FROM members WHERE id='legacy'");
                members.next();
                assertThat(members.getInt(1)).isEqualTo(1);
                var history = s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1");
                history.next();
                assertThat(history.getInt(1)).isEqualTo(2); // baseline(0) + V1
            }
        } finally {
            try (var admin = dataSource.getConnection(); var s = admin.createStatement()) {
                s.executeUpdate("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }
}
