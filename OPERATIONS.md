# 운영 가이드

운영·디버깅 시 자주 찾는 정보 모음. 코드 분석 기반으로 정리한 것이라 실제 운영 절차와 다를 수 있음 — 발견 즉시 보강.

## 1. 서버 기동

기본 포트: **9090** (`server.port` in `application.properties`)

```bash
./gradlew bootRun                                          # 기본 프로파일 (= prod DB)
./gradlew bootRun --args='--spring.profiles.active=local'  # 로컬 개발용
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun             # 환경변수 방식
```

### 프로파일별 DB 매핑

| 프로파일 | DB 호스트 | 비고 |
|---------|----------|------|
| (없음) | `hr.sosyge.net:3306` | base `application.properties` |
| `local` | `dev.sosyge.net:3306` | 로컬 개발 권장 |
| `dev` | `dev.sosyge.net:3306` | dev 배포 |
| `prod` | `hr.sosyge.net:3306` | 운영 |

> ⚠️ 인자 없이 실행하면 prod DB와 동일 호스트에 붙음. 로컬 작업 시 반드시 `local` 또는 `dev` 프로파일 지정.

### 종료
- 포그라운드: `Ctrl+C`
- 백그라운드: `lsof -ti:9090 | xargs kill`

---

## 2. 로그인 페이지 (4종)

도메인이 PE(본원)와 AFF(계열사)로 갈리고, 각각 일반 사용자·기관 관리자 진입점이 분리됨.

| 대상 | URL | 인증 후 진입 | 권한 |
|------|-----|--------------|------|
| PE 일반 사용자 | `/login` | `/Info/{idx}` (직원 마이페이지) | `ROLE_USER` |
| PE 슈퍼 어드민 | `/login` | `/admin/institutions` (기관 목록) | `ROLE_ADMIN` (사번 `12365478`) |
| PE 기관 관리자 | `/pe/inst-login` | `/pe/inst-admin/dashboard` | `ROLE_INST_ADMIN` |
| AFF 일반 사용자 | `/aff/login` | (직원 마이페이지) | `ROLE_USER` |
| AFF 슈퍼 어드민 | `/aff/login` | `/aff/admin/userList` | `ROLE_ADMIN` (사번 `12365478`) |
| AFF 기관 관리자 | `/aff/inst-login` | `/aff/inst-admin/dashboard` | `ROLE_INST_ADMIN` |

### 권한 모델 메모
- **슈퍼 어드민 = 사번 `12365478` 단일 계정** (`CustomUserDetailsService` / `CustomAffUserDetailsService`에 하드코딩). PE/AFF 양쪽에서 같은 사번으로 로그인하면 각각 어드민 권한 부여.
- **기관 관리자**는 별도 `institution_admin` 테이블 + 별도 인증 체인. `InstitutionAdminContext`로 자기 기관 범위에만 접근.
- 비밀번호 변경 필수 사용자는 `/pwd/{idx}`로 강제 리다이렉트.

---

## 3. 주요 어드민 페이지

### 슈퍼 어드민 (PE)

| 기능 | URL |
|------|-----|
| 기관 목록·등록·수정 | `/admin/institutions` |
| 기관별 관리자 계정 관리 | `/admin/institutions/{id}/admins` |
| 슈퍼 어드민 대시보드 | `/admin/dashboard` |
| 직원 목록 | `/admin/userList` |
| 부서 관리 | `/admin/subManagement` |
| 직원 데이터 업로드 | `/admin/userDataUpload` |
| 평가 대상 설정 | `/admin/target` |
| 커스텀 대상 추가 | `/admin/targets/new` |
| 평가 문제은행 | `/admin/evaluation` |
| 평가 공개 설정 | `/admin/evalrelease` |
| 진행률 (기관별) | `/admin/progress/org` |
| 진행률 상세 | `/admin/progress/org/detail` |
| KPI 업로드 | `/admin/kpiupload` · `/admin/kpiGeneralUpload` |
| KPI 요약 | `/admin/kpi/summary` |
| 매핑 룰 | `/admin/mapping-rule` |
| 평가 총괄표 | `/admin/reportsummary` |
| 공지 관리 | `/admin/notice` |

### 슈퍼 어드민 (AFF)

