# CI, release, and security workflows

What the GitHub Actions workflows in this repository run, and what a green
check does not prove.

Verified against `.github/workflows/ci.yml`, `cd.yml`, `release.yml`,
`security.yml`, `.github/dependabot.yml`, `.github/BRANCH_PROTECTION.md`,
`build.gradle.kts` (`qualityGate`, JaCoCo), `Dockerfile`, and
`src/main/resources/application-test.yml`. The only test class on this branch
is `NavigationPermissionRulesTest`.

## Intent

- On pull requests and on `main` / `develop`, compile, format-check, and test.
- On `develop`, `main`, and a published GitHub Release, build and push a
  container image to GHCR.
- On a version tag, build a release JAR and open a GitHub Release.
- On pull requests to `main`, plus a weekly schedule, run CodeQL, a dependency
  review, Trivy, and TruffleHog.

Deploy steps in `cd.yml` do not SSH or apply a compose file. They print a
placeholder. A successful CD workflow is an image push plus that placeholder.

## Workflow map

| File | Triggers | Cancels older runs |
|------|----------|--------------------|
| `ci.yml` | Push to `main` or `develop`; pull request into either | Yes (`ci-${{ github.ref }}`) |
| `cd.yml` | Push to `main` or `develop`; `release` published | No |
| `release.yml` | Tag `vMAJOR.MINOR.PATCH` or `vMAJOR.MINOR.PATCH-*` | No (no concurrency block) |
| `security.yml` | Pull request into `main`; Mondays 06:00 UTC; `workflow_dispatch` | No |

`BRANCH_PROTECTION.md` expects required checks named `Build & Test` and
`CodeQL Analysis`. Those strings match the job `name` values in `ci.yml` and
`security.yml`. Security does not run on pull requests that target only
`develop`.

Dependabot (`.github/dependabot.yml`) opens weekly Monday updates for Gradle,
GitHub Actions, and Docker. Gradle updates are grouped for Spring and for
Testcontainers/JUnit, and are labeled `dependencies` / `java`.

## CI (`ci.yml`)

Job `Build & Test` on `ubuntu-latest`, timeout 15 minutes. Java 17 Temurin.
Gradle cache is read-only unless the ref is `refs/heads/main` or
`refs/heads/develop`.

Order:

1. `./gradlew spotlessCheck`
2. `./gradlew compileJava compileTestJava`
3. `./gradlew test` with `SPRING_PROFILES_ACTIVE=test` and `DB_*` pointed at a
   Postgres 16 service (`ecom360_test` / `postgres` / `postgres` on port 5432)
4. `./gradlew jacocoTestReport` (`if: always()`)
5. Upload test HTML, JaCoCo HTML, and JaCoCo XML as artifacts (7, 7, and 3 days)
6. `dorny/test-reporter` on `build/test-results/test/*.xml` (`fail-on-empty: false`)
7. `./gradlew bootJar -x test` and upload `build/libs/*.jar` for 3 days

`application-test.yml` documents a Testcontainers fallback and defaults
`spring.datasource.url` to `jdbc:postgresql://localhost:5432/ecom360_test`.
Testcontainers is a test dependency, and CI starts Postgres, but
`NavigationPermissionRulesTest` is a plain JUnit test: it does not start Spring
or open a database. A green `Build & Test` currently means that class passed,
Spotless passed, and `bootJar` produced a JAR.

`tasks.qualityGate` (Spotless, compile, test, JaCoCo report, and
`jacocoTestCoverageVerification` at 50% line coverage) is not invoked. The
coverage verification task is not a CI failure condition. The workflow
permission comment mentions a coverage PR comment; no step posts one.

JWT in the test step is `JWT_SECRET=ci-test-secret-key-min-32-characters-long-for-hmac`.
`application-test.yml` also sets its own `jwt.secret` when the test profile
loads. The unit test does not read either value.

## CD (`cd.yml`)

Permissions: `contents: read`, `packages: write`, `id-token: write`. Registry
`ghcr.io`, image name `github.repository`. Platform `linux/amd64` only. Build
cache is GitHub Actions cache (`type=gha`, `mode=max`).

### Image

`build-image` (timeout 20 minutes) checks out the repo, computes a version,
logs in with `github.actor` and `GITHUB_TOKEN`, then
`docker/build-push-action` pushes:

- tags from `docker/metadata-action`: branch ref, semver `{{version}}`, semver
  `{{major}}.{{minor}}`, and `sha-` prefix (semver patterns apply when the ref
  is a version tag)
- plus `ghcr.io/<repo>:<version>`

`<version>` is:

| Event | Value |
|-------|--------|
| `release` | `github.event.release.tag_name` (includes the `v` prefix) |
| Push to `main` | `main-` + 7-character SHA |
| Anything else (`develop`) | `dev-` + 7-character SHA |

