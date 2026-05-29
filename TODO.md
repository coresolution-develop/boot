# TODO

운영 중 발견된 수정·개선 사항. 처리 후 항목은 "✅ 최근 완료" 섹션으로 압축·이동.

## 🚀 진행 예정

- **AI 리포트 저데이터 요약 보강 검증** — 평가 데이터(essay/응답자)가 적을 때 GPT 요약이 너무 간결해지던 문제로, 프롬프트에 "데이터 부족 시 작성 정책"을 추가함(아래 ✅). ⚠️ **운영 데이터 없어 실측 미완료.** 작년(2025) 데이터로 검증 필요: ① 짧은 summary 후보 식별 → ② 대상 1명 summary 백업 → ③ `input_hash` 변조로 캐시 무효화 → ④ [코어 AI 요약] 재생성 → ⑤ old/new 길이 비교(목표 ≥1.5배, "표본이 적어..." 문구 확인). 캐시 키: `(eval_year, target_id, data_ev, kind)`, kind ∈ {ESSAY, SCORE, SCORE_KY}, TOTAL은 data_ev='TOTAL'.

## 🔐 보안/품질 (참고 — 별도 트랙)

> 코드 분석 중 발견된 항목. 우선순위 별도 판단.

- **🔴 유출된 DB 비밀번호 로테이션 필요** — `${DB_USERNAME}`/`${DB_PASSWORD}` 환경변수 이관은 완료(아래 ✅)했으나 과거 커밋(`csdev1234`, `Core0220!_!@@`)이 git history에 그대로 남아 있음. dev/prod DB 양쪽 비밀번호를 즉시 교체하고 새 값을 환경변수로 주입해야 실질적 leak 차단됨. (history rewrite는 강제 푸시 영향 커서 별도 결정 필요.)
- **슈퍼 어드민 하드코딩** — 사번 `12365478` 단일 계정 의존 (`CustomUserDetailsService` / `CustomAffUserDetailsService`). 인원 변경 시 코드 수정 필요.
- **Report Service silent catch** — `AffEvalReportService`/`EvalReportService` 합 33개 catch 중 일부는 의도된 fallback이지만 디버깅 가시성 0인 케이스 혼재. 사례별 판단 후 로깅 추가 검토.
- **KPI 매퍼의 PE 레거시 역할** — `KpiMapper`/`AffKpiMapper`/`AffKpiInfo2025Mapper`에 `sub_head`/`one_person_sub` 등 소문자 역할 참조 존재. 2025년 데이터 호환 위해 유지 중 — 신규 데이터 표준은 대문자(AFF_*).

## ✅ 최근 완료 (이번 세션)

### AI 리포트
- **저데이터 요약 보강** — 평가 데이터가 적을 때 GPT(`gpt-4o-mini`) 요약이 한두 문장으로 압축되던 문제. 프롬프트만 강화(비용/모델/호출 횟수 변화 없음). 4개 프롬프트(EssayPrompt/ScorePrompt/TotalPrompt/KyTotalPrompt) system에 "데이터 부족 시 작성 정책" 블록 추가 — 점수·관계 사실 정리 → 표본 한계 명시 → 일반 행동 원칙·관찰 포인트 → 다음 구간 검증 포인트의 구성 요소로 분량 충족, strengths/improvements 정확히 3개 강제, 회피 문장("데이터가 부족합니다"만으로 채우기) 금지. `EvalReportService`(PE)·`AffEvalReportService`(AFF)·`OpenAiCommentSummarizer`(AFF essay 공유 경로) 반영. EssayPrompt user의 "3~5문장" → "5~7문장" baseSchema 정합. 컴파일 통과. ⚠️ 실측 미완료(위 🚀 참조).

### 인프라
- **DB 자격증명 환경변수 이관** — `application(/-local/-dev/-prod).properties` 4개 파일의 평문 username/password 제거 → `${DB_USERNAME}`/`${DB_PASSWORD}` 플레이스홀더(기본값 없음 → 미설정 시 부팅 실패로 누락 즉시 노출). `.env.example` 추가, `.gitignore`에 `.env*` 룰, README에 IDE/PowerShell/setx 사용법 정리. ⚠️ 과거 커밋의 비밀번호는 git history에 남아 있으므로 **DB 비밀번호 로테이션 필수**(보안/품질 섹션 참고).
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

### 보안
- **AFF inst-admin pendingPairs IDOR 가드** — `/aff/inst-admin/api/progress/members/{targetId}/pending`에 기관 스코프 검증. `affLoginMapper.findById(targetId, year)` 후 `c_name` 미일치 시 403. PE `d9580fa`와 동일 패턴. 회귀 테스트 4건.

### AFF report 이주 (PE 정합)
- **본문 리라이트** — `aff/user/report.html` 1719 → 921줄(약 55% 축소). PE `pe/user/report.html` 구조 기반. `Include/afflayout`·`/aff/report` URL·`/js/aff/user/report.js`로 정합. PE 전용 섹션 제거(경혁팀 `isKyTeam`/레이더 `renderRadar`/`row` KPI 표). AFF 컨트롤러 모델 키와 1:1 매핑. AFF 전용 보존: `.area-summaries.no-print`, `print-ai-body` flex, `og:url=/aff/login`. empName 마스킹 없음(AFF 정책).
- **연도 셀렉트 통합** — 본문 `<select id="yearSelect">` → `<input type="hidden">`. 네비 `#un-year-select`가 단일 컨트롤. 폼 submit 시 현재 year 보존.
- **`initPage()` user 모드 감지 버그 수정** — `user_nav_new`(`#user-sidebar`)가 `#user-nav` 부재로 admin 경로로 흘러 `syncYearToForms`가 모든 `input[name="year"]`을 localStorage 연도(2026)로 덮어쓰던 회귀. `isUserMode`를 `#user-nav || #user-sidebar`로 보강. `layout.html`·`afflayout.html` 양쪽 적용. ⚠️ 브라우저 검증 미완료.

### 헤더 정합
- **PE pwdSet/report 헤더** — `Include/layout :: ev_header` 신규 fragment + `user_nav_new`로 통일. Info와 동일한 `.ev-header` 디자인.
- **PE mypage/mypage-kpi 헤더** — 동일 패턴(`ev_header` + `user_nav_new`) 적용, `.main-content { margin-top: 56px }` 회피 규칙 추가.
- **AFF 직원 페이지 헤더 통일** — `Include/afflayout`에 `ev_header`/`user_nav_new` fragment 신규 (PE 미러, `/aff/Info`·`/aff/logout` 라우팅, "계열사" chip). `aff/user/info.html`·`pwdset.html`은 PE 스타일 self-contained 디자인으로 본문까지 이주(연도 선택기·`isPastYear` 배너 보존, 분류 키 `orgAll`/`agcAll`/`subHeadAll`/`subStaffAll` 매핑). `report.html`은 별도 트랙으로 본문 PE 정합 완료(위 "AFF report 이주" 참조).
