# MRR Backend

Swagger UI: `http://localhost:18045/v1/swagger-ui/index.html`

## Local Run

1. Configure environment variables (recommended), or create `src/main/resources/application-local.properties` from `application-local.example.properties`.
2. Start service on your preferred port.

Common env vars:

- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `IMAGE_URL`
- `IMAGE_BASE_PATH`
- `IMAGE_USERNAME`
- `IMAGE_PASSWORD`
- `AES_SECRET_KEY`

Example PowerShell:

```powershell
$env:SERVER_PORT="18045"
$env:SPRING_DATASOURCE_URL="jdbc:sqlite:D:\Project\MRR\data\scan_test.db"
$env:IMAGE_BASE_PATH="D:\data\img"
$env:AES_SECRET_KEY="replace-with-32-byte-secret"
```
