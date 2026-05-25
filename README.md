# boot

## 환경변수 설정

DB 자격증명은 `.properties` 파일이 아닌 환경변수로 주입한다. 미설정 시 부팅 실패.

필수 변수: `DB_USERNAME`, `DB_PASSWORD` (선택: `OPENAI_API_KEY`)

설정 방법(택1):

**IntelliJ Run Configuration**
1. Run → Edit Configurations
2. Environment variables 칸에 `DB_USERNAME=...;DB_PASSWORD=...` 추가

**PowerShell (현재 세션)**
```powershell
$env:DB_USERNAME = "csdev"
$env:DB_PASSWORD = "..."
.\gradlew bootRun
```

**Windows 영구 설정**
```powershell
setx DB_USERNAME "csdev"
setx DB_PASSWORD "..."
```
(새 셸부터 적용)

자세한 변수 목록은 [.env.example](.env.example) 참고.

## 프로필

| 프로필 | DB | 용도 |
|---|---|---|
| `local` | dev.sosyge.net | 로컬 개발 |
| `dev`   | dev.sosyge.net | 개발 서버 |
| `prod`  | hr.sosyge.net  | 운영 |
| (none)  | hr.sosyge.net  | base — 가급적 프로필 명시 |

실행 예: `./gradlew bootRun --args='--spring.profiles.active=local'`