| 기능 | URL |
|------|-----|
| 직원 목록 | `/aff/admin/userList` |
| 부서 관리 | `/aff/admin/subManagement` |
| 직원 데이터 업로드 | `/aff/admin/userDataUpload` |
| 평가 대상 설정 | `/aff/admin/target` |
| 커스텀 대상 추가 | `/aff/admin/targets/new` |
| 평가 문제은행 | `/aff/admin/evaluation` |
| 평가 공개 설정 | `/aff/admin/evalrelease` |
| 평가 총괄표 | `/aff/admin/reportsummary` |

### 기관 관리자 (PE)

| 기능 | URL |
|------|-----|
| 대시보드 | `/pe/inst-admin/dashboard` |
| 평가 대상 설정 | `/pe/inst-admin/targets` |
| 진행률 현황 (+ 드릴다운) | `/pe/inst-admin/progress` |
| 평가완료 편지 | `/pe/inst-admin/end-letter` |
| 평가 공개 | `/pe/inst-admin/evalrelease` |
| 평가 진행 (대신 작성) | `/pe/inst-admin/evaluation/...` |

### 기관 관리자 (AFF)

| 기능 | URL |
|------|-----|
| 대시보드 | `/aff/inst-admin/dashboard` |
| 평가 대상 설정 | `/aff/inst-admin/targets` |
| 진행률 현황 (+ 드릴다운) | `/aff/inst-admin/progress` |
| 평가 공개 | `/aff/inst-admin/evalrelease` |
| 직원명부 | `/aff/inst-admin/userList` |

### 엑셀 업로드 컨트롤러

| 컨텍스트 | 경로 prefix |
|---------|-------------|
| PE 슈퍼 | `/excel/...` |
| AFF 슈퍼 | `/aff/excel/...` |
| PE 기관 관리자 | `/pe/inst-admin/excel/...` |
| AFF 기관 관리자 | `/aff/inst-admin/excel/...` |

---

## 4. 핵심 설정값 (`application.properties`)

| 키 | 기본값 | 의미 |
|----|--------|------|
| `app.current.eval-year` | `2026` | 현재 평가 연도 (매년 갱신) |
| `app.admin.id` | `12365478` | 슈퍼 어드민 사번 (연도 전환 자동 시딩에 사용) |
| `app.admin.sub-code` | `CORE` | 관리자 부서코드 |
| `app.upload.bg-dir` | `src/main/resources/static/uploads/bg` | 배경 이미지 업로드 경로 |
| `openai.api-key` | `${OPENAI_API_KEY:}` | AI 리포트용 OpenAI 키 (env로 주입) |
| `openai.model` | `gpt-4o-mini` | AI 리포트 모델 |
| `server.port` | `9090` | HTTP 포트 |

연도 전환 작업: 이 값만 바꾸면 매퍼·서비스 전반의 연도 분기가 자동으로 따라가는 구조 (`@Value("${app.current.eval-year}")`).

---

## 5. 정적 리소스 / 업로드

- 정적: `classpath:/static/` + `file:src/main/resources/static/` (개발 시 라이브 반영)
- 업로드: `src/main/resources/static/uploads/bg/` (배경 이미지)
- 파일 업로드 한도: 10MB (요청 총합 12MB)

---

## 6. 디버깅 팁

- `logging.level.com.coresolution.pe=DEBUG` 기본 적용 — 컨트롤러·서비스 디버그 로그 모두 보임.
- `logging.level.org.springframework.security=DEBUG` — 권한 거부 원인 추적 시 유용.
- MyBatis 로그: `mybatis.configuration.log-impl=StdOutImpl` — 실제 바인딩된 SQL이 콘솔에 출력.
- DevTools 재시작 활성화 — 클래스 변경 시 자동 리로드.

---

## 7. 알려진 주의 사항

- 평문 DB 비밀번호가 git에 커밋되어 있음(`application-*.properties`). 운영 정책 차원에서 환경변수/시크릿 관리자로 이관 필요.
- 슈퍼 어드민이 단일 하드코딩 사번(`12365478`)에 의존. 인원 변경 시 코드 수정 필요.
- `AffInstAdminPageController`의 `/api/progress/members/{targetId}/pending` 엔드포인트에 기관 스코프 가드 누락 (PE 쪽은 가드 추가됨, 후속 작업 필요).
