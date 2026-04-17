# Project Guidelines

## Code Style
Java 17 with Lombok @Data for entities. Use Jakarta validation imports (not javax). MyBatis annotation-based SQL with snake_case to camelCase mapping enabled.

## Architecture
Spring Boot 3.4.7 web application with dual evaluation domains (PE and AFF). Layered: Controller → Service → Mapper (MyBatis) → Entity. Year-based table routing. Security with custom authentication providers.

## Build and Test
Build: `./gradlew bootWarDev` or `./gradlew bootWarProd` for WAR files. Run: `./gradlew bootRunDev` (port 9090). Test: `./gradlew test` (JUnit 5).

## Conventions
Application properties control eval-year (default 2026). Auto-initialization creates year-specific tables on startup. Soft deletes with del_yn != 'N'. SHA256/BCrypt password coexistence. See HELP.md for Spring Boot references.