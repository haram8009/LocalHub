USE ssafy_trip;
SELECT 'sidos' AS table_name, COUNT(*) AS row_count FROM sidos
UNION ALL SELECT 'guguns', COUNT(*) FROM guguns
UNION ALL SELECT 'contenttypes', COUNT(*) FROM contenttypes
UNION ALL SELECT 'attractions', COUNT(*) FROM attractions;
SELECT COUNT(*) AS orphan_districts FROM attractions a
LEFT JOIN guguns g ON a.area_code=g.sido_code AND a.si_gun_gu_code=g.gugun_code
WHERE a.area_code IS NOT NULL AND a.si_gun_gu_code IS NOT NULL AND g.no IS NULL;
SELECT COUNT(*) AS orphan_types FROM attractions a
LEFT JOIN contenttypes c ON a.content_type_id=c.content_type_id
WHERE a.content_type_id IS NOT NULL AND c.content_type_id IS NULL;
SELECT COUNT(*) AS orphan_areas FROM attractions a LEFT JOIN sidos s ON a.area_code=s.sido_code
WHERE a.area_code IS NOT NULL AND s.no IS NULL;
