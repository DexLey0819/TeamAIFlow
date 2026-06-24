# Verification And CI Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first phase of the phased compatible refactor by fixing stale verification, adding one local verification command, adding low-risk backend unit tests, and introducing CI.

**Architecture:** Keep application behavior unchanged. Use existing Node static-check scripts for frontend contract checks, add a root shell script as the local verification entrypoint, add pure backend unit tests that do not need MySQL, and wire the same checks into GitHub Actions.

**Tech Stack:** Vue 3/Vite/Node.js frontend scripts, Java 17/Spring Boot/JUnit 5 backend tests, Bash local verification, GitHub Actions CI.

---

## Scope Note

The approved spec covers several independent subsystems: verification/CI, route safety, frontend decomposition, AI service modularization, and configurable role-agent profiles. This plan intentionally implements only Phase 1: verification and CI baseline. Route refactor, frontend splitting, AI modularization, and role-agent data model should each get their own implementation plan after this baseline passes.

Current workspace note: `/Users/dexley/Documents/TeamFlowAI` does not contain a `.git` directory, so commit steps cannot run in this local copy. If executing from a Git checkout, commit after each task using the command shown in that task.

## File Structure

- Modify: `teamflow-ai-frontend/scripts/check-join-redirect.mjs`
  - Responsibility: assert the join-project redirect flow and demo applicant seed data match the current `USER`/`ADMIN` role model.
- Modify: `teamflow-ai-frontend/package.json`
  - Responsibility: expose named check commands and a single frontend `check` command.
- Create: `scripts/verify.sh`
  - Responsibility: run the local verification baseline from the repository root.
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/common/ResultTest.java`
  - Responsibility: pure unit tests for the common API response wrapper.
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/security/JwtUtilTest.java`
  - Responsibility: pure unit tests for JWT token generation, parsing, invalid-token handling, and secret validation.
- Create: `.github/workflows/ci.yml`
  - Responsibility: run frontend checks/build and backend tests in CI.
- Modify: `README.md`
  - Responsibility: document local verification and CI expectations.

## Task 1: Fix The Stale Join Redirect Check

**Files:**
- Modify: `teamflow-ai-frontend/scripts/check-join-redirect.mjs`

- [ ] **Step 1: Run the current failing check**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-join-redirect.mjs
```

Expected: FAIL with an assertion similar to `README should document the clean applicant account`, because the script still expects `STUDENT` while the current README documents `applicant` as `USER`.

- [ ] **Step 2: Update the role assertions**

Replace the final README assertion in `teamflow-ai-frontend/scripts/check-join-redirect.mjs`:

```js
assert.match(readmeSource, /\| applicant \| STUDENT \| 新申请演示账号 \|/, 'README should document the clean applicant account')
```

With these assertions:

```js
assert.match(readmeSource, /\| applicant \| USER \| 新申请演示账号 \|/, 'README should document the clean applicant account as USER')
assert.doesNotMatch(readmeSource, /\| applicant \| STUDENT \|/, 'README should not refer to the removed STUDENT role')
assert.match(
  databaseSource,
  /\(9,\s*'applicant'[\s\S]*?'USER',\s*1\)/,
  'database seed should create the clean applicant account as USER'
)
assert.doesNotMatch(databaseSource, /\bSTUDENT\b/, 'database seed should not use the removed STUDENT role')
```

- [ ] **Step 3: Run the fixed check**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
node scripts/check-join-redirect.mjs
```

Expected: PASS and print:

```text
join redirect checks passed
```

- [ ] **Step 4: Commit if Git is available**

Run from the Git checkout root:

```bash
git add teamflow-ai-frontend/scripts/check-join-redirect.mjs
git commit -m "test: align join redirect check with user role model"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 2: Add Frontend Check Scripts

**Files:**
- Modify: `teamflow-ai-frontend/package.json`

- [ ] **Step 1: Update package scripts**

Replace the `scripts` block in `teamflow-ai-frontend/package.json`:

```json
{
  "dev": "vite --host 127.0.0.1",
  "build": "vite build",
  "preview": "vite preview --host 127.0.0.1"
}
```

With:

```json
{
  "dev": "vite --host 127.0.0.1",
  "build": "vite build",
  "preview": "vite preview --host 127.0.0.1",
  "check:join-redirect": "node scripts/check-join-redirect.mjs",
  "check:member-access": "node scripts/check-member-access-boundary.mjs",
  "check:profile": "node scripts/check-profile-feature.mjs",
  "check:project-workspace": "node scripts/check-project-workspace-editor.mjs",
  "check": "npm run check:join-redirect && npm run check:member-access && npm run check:profile && npm run check:project-workspace"
}
```

- [ ] **Step 2: Run the combined frontend check**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run check
```

