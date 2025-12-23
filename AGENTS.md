# Repository Guidelines
## Communication
- Default to replying in Chinese unless the user explicitly asks for another language.

## Project Structure & Module Organization

- `src/main/java`: core library code (AOP interceptor, lock executors, annotations, auto-configuration).
- `src/main/resources`: Spring Boot auto-configuration imports at `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- `src/test/java`: unit and integration-style tests for AOP, key resolution, and executor implementations.
- `pom.xml`: Maven build, dependencies, and plugin configuration.

## Build, Test, and Development Commands

- `mvn clean test`: run the full test suite with JaCoCo coverage instrumentation.
- `mvn -DskipTests package`: build the library JAR without running tests.
- `mvn test -Dtest=DistributedLockInterceptorTest`: run a single test class.
- `mvn -Prelease deploy`: release profile for publishing (signing and publishing plugins enabled).

## Coding Style & Naming Conventions

- Java 17, 4-space indentation, UTF-8 source encoding.
- Package names are lowercase; class names use UpperCamelCase.
- Keep license headers in all `src/**/*.java` files; the `license-maven-plugin` runs during `validate` and will format headers.
- Avoid introducing non-ASCII characters unless already present and required.

## Testing Guidelines

- Tests live under `src/test/java` and use JUnit 5 via `spring-boot-starter-test` (AssertJ + Mockito).
- Test naming follows `*Test` suffix (e.g., `DistributedLockInterceptorTest`).
- Use mocks for executor dependencies; integration tests should remain self-contained.

## Commit & Pull Request Guidelines

- Only one commit exists; no formal convention is established. Use concise, imperative subjects (e.g., “Add Redisson lock executor”).
- PRs should include a short description, rationale, and relevant test commands/output.
- Link GitHub issues if applicable; attach screenshots only when changing documentation or diagrams.

## Configuration & Security Notes

- Lock behavior is driven by `DistributedLockProperties` and annotation parameters; prefer explicit `lockType`, `waitTime`, and `leaseTime` in new features.
- Executor modules are optional dependencies; keep new executors isolated under `com.childrengreens.distributedlock.executor`.
