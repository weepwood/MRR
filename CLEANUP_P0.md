# P0 Cleanup (2026-03-23)

This document records the low-risk repository cleanup completed in P0.

## 1) Repo Noise Cleanup

Removed tracked temporary/artifact files in frontend root:

- `frontend-repo/dist-2026.03.22.zip`
- `frontend-repo/print_test.html`
- `frontend-repo/test_multiselect_fix.html`

Updated ignore rules:

- `frontend-repo/.gitignore`: added `*.zip`, `print_test.html`, `test_multiselect_fix.html`
- `backend-repo/.gitignore`: added `application-local.properties`

## 2) Frontend Log Noise Cleanup

Removed page-level `console.log` debug output from:

- `frontend-repo/src/components/ImageGalleryEl.vue`
- `frontend-repo/src/components/ImageGalleryEl-3.vue`
- `frontend-repo/src/components/admin/RecordsView.vue`
- `frontend-repo/src/components/ImageGallery.vue`
- `frontend-repo/src/components/PrintPage.vue`
- `frontend-repo/src/components/HelloWorld.vue`
- `frontend-repo/src/components/MessageToastDemo.vue`

Also removed one unused router import:

- `frontend-repo/src/router/index.js`

## 3) Backend Config Decoupling

Converted hard-coded machine paths/secrets in main config to env-driven defaults:

- `backend-repo/src/main/resources/application.properties`

Added local profile example template:

- `backend-repo/src/main/resources/application-local.example.properties`

Removed stale backup config:

- `backend-repo/src/main/resources/application.old.properties`

Updated backend run notes:

- `backend-repo/README.md`
