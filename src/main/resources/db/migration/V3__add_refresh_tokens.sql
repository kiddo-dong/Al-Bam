-- 리프레시 토큰을 서버에 기록해 폐기할 수 있게 한다.
--
-- 이전에는 리프레시 토큰이 순수한 JWT여서 서명과 만료만으로 검증됐다. 그래서 서버가 토큰을 무효화할
-- 수단이 없었고, 로그아웃해도(쿠키만 삭제) 값을 복사해 둔 쪽은 만료일(14일)까지 재발급을 계속 받을 수
-- 있었다. 이 테이블에 행이 있어야만 재발급되므로, 이제 행을 지우는 것이 곧 폐기다.
--
-- 토큰 원문이 아니라 SHA-256 16진값(64자)을 저장한다. DB가 유출되더라도 바로 쓸 수 있는 토큰이
-- 함께 넘어가지 않게 하기 위함이다.
--
-- 기존 로그인 세션은 이 테이블에 기록이 없어 다음 재발급 때 거부된다. 사용자는 한 번 다시 로그인해야 한다.

CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_tokens_token_hash` (`token_hash`),
  KEY `idx_refresh_tokens_user_id` (`user_id`),
  -- 만료 행 정리가 이 컬럼으로 훑으므로 인덱스를 둔다.
  KEY `idx_refresh_tokens_expires_at` (`expires_at`),
  CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
