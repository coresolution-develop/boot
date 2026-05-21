# TODO

운영 중 발견된 수정·개선 사항. 처리 후 항목은 "✅ 최근 완료" 섹션으로 압축·이동.

## 🚀 진행 예정

### A. AFF 직원 페이지 디자인 이주 (PE 스타일로)
`/aff/Info`, `/aff/pwdSet`, `/aff/report` 세 페이지가 작년 2025 디자인(`infocss2025.css`, `leftMenucss2025.css`, `demoinfocss.css` 등)을 그대로 사용 중. PE의 self-contained `.ev-header` + `.evaluator-bar` 디자인으로 이주 필요.

- **이주 방향**: PE 스타일 캐노니컬, AFF 고유 기능(연도 선택기, `isPastYear` 배너) 보존
- **분류 카테고리 키 불일치** (이주 시 모델 매핑):
  - PE: `ghAll`/`membersAll`/`medicalAll`/`subHeads`
  - AFF: `orgAll`/`agcAll`/`subAll`/`subHeadAll`, `scopeLabels`, `availableYears`, `isPastYear`
- **선행 작업 완료**: PE/AFF 로그인은 디자인 통일됨, AGC 모델·커스텀 평가·역할 도메인은 정합 완료

### B. PE inst-admin 측 추가 정합 (작업 #32 후속)
PE `mypage.html`, `mypage-kpi.html`도 admin header fragment 사용 중. `ev_header` 로 통일 필요 (pwdset/report는 이미 완료).

## 🔐 보안/품질 (참고 — 별도 트랙)

> 코드 분석 중 발견된 항목. 우선순위 별도 판단.

- **DB 비밀번호 평문 커밋** — `application-*.properties` 4개 파일에 노출. 환경변수/시크릿 매니저 이관 필요.
- **슈퍼 어드민 하드코딩** — 사번 `12365478` 단일 계정 의존 (`CustomUserDetailsService` / `CustomAffUserDetailsService`). 인원 변경 시 코드 수정 필요.
- **AFF inst-admin pendingPairs IDOR** — [AffInstAdminPageController.java:193](src/main/java/com/coresolution/pe/controller/AffInstAdminPageController.java#L193) 기관 스코프 가드 누락. PE 쪽은 적용 완료(커밋 `d9580fa`).
- **Report Service silent catch** — `AffEvalReportService`/`EvalReportService` 합 33개 catch 중 일부는 의도된 fallback이지만 디버깅 가시성 0인 케이스 혼재. 사례별 판단 후 로깅 추가 검토.
- **KPI 매퍼의 PE 레거시 역할** — `KpiMapper`/`AffKpiMapper`/`AffKpiInfo2025Mapper`에 `sub_head`/`one_person_sub` 등 소문자 역할 참조 존재. 2025년 데이터 호환 위해 유지 중 — 신규 데이터 표준은 대문자(AFF_*).

## ✅ 최근 완료 (이번 세션)

### 인프라
- **Flyway 도입** — `flyway-core`/`flyway-mysql` 의존성, `db/migration/` 경로, baseline-version=3, repair-on-startup. SQL은 V1~V6 정리.

### 데이터 모델
- **V4 `institutions.kind`** — PE/AFF 배타적 분류 + CHECK + 인덱스. 슈퍼 어드민 UI(생성/수정 폼·목록 컬럼) 반영.
- **V5 `personnel_evaluation_aff.sub_management.institution_id`** — 기관별 sub_code 스코프, users JOIN 없이 직접 필터. 부서 24행 백필.
- **V6 `institutions.agc_code`** — AGC 그룹(정성모아·자야 등) 식별자. 같은 AGC ORG 묶기.

### 인증·권한
- **로그인 진입점 게이트** — `InstAdminAuthenticationProvider`가 요청 URI(`/aff/` prefix)로 expectedKind 결정 → `institution.kind` 불일치 시 `BadCredentialsException`. 회귀 테스트 5건.
- **AFF 역할 표준화** — `userDetail`/`userList`/`uploadPreview`의 PE 역할(`team_head`, `medical_leader` 등) → AFF 역할(`AFF_ORG_HEAD`/`AFF_AGC_HEAD`/`AFF_SUB_HEAD`/`SUB_MEMBER`). `VALID_ROLES` 대문자 통일.

### 평가 대상 자동 생성
- **AFF 도메인 코드 재작성** — `AffAdminTargetService.generateTargets`에서 PE 코드(GH/MEDICAL/SUB_MEMBER_TO_HEAD) 제거 → AFF 12개 코드(S*/A*/O*) 처리.
- **AGC 단위 cross-ORG** — A* 규칙은 같은 `agc_code` 가진 모든 ORG 직원 모아서 매핑. `getUsersWithRolesByOrgList` mapper 추가.
- **6개로 슬림화** — 운영 시트 분석 결과 수직평가 본진 중심으로 슬림. N×N 규칙은 커스텀 메뉴로 분리.

### 커스텀 평가 대상
- **PE inst-admin** — `/pe/inst-admin/custom-targets` 신규. IDOR 가드 (자기 기관 직원만).
- **AFF inst-admin** — `/aff/inst-admin/custom-targets` 신규. 12개 typeCode 노출, dataEv 자동 추론.

### 기관 관리자 운영
- **로그인 ID 변경 기능** — `updateLoginId` mapper/service/controller + 슈퍼 어드민 UI 인라인 폼. `DuplicateKeyException` 처리. 회귀 테스트 7건.
- **비밀번호 재설정 진단성 보강** — rowsAffected 검증, 성공 로그, `@PathVariable` 이름 명시. 1자리 비밀번호 허용으로 정책 통일.

### 직원 측 버그·UX
- **비밀번호 초기화 연도 불일치 수정** — `resetPasswordById(id, year)` 추가. 화면 year로 권한 검증, 실제 초기화는 `currentEvalYear` 테이블에.
- **평가 완료 페이지 배경 이미지** — hero → 메시지 카드 뒤로 이동. 미리보기/실제 일치.
- **end-letter 배경 업로드 500 수정** — `MultipartFile.transferTo`의 상대경로 이슈 → `Files.copy` + 절대경로.
- **평가 완료 메시지 중앙정렬** — PE `.letter-body`에 `text-align: center` 추가.

### 로그인 페이지
- **AFF 로그인 PE 디자인 이주** — `/aff/login`을 PE 모던 디자인으로. "계열사" chip 표시.
- **사번/비밀번호 토글 라벨** — `#pwdLabel` 텍스트도 함께 전환 (양쪽).
- **비밀번호 강도·6자 제약 제거** — 직원·관리자 모두 1자리 허용. 강도 UI/JS 제거.

### 헤더 정합
- **PE pwdSet/report 헤더** — `Include/layout :: ev_header` 신규 fragment + `user_nav_new`로 통일. Info와 동일한 `.ev-header` 디자인.
