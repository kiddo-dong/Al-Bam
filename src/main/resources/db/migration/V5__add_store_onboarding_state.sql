-- 매장 온보딩 진행 상태.
--
-- 진행도를 "데이터가 있는가"로만 판단하면, 매장을 만들 때 업종 프리셋이 채워 넣은 영업시간과
-- 체크리스트 때문에 사장님이 아무것도 안 한 매장이 이미 절반 넘게 끝난 것처럼 보인다. 그 내용이
-- 이 매장에 맞는지는 아직 아무도 보지 않았는데도 그렇다. 그래서 값의 유무와 별개로 "사장님이
-- 확인했다"를 따로 남긴다.
--
-- 단계 이름을 코드의 enum이 아니라 문자열로 두는 이유는 업종 프리셋과 같다 — 어떤 단계를 보여줄지는
-- 화면의 사정이라, 단계를 하나 넣고 빼는 데 서버 배포가 필요하지 않게 한다.

ALTER TABLE stores ADD COLUMN onboarding_completed_at DATETIME(6) NULL;

CREATE TABLE `store_onboarding_steps` (
  `store_id` bigint NOT NULL,
  `step_key` varchar(40) NOT NULL,
  -- 같은 단계를 두 번 확인해도 행이 늘지 않도록 둘을 묶어 기본키로 둔다.
  PRIMARY KEY (`store_id`, `step_key`),
  CONSTRAINT `fk_store_onboarding_steps_store` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
