package com.enjoytrip;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real MySQL integration tests. A fresh schema is owned by this test JVM only. */
abstract class MySqlIntegrationTest {
    private static final TestDatabase DATABASE = TestDatabase.create();

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:db/schema-mysql.sql");
    }

    private record TestDatabase(String serverUrl, String schema, String username, String password) {
        String url() { return serverUrl + schema + "?connectionTimeZone=UTC&characterEncoding=UTF-8"; }

        static TestDatabase create() {
            Properties local = new Properties();
            Path file = Path.of(".env");
            if (!Files.exists(file)) file = Path.of("backend", ".env");
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    local.load(reader);
                } catch (IOException e) { throw new IllegalStateException("Cannot read local DB settings", e); }
            }
            URI service = URI.create(local.getProperty("DB_URL", "jdbc:mysql://localhost:3306/ssafy_trip").replaceFirst("^jdbc:", ""));
            String host = setting("TEST_DB_HOST", service.getHost());
            int port = Integer.parseInt(setting("TEST_DB_PORT", String.valueOf(service.getPort() < 0 ? 3306 : service.getPort())));
            if (!host.matches("[a-zA-Z0-9.-]+") || port < 1 || port > 65535)
                throw new IllegalArgumentException("Invalid TEST_DB_HOST or TEST_DB_PORT");
            var db = new TestDatabase("jdbc:mysql://" + host + ":" + port + "/",
                "enjoytrip_test_" + UUID.randomUUID().toString().replace("-", ""),
                setting("TEST_DB_USERNAME", local.getProperty("DB_USERNAME", "root")),
                setting("TEST_DB_PASSWORD", local.getProperty("DB_PASSWORD", "")));
            try (Connection connection = db.connect(); var statement = connection.createStatement()) {
                if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName()))
                    throw new IllegalStateException("Integration tests require MySQL");
                statement.executeUpdate("CREATE DATABASE `" + db.schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                System.out.println("Integration database: MySQL " + connection.getMetaData().getDatabaseProductVersion() + " / " + db.schema);
            } catch (SQLException e) {
                throw new IllegalStateException("MySQL test database creation failed. Check TEST_DB_* settings and CREATE/DROP DATABASE privileges.", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(db::drop, "drop-mysql-test-schema"));
            return db;
        }

        private static String setting(String name, String fallback) {
            String value = System.getenv(name);
            return value == null ? fallback : value;
        }

        private Connection connect() throws SQLException {
            return DriverManager.getConnection(serverUrl + "?connectionTimeZone=UTC", username, password);
        }

        private void drop() {
            // Only the random identifier generated above can be dropped.
            if (!schema.matches("enjoytrip_test_[0-9a-f]{32}")) throw new IllegalStateException("Invalid test schema");
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE `" + schema + "`");
                System.out.println("Removed MySQL test schema: " + schema);
            } catch (SQLException e) {
                System.err.println("Could not remove test schema " + schema + ": " + e.getSQLState());
            }
        }
    }
}
