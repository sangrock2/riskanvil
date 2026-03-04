# Generated API Client

This directory is generated from OpenAPI.

Generate or refresh:

```powershell
pwsh -File scripts/generate_openapi_client.ps1
```

Sync from running backend and generate:

```powershell
pwsh -File scripts/generate_openapi_client.ps1 -SyncFromBackend -BackendBaseUrl http://localhost:8080
```

Do not hand-edit generated files.

Direct command:

```bash
npm --prefix frontend run openapi:generate
```
