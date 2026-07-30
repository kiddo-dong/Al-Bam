---
name: spring-domain-scaffold
description: Scaffold or extend a domain in the albam Spring Boot backend following this project's established conventions (package layout, DTOs, authorization, error handling). Use when adding a new domain/feature or a new entity/endpoint to an existing domain.
---

# 알밤(Al-Bam) 백엔드 도메인 스캐폴딩

이 프로젝트에서 새 도메인이나 기능을 추가할 때 따르는 컨벤션을 정리한 스킬입니다. 세션 전반에서 반복적으로 확인된 패턴을 그대로 반영합니다.

## 패키지 구조 (domain-first + layer subfolders)

```
domain/{feature}/
  controller/   {Feature}Controller.java
  service/      {Feature}Service.java (+ 필요 시 {Feature}AuthorizationService 등 보조 서비스)
  repository/   {Feature}Repository.java (JpaRepository)
  entity/       {Feature}.java (+ 관련 enum)
  dto/          {Feature}Request.java, {Feature}Response.java 등 (Java record)
```

새 도메인은 항상 이 5개 하위 폴더 패턴을 따른다. 레이어를 최상위로 나누지 않는다(예: `controller/{feature}/` 같은 구조 금지).

## 엔티티 컨벤션

- `BaseTimeEntity` 상속 (createdAt/updatedAt 자동 관리)
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Getter` — setter 없음, 도메인 메서드로만 상태 변경
- 연관관계(`@ManyToOne` 등)는 기본 `FetchType.LAZY` (N+1은 조회 시점에 fetch join 등으로 해결)
- 생성자에서 필수 필드 받기, 나머지는 `change*()` / 도메인 동작 메서드(`resign()`, `rejoin()`, `clockOut()` 등)로 변경
- 소프트 삭제 vs 하드 삭제: 근태/급여 이력에 연결된 것(회원 탈퇴, 멤버 퇴사)은 소프트 삭제(status 플래그 + 타임스탬프). 그 외 대부분은 하드 삭제. 애매하면 "이 데이터가 지워지면 과거 급여/근태 계산이 깨지는가?"로 판단.

## DTO 컨벤션
  
- 전부 Java `record`. Request/Response 분리.
- Bean Validation은 record 컴포넌트에 직접(`@NotNull`, `@NotBlank`, `@Min` 등).
- Response record에 정적 팩토리 `from(Entity)` 메서드 두는 패턴.
- import는 명시적으로 (`import com.example.albam.domain.x.dto.Y;`) — 와일드카드 import 금지.

## 서비스 컨벤션

- `@Service @RequiredArgsConstructor @Transactional(readOnly = true)` 클래스 레벨, 쓰기 메서드에만 개별 `@Transactional`.
- 의존성은 항상 생성자 주입(`@RequiredArgsConstructor` + `private final`). 필드 `@Autowired` 금지.
- **인가는 매 메서드 첫 줄에서 명시적으로 체크** (Spring Security `@PreAuthorize` 안 씀):
  - `storeAuthorizationService.requireMember(storeId, userId)` — 재직 중인 멤버(ACTIVE)만
  - `storeAuthorizationService.requireOwnerOrManager(storeId, userId)` — OWNER/MANAGER만
  - `storeAuthorizationService.requireOwner(storeId, userId)` — OWNER만
- 존재하지 않는 리소스는 `NotFoundException`, 잘못된 입력/상태는 `InvalidRequestException`, 권한 부족은 `ForbiddenException`, 충돌은 `ConflictException` (모두 `global/exception` 패키지, `GlobalExceptionHandler`가 적절한 HTTP 상태로 매핑).
- 여러 메서드에서 반복되는 검증 로직은 private 헬퍼로 추출하되, "검증만 하고 저장은 안 하는" 버전이 필요하면(AI 초안 검증처럼) 그 헬퍼를 public으로 노출해서 재사용 — 로직을 중복 작성하지 않는다.

## 컨트롤러 컨벤션

- `@RestController @RequestMapping("/api/v1/stores/{storeId}/{resource}") @RequiredArgsConstructor`
- 응답은 항상 `ApiResponse<T>`로 감싸기: `ApiResponse.success(data)` / `ApiResponse.ok()` (void) / 실패는 예외로 던지고 `GlobalExceptionHandler`가 처리 (컨트롤러에서 직접 에러 응답 만들지 않음).
- 생성은 `ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(...))`.
- `@CurrentUserId Long userId` 커스텀 어노테이션으로 인증된 사용자 ID 획득.
- 메서드 위에 한 줄 Javadoc으로 "누가 호출 가능한지" 명시 (예: `/** 거래처 목록 — OWNER/MANAGER 전용. */`).

## API 테스트 스크립트 (curl)

새 컨트롤러(또는 새 엔드포인트 묶음)를 만들면 `src/main/resources/http/`에 curl 테스트 스크립트를 같이 만든다. Swagger로도 테스트할 수 있지만, 스크립트가 있으면 E2E 흐름을 순서대로 재현하기 좋다.

- 파일명: 소문자 리소스명 (예: `supplier.sh`, `labor-qa.sh`)
- `#!/bin/bash` 셔뱅으로 시작, 각 curl 앞에 무엇을 하는지 `echo`로 설명
- 변수: `BASE_URL="http://localhost:8080"`, 인증은 쿠키가 아니라 JWT — `TOKEN="$1"` 로 받아 `-H "Authorization: Bearer $TOKEN"` 사용 (토큰은 `/api/v1/auth/login` 응답의 accessToken)
- 매장 종속 API면 `STORE_ID="$2"` 식으로 파라미터화
- 응답은 `{success, data, message}` 포맷이므로 `| python3 -m json.tool` 파이프로 보기 좋게 출력

```bash
#!/bin/bash
BASE_URL="http://localhost:8080"
TOKEN="$1"
STORE_ID="$2"

echo "== 거래처 목록 조회 (OWNER/MANAGER 전용) =="
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/stores/$STORE_ID/suppliers" | python3 -m json.tool
```

## 새 도메인 추가 시 체크리스트

1. Entity + Repository 작성 (필요한 조회 메서드는 Spring Data 메서드 이름으로, 복잡하면 `@Query`)
2. DTO(Request/Response) 작성
3. Service 작성 — 인가 체크부터, 그다음 비즈니스 로직
4. Controller 작성 — 얇게, Service 위임만
5. 이 기능이 매장 종속인지 전역(store 무관)인지 먼저 판단 (예: 근로기준법 Q&A는 store 무관, 나머지 대부분은 `/stores/{storeId}/...`)
6. 이 기능을 누가 볼 수 있는지(STAFF 공통 / MANAGER+ / OWNER 전용) 정하고 `docs/frontend-role-guide.md`에도 반영
7. `src/main/resources/http/`에 curl 테스트 스크립트 작성
8. `verify-and-commit` 스킬로 컴파일 검증 후 커밋

## 참고 문서

- `docs/backend-prd.md` — 전체 도메인·API·비즈니스 규칙 레퍼런스 (코드가 우선, 이 문서는 스냅샷)
- `docs/frontend-role-guide.md` — 역할별 화면/API 매핑, 프론트 개발자 커뮤니케이션용
