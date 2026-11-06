# 데이터베이스 ERD

제작자: 박시온, 이하람

`01-schema.sql`과 `03-application-schema.sql`의 전체 10개 테이블·컬럼을 반영한 모델입니다.

```mermaid
erDiagram
    sidos ||--o{ guguns : sido_code
    sidos |o--o{ attractions : area_code
    guguns |o--o{ attractions : "sido_code + gugun_code"
    contenttypes |o--o{ attractions : content_type_id
    travel_posts ||--o{ post_locations : post_id
    travel_posts ||--o{ post_search_terms : post_id
    sidos {
        int no PK "int; NOT NULL"
        int sido_code "int; NOT NULL"
        varchar sido_name "varchar(20); NULL"
    }
    guguns {
        int no PK "int; NOT NULL"
        int sido_code "int; NOT NULL"
        int gugun_code "int; NOT NULL"
        varchar gugun_name "varchar(20); NULL"
    }
    contenttypes {
        int content_type_id PK "int; NOT NULL"
        varchar content_type_name "varchar(45); NULL"
    }
    attractions {
        int no PK "int; NOT NULL"
        int content_id "int; NULL"
        varchar title "varchar(500); NULL"
        int content_type_id "int; NULL"
        int area_code "int; NULL"
        int si_gun_gu_code "int; NULL"
        varchar first_image1 "varchar(100); NULL"
        varchar first_image2 "varchar(100); NULL"
        int map_level "int; NULL"
        decimal latitude "decimal(20,17); NULL"
        decimal longitude "decimal(20,17); NULL"
        varchar tel "varchar(20); NULL"
        varchar addr1 "varchar(100); NULL"
        varchar addr2 "varchar(100); NULL"
        varchar homepage "varchar(1000); NULL"
        varchar overview "varchar(10000); NULL"
    }
    members {
        varchar id PK "varchar(20); NOT NULL"
        datetime created_at "datetime(6); NOT NULL"
        bigint credential_version "bigint; NOT NULL"
        varchar email "varchar(100); NOT NULL"
        varchar name "varchar(20); NOT NULL"
        varchar password_hash "varchar(255); NOT NULL"
    }
    travel_posts {
        bigint id PK "bigint; NOT NULL"
        varchar author_id "varchar(20); NOT NULL"
        varchar author_name "varchar(20); NOT NULL"
        varchar category "varchar(30); NOT NULL"
        longtext content "longtext; NOT NULL"
        datetime created_at "datetime(6); NULL"
        varchar title "varchar(200); NOT NULL"
        datetime updated_at "datetime(6); NULL"
        bigint view_count "bigint; NOT NULL"
    }
    post_locations {
        bigint post_id PK "bigint; NOT NULL"
        varchar address "varchar(500); NULL"
        varchar category "varchar(255); NULL"
        varchar content_id "varchar(255); NULL"
        varchar content_type_id "varchar(255); NULL"
        varchar description "varchar(10000); NULL"
        double latitude "double; NULL"
        double longitude "double; NULL"
        varchar name "varchar(255); NULL"
        varchar original_image_url "varchar(2000); NULL"
        varchar region "varchar(255); NULL"
        varchar tel "varchar(255); NULL"
        varchar thumbnail_image_url "varchar(2000); NULL"
        int visit_order "int; NULL"
        int visit_index PK "int; NOT NULL"
    }
    post_search_terms {
        bigint id PK "bigint; NOT NULL"
        bit body_hit "bit(1); NOT NULL"
        mediumtext term "mediumtext; NOT NULL"
        varchar term_hash "varchar(64); NULL"
        bit title_hit "bit(1); NOT NULL"
        bigint post_id "bigint; NOT NULL"
    }
    search_index_state {
        varchar name PK "varchar(255); NOT NULL"
        varchar version "varchar(255); NOT NULL"
    }
    search_term_seq {
        bigint next_val "bigint; NULL"
    }
```

## 관계와 제약 조건

- `sidos.sido_code`는 유일키이며 구군·관광지에서 참조합니다.
- `guguns(sido_code, gugun_code)`는 복합 유일키입니다. 관광지는 `(area_code, si_gun_gu_code)`로 참조하여 시도별 중복 구군 코드를 구분합니다.
- `contenttypes.content_type_id`는 관광 유형 참조 키입니다.
- `members.email`은 유일합니다.
- `post_locations(post_id, visit_index)`는 복합 기본키입니다.
- `post_search_terms(term_hash, post_id)`는 복합 유일키이며 게시글 삭제 시 토큰도 삭제합니다.
- `travel_posts.author_id`와 `members.id`의 연결은 서비스가 관리합니다. 샘플 게시글의 작성자는 실제 회원이 아닐 수 있어 물리 FK는 없습니다. 회원 탈퇴 시 서비스가 작성 게시글과 검색 토큰을 삭제합니다.
- `post_locations`는 외부 API 관광지도 보관하는 방문지 스냅샷이므로 관광지 테이블에 물리 FK를 두지 않습니다.
- `search_index_state`는 재색인 버전을, `search_term_seq`는 Hibernate의 검색 토큰 ID 생성을 관리합니다.

정확한 인덱스·기본값·FK 정의는 [관광지 DDL](01-schema.sql)과 [애플리케이션 DDL](03-application-schema.sql)을 확인하세요.
