# API Contract (P2 Baseline)

This file records current frontend/backend API alignment.

## Gateway/Proxy Mapping (frontend `vite.config.js`)

- `/api/*` -> backend `/v1/*`
- `/searchApi/*` -> backend `/v2/*`
- `/loginApi` -> backend `/login`

## Auth

- `POST /login`
  - request: `{ username, password }`
  - response: `Result<LoginResponseDTO>`

## Image

- `GET /v1/img-api/{bah}`
- `GET /v1/img-api/download/{BAH}`
- `GET /v1/img-api/image/{BAH}/{BRXH}/{FOLDER}/{FILENAME}`
- `PUT /v1/img-api/updateImageType/{id}`

## Search

- `GET /v2/search/getBAHByID/{idCard}` (default flow)
- `GET /v2/search/getBAHByEncryptID` (reserved/compatibility)
- `GET /v2/search/getBAHByEncryptIDLegacy` (legacy compatibility)

## Scan

- `GET /v1/scan-api/page?page={page}&size={size}`
- `GET /v1/scan-api/{id}`
- `PUT /v1/scan-api/{id}`
- `DELETE /v1/scan-api/{id}`
- `POST /v1/scan-api/batch-download`
  - request: `{ ids: string[] }`
  - response: `application/octet-stream` zip

## Notes

- Frontend `batchDownloadRecords` now targets `/scan-api/batch-download`, which proxies to backend `/v1/scan-api/batch-download`.
- Current default patient lookup is plaintext `idCard`, not encrypted ID.
