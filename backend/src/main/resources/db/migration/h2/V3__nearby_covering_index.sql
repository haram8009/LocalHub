-- 주변 추천 덮개 인덱스 (mysql/V3__nearby_covering_index.sql 참고)
CREATE INDEX IF NOT EXISTS idx_post_location_nearby ON post_locations(latitude, longitude, visit_order);
DROP INDEX IF EXISTS idx_post_location_coordinates;
