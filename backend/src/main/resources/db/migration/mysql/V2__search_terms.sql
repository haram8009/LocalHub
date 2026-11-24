-- 검색 단어 사전: 부분 일치 검색이 postings 전체(게시글 수 x 단어 수)가 아니라 서로 다른 단어만 훑도록 합니다.
-- 추가만 하는 변경이라 이전 버전과 같은 DB를 함께 써도 됩니다(블루그린). 채우기는 앱 기동 시 SearchIndexInitializer 가 합니다.
CREATE TABLE IF NOT EXISTS post_search_terms (
 term_hash VARCHAR(64) PRIMARY KEY, term LONGTEXT NOT NULL
);
