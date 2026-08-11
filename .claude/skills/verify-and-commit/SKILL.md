---
name: verify-and-commit
description: The build-verification ritual for this project before committing any Java change - JDK21 override compile/test-compile, then commit. Use after any code change in this repo, before creating a git commit.
---

# 알밤(Al-Bam) 백엔드 — 커밋 전 검증 의식

이 프로젝트는 `pom.xml`에 `java.version=25`가 명시되어 있지만, 로컬 환경엔 JDK 21만 설치되어 있는 경우가 흔하다. 그래서 컴파일 검증은 항상 오버라이드 플래그를 붙인다.

## 코드 변경 후 매번 실행

```bash
./mvnw -q -Djava.version=21 compile
./mvnw -q -Djava.version=21 test-compile
```

- 둘 다 출력이 없으면(quiet 모드) 성공. 실패 시 전체 로그를 보려면 `-q` 없이 재실행.
- `-o`(오프라인) 플래그는 쓰지 않는다 — 이 프로젝트에서 플러그인 해석 실패를 일으킨 전례가 있음.
- 새 의존성을 pom.xml에 추가한 직후에는 `./mvnw -q -Djava.version=21 dependency:resolve`로 먼저 다운로드/버전 충돌 여부만 빠르게 확인해도 좋다.

## 커밋 전 확인

1. `git status --short` — 의도한 파일만 스테이징하는지 확인. `git add -A`/`git add .` 지양, 파일 명시.
2. 무관한 미추적 파일(`.mcp.json` 등 설정/시크릿 파일)은 실수로 같이 커밋되지 않도록 주의.
3. `git fetch origin` 후 `git log origin/main..HEAD --oneline`으로 푸시 전 상태 확인.
4. 커밋 메시지는 "무엇을 왜"에 집중 (변경 목록 나열이 아니라 의도/설계 이유 위주). 관련 없는 변경은 별도 커밋으로 분리.

## 이 프로젝트 특유의 위험 포인트

- **새 인프라 의존성 추가 시(DB, 외부 API 등)**: 로컬에 해당 인프라가 없어도 **앱 전체가 부팅 실패하면 안 된다.** `@Lazy` + `ObjectProvider<T>`로 지연 주입하고, 실제 사용 시점에만 실패하도록 만들 것 (S3Uploader/MailService/AiConfig.vectorStore가 이 패턴의 선례).
  - 주의: `@Lazy`는 **빈을 만드는 쪽(`@Bean` 메서드)**과 **주입받는 쪽(생성자 파라미터)** 양쪽에 다 신경 써야 한다. Lombok `@RequiredArgsConstructor`로 일반 필드 주입하면 그 필드를 가진 서비스가 만들어질 때 즉시 연결을 시도해버려서 지연 효과가 없어진다 — `ObjectProvider<T>`로 감싸고 실제 사용 지점에서 `.getObject()` 호출해야 진짜로 지연된다.
- **재기동마다 데이터를 다시 채우는 배치/적재 로직**: 대상이 인메모리면 무해하지만, 영속 저장소(S3 Vectors 등)에서는 중복이 쌓인다. 근로기준법 Q&A 지식베이스가 이 문제를 겪어 기동 시 자동 적재를 없애고 관리자 트리거 API로 바꿨으며, 청크 ID를 내용 해시로 고정해 재적재 시 덮어쓰게 했다. 비슷한 적재 로직을 추가할 때 같은 방식을 따를 것.
- **DB 스키마 변경**: `ddl-auto=validate`라 엔티티만 고치면 부팅이 실패한다. `src/main/resources/db/migration/`에 `V{N}__설명.sql` 마이그레이션을 함께 추가해야 한다.
