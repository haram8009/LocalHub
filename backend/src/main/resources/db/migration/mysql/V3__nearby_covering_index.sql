-- 주변 추천: 상자 범위를 인덱스만으로 읽고(거리 계산에 필요한 visit_order 포함, post_id/visit_index 는 PK로 따라옴)
-- 본문 행은 상위 N 개만 읽도록 기존 (latitude,longitude) 인덱스를 덮개 인덱스로 바꿉니다.
CREATE INDEX idx_post_location_nearby ON post_locations(latitude, longitude, visit_order);
DROP INDEX idx_post_location_coordinates ON post_locations;
