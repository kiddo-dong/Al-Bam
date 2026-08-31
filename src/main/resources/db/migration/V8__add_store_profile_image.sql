-- 매장 대표 사진.
--
-- 사용자 프로필 사진과 마찬가지로 전체 URL이 아니라 S3 key만 저장한다. 버킷·리전·CDN 도메인은
-- 언제든 바뀔 수 있는 인프라 설정이라, 행마다 복사해두면 옮길 때 저장된 링크가 전부 죽는다.

ALTER TABLE stores ADD COLUMN profile_image_key VARCHAR(255) NULL;
