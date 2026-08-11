-- 이미지를 전체 S3 URL이 아니라 key로 저장하도록 바꾼다.
-- 호스트(버킷·리전·CDN)는 인프라 설정이라 행마다 복사해두면 옮길 때 링크가 전부 죽는다.
-- 컬럼명도 실제 담기는 값에 맞춰 바꿔, 나중에 URL로 착각해 쓰는 일을 막는다.

ALTER TABLE users CHANGE COLUMN profile_image_url profile_image_key VARCHAR(255);

ALTER TABLE manual_images CHANGE COLUMN image_url image_key VARCHAR(255);

-- 기존 행에 남아 있는 전체 URL에서 호스트 부분을 잘라 key만 남긴다.
-- (형식: https://{bucket}.s3.{region}.amazonaws.com/{key})
UPDATE users
SET profile_image_key = SUBSTRING_INDEX(profile_image_key, '.amazonaws.com/', -1)
WHERE profile_image_key LIKE '%.amazonaws.com/%';

UPDATE manual_images
SET image_key = SUBSTRING_INDEX(image_key, '.amazonaws.com/', -1)
WHERE image_key LIKE '%.amazonaws.com/%';
