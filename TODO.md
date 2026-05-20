# TODO

운영 중 발견된 수정·개선 사항. 처리 후 항목은 삭제하거나 `~~취소선~~`으로 마킹.

## 🐛 버그

### 1. 슈퍼 어드민 — 기관 관리자 비밀번호 재설정 동작 안 함
- **위치**: [SuperAdminInstitutionController.java:154](src/main/java/com/coresolution/pe/controller/admin/SuperAdminInstitutionController.java#L154) `POST /admin/institutions/{id}/admins/{adminId}/reset-pwd`
- **UI**: [institutionAdmins.html:124](src/main/resources/templates/pe/super-admin/institutionAdmins.html#L124)
- **증상**: 비밀번호 재설정 폼 제출해도 실제로 변경되지 않음
- **추정 원인**: 미확인 — `InstitutionService.resetAdminPassword(int id, String rawPassword)` 동작 검증 필요
- **확인 포인트**:
  - bcrypt 인코딩 누락 여부
  - `adminId` vs `idx` 컬럼 불일치 (라우트 `{adminId}`가 실제 DB의 어떤 컬럼에 매핑되는지)
  - 트랜잭션 커밋 여부
  - 컨트롤러는 예외 시 flash 에러만 표시 → 서버 로그(`log.error` 적용 완료, 커밋 `6deba53`) 확인

## ✨ 기능 추가

### 2. 슈퍼 어드민 — 기관 관리자 **ID** 변경 기능
- **현재**: ID 변경 UI/엔드포인트 없음. `loginId`는 생성 시점에만 입력 가능.
- **요청**: 슈퍼 어드민이 기관 관리자의 `loginId`도 수정할 수 있어야 함
- **고려 사항**:
  - 중복 ID 검증 (현재 생성 로직과 동일: `DuplicateKeyException` → 친화 메시지)
  - 변경 이력 로그 (`adminId` 그대로, `loginId`만 갱신)
  - 세션 중인 본인 계정 변경 시 강제 로그아웃 정책

### 3. 직원 마이페이지 UI 상이 (PE vs AFF)
- **위치**: `/Info/{idx}` (PE) vs AFF 측 마이페이지
- **증상**: 양쪽 UI가 일관되지 않음 (구체 항목은 비교 필요 — 스크린샷/체크리스트 작업 선행)
- **다음 단계**:
  - PE/AFF 마이페이지 템플릿 diff 정리
  - 어느 쪽을 기준(canonical)으로 맞출지 결정
  - 차이 항목별 마이그레이션 계획

## 🔐 보안/품질 (참고 — 별도 트랙)

> 이 섹션은 코드 분석 중 발견된 항목. 우선순위 별도 판단.

- **DB 비밀번호 평문 커밋** — `application-*.properties` 4개 파일에 노출. 환경변수/시크릿 매니저 이관 필요.
- **슈퍼 어드민 하드코딩** — 사번 `12365478` 단일 계정 의존 (`CustomUserDetailsService` / `CustomAffUserDetailsService`). 인원 변경 시 코드 수정 필요.
- **AFF inst-admin pendingPairs IDOR** — [AffInstAdminPageController.java:193](src/main/java/com/coresolution/pe/controller/AffInstAdminPageController.java#L193) 기관 스코프 가드 누락. PE 쪽은 적용 완료(커밋 `d9580fa`).
- **Report Service silent catch** — `AffEvalReportService`/`EvalReportService` 합 33개 catch 중 일부는 의도된 fallback이지만 디버깅 가시성 0인 케이스 혼재. 사례별 판단 후 로깅 추가 검토.