Expected: PASS and include all four lines:

```text
join redirect checks passed
member access boundary checks passed
profile feature checks passed
project workspace editor checks passed
```

- [ ] **Step 3: Commit if Git is available**

Run from the Git checkout root:

```bash
git add teamflow-ai-frontend/package.json
git commit -m "test: add frontend verification scripts"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 3: Add Root Local Verification Script

**Files:**
- Create: `scripts/verify.sh`

- [ ] **Step 1: Create the script**

Create `scripts/verify.sh` with this exact content:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/teamflow-ai-frontend"
BACKEND_DIR="$ROOT_DIR/teamflow-ai-backend"

echo "==> Frontend checks"
cd "$FRONTEND_DIR"
if [ ! -d node_modules ]; then
  echo "Frontend dependencies are missing. Run: cd teamflow-ai-frontend && npm ci"
  exit 1
fi
npm run check

echo "==> Frontend build"
npm run build

echo "==> Backend tests"
if command -v mvn >/dev/null 2>&1; then
  cd "$BACKEND_DIR"
  mvn test
else
  echo "WARN: mvn was not found; skipped backend tests."
  echo "Install Maven 3.8+ or run backend tests in CI."
fi
```

- [ ] **Step 2: Make the script executable**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI
chmod +x scripts/verify.sh
```

Expected: command exits with code `0`.

- [ ] **Step 3: Run local verification**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI
bash scripts/verify.sh
```

Expected with frontend dependencies installed: frontend checks pass, frontend build passes, and backend tests either run with Maven or print:

```text
WARN: mvn was not found; skipped backend tests.
Install Maven 3.8+ or run backend tests in CI.
```

- [ ] **Step 4: Commit if Git is available**

Run from the Git checkout root:

```bash
git add scripts/verify.sh
git commit -m "test: add root verification script"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 4: Add Low-Risk Backend Unit Tests

**Files:**
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/common/ResultTest.java`
- Create: `teamflow-ai-backend/src/test/java/com/example/teamflow/security/JwtUtilTest.java`

- [ ] **Step 1: Add Result tests**

Create `teamflow-ai-backend/src/test/java/com/example/teamflow/common/ResultTest.java`:

```java
package com.example.teamflow.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResultTest {

    @Test
    void successWithoutDataUsesStandardSuccessShape() {
        Result<Object> result = Result.success();

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void successWithDataKeepsPayload() {
        Result<String> result = Result.success("payload");

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("payload", result.getData());
    }

    @Test
    void failWithoutCodeUsesServerErrorCode() {
        Result<Object> result = Result.fail("操作失败");

        assertEquals(500, result.getCode());
        assertEquals("操作失败", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void failWithCodeKeepsCustomCode() {
        Result<Object> result = Result.fail(403, "没有权限执行该操作");

        assertEquals(403, result.getCode());
        assertEquals("没有权限执行该操作", result.getMessage());
        assertNull(result.getData());
    }
}
```

- [ ] **Step 2: Add JWT tests**

Create `teamflow-ai-backend/src/test/java/com/example/teamflow/security/JwtUtilTest.java`:

```java
package com.example.teamflow.security;

import com.example.teamflow.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtil, "expire", 3_600_000L);
        jwtUtil.validateSecret();
    }

    @Test
    void generatedTokenIsValidAndKeepsUsername() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("pm");
        user.setRole("USER");

        String token = jwtUtil.generateToken(user);

        assertTrue(jwtUtil.isValid(token));
        assertEquals("pm", jwtUtil.getUsername(token));
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertFalse(jwtUtil.isValid("not-a-valid-token"));
    }

    @Test
    void shortSecretIsRejected() {
        JwtUtil invalid = new JwtUtil();
        ReflectionTestUtils.setField(invalid, "secret", "short-secret");
        ReflectionTestUtils.setField(invalid, "expire", 3_600_000L);

        assertThrows(IllegalStateException.class, invalid::validateSecret);
    }
}
```

