# Profile Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a personal information page where the current user can edit email and phone.

**Architecture:** Keep the profile update scoped to the authenticated user. The backend exposes `PUT /api/auth/me` and returns an updated `UserVO`; the frontend updates Pinia and local storage after saving.

**Tech Stack:** Spring Boot 3, MyBatis-Plus, Vue 3, Pinia, Vue Router, Element Plus.

---

### Task 1: Add A Feature Check

**Files:**
- Create: `teamflow-ai-frontend/scripts/check-profile-feature.mjs`

- [ ] Write a Node assertion script that checks for `ProfileUpdateDTO`, `PUT /api/auth/me`, `AuthService.updateMe`, frontend `updateMe`, `/profile` route, sidebar menu entry, and `Profile.vue`.
- [ ] Run `node scripts/check-profile-feature.mjs` and verify it fails before implementation.

### Task 2: Add Backend Profile Update

**Files:**
- Create: `teamflow-ai-backend/src/main/java/com/example/teamflow/dto/ProfileUpdateDTO.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/controller/AuthController.java`
- Modify: `teamflow-ai-backend/src/main/java/com/example/teamflow/service/AuthService.java`

- [ ] Add DTO fields `email` and `phone`.
- [ ] Add `PUT /api/auth/me` endpoint.
- [ ] Update only the current user's `email`, `phone`, and `updateTime`.
- [ ] Return updated `UserVO`.

### Task 3: Add Frontend Profile Page

**Files:**
- Modify: `teamflow-ai-frontend/src/api/auth.js`
- Modify: `teamflow-ai-frontend/src/stores/user.js`
- Modify: `teamflow-ai-frontend/src/router/index.js`
- Modify: `teamflow-ai-frontend/src/views/Layout.vue`
- Create: `teamflow-ai-frontend/src/views/Profile.vue`

- [ ] Add `updateMe(data)` API.
- [ ] Add `updateProfile(payload)` store action.
- [ ] Register `/profile`.
- [ ] Add sidebar entry `个人信息`.
- [ ] Build a Profile page with read-only username, real name, role, student number and editable email, phone.

### Task 4: Verify

**Commands:**
- `node scripts/check-profile-feature.mjs`
- `npm run build`

- [ ] Run both commands from `teamflow-ai-frontend`.
- [ ] Confirm both commands exit with code 0.
