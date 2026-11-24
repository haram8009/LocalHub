-- 검색 단어 사전 (mysql/V2__search_terms.sql 참고)
CREATE TABLE IF NOT EXISTS post_search_terms (
 term_hash VARCHAR(64) PRIMARY KEY, term LONGTEXT NOT NULL
);
