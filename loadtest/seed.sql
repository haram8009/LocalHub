-- 부하 테스트용 합성 데이터. 운영 DB에는 절대 실행하지 마세요. (loadtest/seed.sh 가 호출)
-- 실제 관광지(attractions) 좌표와 contentId 를 써서 근처 추천·유사 코스 추천이 실제처럼 걸리게 합니다.
--   회원 @members 명(load0001…), 게시글 @posts 건, 게시글마다 장소 2~6곳(같은 시군구의 이웃 관광지)
-- 인기 장소에 코스가 몰리도록 기준 장소를 POW(RAND(), 2) 로 치우치게 고릅니다.
SET SESSION cte_max_recursion_depth = 1000000;
SET @members = COALESCE(@members, 1000);
SET @posts = COALESCE(@posts, 50000);

-- 이전 부하 데이터 제거 (load* 회원과 그 글). 다른 데이터는 건드리지 않습니다.
DELETE FROM travel_posts WHERE author_id LIKE 'load%';
DELETE FROM members WHERE id LIKE 'load%' AND id <> 'loadseed';

-- 회원: loadseed(시드 스크립트가 API 로 가입시킨 계정)의 비밀번호 해시를 그대로 복사합니다.
INSERT INTO members(id, password_hash, name, email, created_at, credential_version)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < @members)
SELECT CONCAT('load', LPAD(i, 4, '0')), (SELECT password_hash FROM members WHERE id = 'loadseed'),
       CONCAT('부하', i), CONCAT('load', i, '@example.com'), NOW(6), 0
FROM n;

-- 장소 풀: 시도·시군구·위도 순으로 번호를 매겨 번호가 가까우면 실제로도 가깝게 만듭니다.
DROP TEMPORARY TABLE IF EXISTS seed_places;
CREATE TEMPORARY TABLE seed_places (rn INT PRIMARY KEY, content_id INT, content_type_id INT, title VARCHAR(500),
  addr VARCHAR(100), latitude DOUBLE, longitude DOUBLE, image VARCHAR(100));
INSERT INTO seed_places
SELECT ROW_NUMBER() OVER (ORDER BY area_code, si_gun_gu_code, latitude, no), content_id, content_type_id, title,
       addr1, latitude, longitude, first_image1
FROM attractions
WHERE content_id IS NOT NULL AND latitude BETWEEN 33 AND 39 AND longitude BETWEEN 124 AND 132;
SET @places = (SELECT COUNT(*) FROM seed_places);

-- 게시글 번호 → 기준 장소, 장소 수
DROP TEMPORARY TABLE IF EXISTS seed_posts;
CREATE TEMPORARY TABLE seed_posts (i INT PRIMARY KEY, anchor INT, places INT, author INT, created DATETIME(6));
INSERT INTO seed_posts
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < @posts)
SELECT i,
       1 + FLOOR(POW(RAND(i), 2) * (@places - 16)),  -- 마지막 장소가 anchor + 5*3 까지 가므로
       2 + (i % 5),
       1 + (i % @members),
       NOW(6) - INTERVAL (@posts - i) MINUTE
FROM n;

INSERT INTO travel_posts(id, category, title, content, author_id, author_name, view_count, created_at, updated_at)
SELECT 1000000 + s.i,
       ELT(1 + s.i % 4, 'COURSE', 'REVIEW', 'TIP', 'RECOMMENDATION'),
       CONCAT(p.title, ' ', ELT(1 + s.i % 6, '당일치기', '1박 2일', '가족 여행', '맛집 투어', '산책 코스', '사진 명소')),
       CONCAT(COALESCE(p.addr, ''), ' 일대를 둘러본 기록입니다. ', p.title, ' 에서 시작해 근처 ', s.places - 1,
              '곳을 더 들렀습니다. ', ELT(1 + s.i % 5, '주차가 편했어요.', '아이와 가기 좋아요.', '저녁 노을이 멋집니다.',
              '평일 오전이 한적합니다.', '근처 시장도 들러 보세요.')),
       CONCAT('load', LPAD(s.author, 4, '0')), CONCAT('부하', s.author),
       FLOOR(RAND(s.i * 7) * 500), s.created, s.created
FROM seed_posts s JOIN seed_places p ON p.rn = s.anchor;

-- 장소: 기준 장소 이후 이웃 번호를 1~3칸 간격으로 이어 붙입니다.
INSERT INTO post_locations(post_id, visit_index, content_id, content_type_id, name, category, address, region,
                           latitude, longitude, thumbnail_image_url, visit_order)
WITH RECURSIVE k(j) AS (SELECT 0 UNION ALL SELECT j + 1 FROM k WHERE j < 5)
SELECT 1000000 + s.i, k.j, CAST(p.content_id AS CHAR), CAST(p.content_type_id AS CHAR), p.title, NULL, p.addr,
       SUBSTRING_INDEX(COALESCE(p.addr, ''), ' ', 1), p.latitude, p.longitude, NULLIF(p.image, ''), k.j + 1
FROM seed_posts s JOIN k ON k.j < s.places
JOIN seed_places p ON p.rn = s.anchor + k.j * (1 + s.i % 3);

-- 검색 인덱스는 애플리케이션이 다음 기동 때 다시 만들도록 상태를 지웁니다.
DELETE FROM search_index_state WHERE name = 'posts';

SELECT (SELECT COUNT(*) FROM members WHERE id LIKE 'load%') AS members,
       (SELECT COUNT(*) FROM travel_posts) AS posts,
       (SELECT COUNT(*) FROM post_locations) AS locations;
