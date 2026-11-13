-- Additive initialization: existing application and tourism data are preserved.
CREATE TABLE IF NOT EXISTS members (
 id VARCHAR(20) PRIMARY KEY, password_hash VARCHAR(255) NOT NULL,
 name VARCHAR(20) NOT NULL, email VARCHAR(100) NOT NULL UNIQUE,
 created_at TIMESTAMP(6) NOT NULL, credential_version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS travel_posts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, category VARCHAR(30) NOT NULL,
 title VARCHAR(200) NOT NULL, content LONGTEXT NOT NULL,
 author_id VARCHAR(20) NOT NULL, author_name VARCHAR(20) NOT NULL,
 view_count BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 INDEX idx_post_author(author_id), INDEX idx_post_created(created_at,id)
);
CREATE TABLE IF NOT EXISTS post_locations (
 post_id BIGINT NOT NULL, visit_index INT NOT NULL, content_id VARCHAR(100), content_type_id VARCHAR(20),
 name VARCHAR(200) NOT NULL, category VARCHAR(100), address VARCHAR(500), region VARCHAR(100),
 description VARCHAR(10000), tel VARCHAR(100), latitude DOUBLE, longitude DOUBLE,
 thumbnail_image_url VARCHAR(2000), original_image_url VARCHAR(2000), visit_order INT,
 INDEX idx_post_location_coordinates(latitude,longitude),
 INDEX idx_post_location_content(content_id),
 PRIMARY KEY(post_id, visit_index), FOREIGN KEY(post_id) REFERENCES travel_posts(id) ON DELETE CASCADE
);
-- Use a new table so the old Hibernate sequence-backed index can coexist during migration.
-- The derived index is rebuilt from the existing travel_posts at first start.
CREATE TABLE IF NOT EXISTS post_search_postings (
 term_hash VARCHAR(64) NOT NULL, post_id BIGINT NOT NULL, term LONGTEXT NOT NULL,
 title_hit BOOLEAN NOT NULL, body_hit BOOLEAN NOT NULL,
 PRIMARY KEY(term_hash, post_id), FOREIGN KEY(post_id) REFERENCES travel_posts(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS search_index_state (name VARCHAR(255) PRIMARY KEY, version VARCHAR(255) NOT NULL);