- [ ] **Step 3: Run the backend unit tests**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-backend
mvn test -Dtest=ResultTest,JwtUtilTest
```

Expected where Maven is installed: PASS with both test classes executed.

Expected in the current local environment where `mvn` is unavailable: command cannot run; record that backend tests are added but local Maven verification is blocked by missing Maven.

- [ ] **Step 4: Commit if Git is available**

Run from the Git checkout root:

```bash
git add teamflow-ai-backend/src/test/java/com/example/teamflow/common/ResultTest.java teamflow-ai-backend/src/test/java/com/example/teamflow/security/JwtUtilTest.java
git commit -m "test: add backend baseline unit tests"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 5: Add GitHub Actions CI

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflow**

Create `.github/workflows/ci.yml` with this exact content:

```yaml
name: CI

on:
  push:
    branches:
      - main
      - master
  pull_request:
  workflow_dispatch:

jobs:
  frontend:
    name: Frontend
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: teamflow-ai-frontend

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: teamflow-ai-frontend/package-lock.json

      - name: Install frontend dependencies
        run: npm ci

      - name: Run frontend checks
        run: npm run check

      - name: Build frontend
        run: npm run build

  backend:
    name: Backend
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: teamflow-ai-backend

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: maven

      - name: Run backend tests
        run: mvn test
```

- [ ] **Step 2: Validate workflow file is present**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI
test -f .github/workflows/ci.yml
```

Expected: command exits with code `0`.

- [ ] **Step 3: Commit if Git is available**

Run from the Git checkout root:

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add frontend and backend verification workflow"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 6: Document Verification Commands

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add local verification documentation**

Insert this section after the existing `## 准备环境` section in `README.md`:

````markdown
## 本地验证

在项目根目录运行：

```bash
cd /Users/dexley/Documents/TeamFlowAI
bash scripts/verify.sh
```

该命令会执行：

- 前端静态功能检查：加入项目重定向、成员/申请权限边界、个人信息页、项目工作区与章节编辑器。
- 前端生产构建：`npm run build`。
- 后端测试：如果本机安装了 Maven，会执行 `mvn test`；如果没有 Maven，会提示跳过后端测试。

也可以分别运行：

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run check
npm run build

cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-backend
mvn test
```

后端本地测试需要 Java 17 和 Maven 3.8+。CI 会在标准 runner 上安装 Java 17 并运行后端测试。
````

- [ ] **Step 2: Add CI documentation**

Insert this section after the new `## 本地验证` section:

````markdown
## CI

GitHub Actions 工作流位于：

```text
.github/workflows/ci.yml
```

CI 会在 push、pull request 和手动触发时运行：

- Node.js 20：`npm ci`、`npm run check`、`npm run build`。
- Java 17：`mvn test`。
````

- [ ] **Step 3: Commit if Git is available**

Run from the Git checkout root:

```bash
git add README.md
git commit -m "docs: document verification and ci"
```

Expected: commit succeeds. In the current local workspace without `.git`, record that the commit step was skipped.

## Task 7: Run Final Phase 1 Verification

**Files:**
- Verify only; no planned file edits.

- [ ] **Step 1: Run frontend checks**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run check
```

Expected: PASS and include:

```text
join redirect checks passed
member access boundary checks passed
profile feature checks passed
project workspace editor checks passed
```

- [ ] **Step 2: Run frontend build**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run build
```

Expected: PASS. Existing Rollup pure-annotation and chunk-size warnings are acceptable if the build exits with code `0`.

- [ ] **Step 3: Run backend tests when Maven is available**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-backend
mvn test
```

Expected where Maven is installed: PASS.

Expected in the current local environment where `mvn` is unavailable: record that backend tests could not be run locally because Maven is missing and the project has no Maven Wrapper.

- [ ] **Step 4: Run root verification script**

Run:

```bash
cd /Users/dexley/Documents/TeamFlowAI
bash scripts/verify.sh
```

Expected: frontend checks pass, frontend build passes, and backend tests run or the Maven warning is printed.

## Phase 1 Completion Criteria

- `teamflow-ai-frontend/scripts/check-join-redirect.mjs` reflects the current `USER`/`ADMIN` role model.
- `npm run check` runs all existing frontend feature checks.
- `bash scripts/verify.sh` is the documented local verification entrypoint.
- Backend has at least two pure unit test classes that do not need MySQL.
- `.github/workflows/ci.yml` runs frontend checks/build and backend tests.
- README documents local verification, Maven prerequisite, and CI behavior.
- Local final verification output is recorded, including any Maven limitation.