The image build is the `Dockerfile`. Its build stage runs
`./gradlew bootJar --no-daemon -x test`. CD does not run unit tests.

### Deploy jobs

| Job | When | GitHub environment |
|-----|------|--------------------|
| `Deploy → Staging` | `github.ref == refs/heads/develop` | `staging` (`vars.STAGING_URL`) |
| `Deploy → Production` | `github.ref == refs/heads/main` or event `release` | `production` (`vars.PRODUCTION_URL`) |

The production job comment says the environment should require manual approval.
That gate is a GitHub environment setting, not a `required_reviewers` key in
the workflow file.

Both deploy scripts echo a placeholder. The SSH `docker compose` blocks and the
`kubectl set image` block are comments. `STAGING_HOST`, `STAGING_USER`,
`STAGING_SSH_KEY`, `PRODUCTION_HOST`, `PRODUCTION_USER`, and
`PRODUCTION_SSH_KEY` are passed into the step and unused while those lines stay
commented.

Health verification runs only when `vars.STAGING_URL` or `vars.PRODUCTION_URL`
is non-empty. It curls `{URL}/actuator/health` up to 30 times, 5 seconds apart,
and fails the job if all attempts fail. An empty URL skips the check, so the
placeholder echo is enough for the job to pass.

A published release (tag ref, not `main`) runs production deploy and does not
run staging. A push to `main` runs production deploy with version
`main-<sha>`, not the Gradle version in `build.gradle.kts`.

## Release (`release.yml`)

Tag push matching `v[0-9]+.[0-9]+.[0-9]+` or the same pattern plus `-` and a
suffix (for example `v1.2.3-rc.1`).

1. `Validate Release` strips the leading `v` and writes it to the step summary.
   It does not read `build.gradle.kts` and does not fail on a mismatch. The
   current Gradle version uses a `+` build suffix (`1.8.1+20262707`), which
   this tag filter does not accept.
2. `Build Release` runs `./gradlew clean test jacocoTestReport bootJar` against
   the same Postgres 16 service and test env as CI. It does not run
   `spotlessCheck` or JaCoCo verification. The JAR artifact is kept 90 days as
   `release-jar-<tag>`.
3. `Create GitHub Release` uses `softprops/action-gh-release` with
   `generate_release_notes: true`, `draft: false`, and `prerelease` when the tag
   contains `-`. The JAR is attached.

Publishing that release fires `cd.yml` (`release: types: [published]`), which
builds an image tagged with the tag name and takes the production deploy path.
`release.yml` itself does not push an image.

## Security (`security.yml`)

| Job | What it does | Failure behavior |
|-----|----------------|------------------|
| `CodeQL Analysis` | Java `security-and-quality` queries. Builds with `./gradlew compileJava`. | CodeQL action failure fails the job |
| `Dependency Audit` | On pull requests only, `actions/dependency-review-action` with `fail-on-severity: high` and `deny-licenses: GPL-2.0, GPL-3.0`. Then `./gradlew dependencies` with stdout discarded. | High-severity dependency diff or a denied license fails the PR job. The Gradle listing does not scan CVEs. Scheduled runs skip the review action. |
| `Container Scan` | `docker build -t ecom360-scan:latest .`, then Trivy `CRITICAL,HIGH`, SARIF uploaded | `exit-code: 0`, so findings do not fail the job |
| `Secret Detection` | TruffleHog on full history (`fetch-depth: 0`) with `--only-verified` | Action failure fails the job; unverified findings are not reported |

Trivy and CodeQL both upload SARIF (`github/codeql-action/upload-sarif` and
`analyze`).

## Local equivalents

```bash
./gradlew spotlessCheck
./gradlew test
./gradlew jacocoTestReport
./gradlew qualityGate
./gradlew bootJar -x test
```

`qualityGate` is stricter than CI because it includes the 50% JaCoCo
verification rule. JaCoCo excludes `**/dto/**`, `**/config/**`, and
`**/Ecom360Application*`.

## Pitfalls

- Green CD does not deploy the process. Wire the commented SSH or Kubernetes
  step before treating the environment as updated.
- Staging and production health checks probe `/actuator/health` on
  `vars.STAGING_URL` / `vars.PRODUCTION_URL`. They do not use the Dockerfile
  probe (`http://localhost:8080/actuator/health/liveness`) or the prod
  management port (`8081` in `application-prod.yml`).
- CI will cancel an in-progress run on the same ref. CD will not.
- Release validation does not compare the tag to the Gradle version.
- Security scanning does not run for pull requests into `develop`.
- Trivy is informational (`exit-code: 0`).
