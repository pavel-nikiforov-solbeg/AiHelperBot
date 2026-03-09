# Security Check Plan

**Date**: 2026-03-09
**Scope**: All project code, properties, configuration files

---

## Findings Summary

| # | Severity | File | Issue |
|---|----------|------|-------|
| 1 | CRITICAL | `.env` | Real API key and DB password in plaintext |
| 2 | CRITICAL | (missing) | No `.gitignore` at project root — `.env` is unprotected from accidental commit |
| 3 | HIGH | `application.yml` | `api-key` missing from `rag.llm` section — LLM API key not wired via env var |
| 4 | MEDIUM | (missing) | No `.env.example` template file |
| 5 | MEDIUM | (missing) | No `README.md` documenting required environment variables |

---

## Detailed Findings

### Finding 1 — CRITICAL: Real secrets in `.env`

**File**: `.env`

```
OPEN_ROUTER_API_KEY=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
DB_PASSWORD=12gonzik09
```

This file should never contain real values in source control. The `.env` file is the correct mechanism for local secrets, BUT only if it is `.gitignore`'d (see Finding 2).

**Action**: Revoke the exposed OpenRouter API key immediately and rotate the DB password.

---

### Finding 2 — CRITICAL: No `.gitignore` at project root

**File**: (missing — only `.idea/.gitignore` exists)

The `.env` file is NOT excluded from Git. Any `git add .` or `git commit` will include it, leaking secrets to the repository.

**Action**: Create `.gitignore` at project root.

---

### Finding 3 — HIGH: `api-key` missing from `application.yml`

**File**: `src/main/resources/application.yml`, lines 29–37 (the `rag.llm` section)

The `rag.llm` section currently has:
```yaml
rag:
  llm:
    base-url: ${LLM_BASE_URL:https://openrouter.ai/api/v1}
    model: ${LLM_MODEL:}
    # ... other properties ...
```

`api-key` is absent. The `.env` defines `OPEN_ROUTER_API_KEY` but there is no `api-key: ${OPEN_ROUTER_API_KEY}` binding in the main config. The test profile sets `api-key: test-key` directly, confirming the property is used at runtime.

**Action**: Add `api-key: ${OPEN_ROUTER_API_KEY}` (or `${OPEN_ROUTER_API_KEY}`) to `application.yml` under `rag.llm`.

---

### Finding 4 — MEDIUM: No `.env.example`

**File**: (missing)

Per the security rules in CLAUDE.md, every secret must have a matching entry in `.env.example` (without the actual value). Currently no such file exists.

**Action**: Create `.env.example` listing all required environment variables.

---

### Finding 5 — MEDIUM: No `README.md`

**File**: (missing)

Per the security rules in CLAUDE.md, `README.md` should document environment variables under an "Environment Variables" section. Currently no README exists.

**Action**: Create `README.md` with at minimum an "Environment Variables" section.

---

## Files Checked — Clean

| File | Status | Notes |
|------|--------|-------|
| `src/main/resources/application.yml` | MOSTLY CLEAN | Missing `api-key` binding (Finding 3) |
| `src/test/resources/application-test.yml` | CLEAN | Test placeholders (`test-key`, empty password for H2) are acceptable |
| `pom.xml` | CLEAN | No credentials |
| `.mvn/wrapper/maven-wrapper.properties` | CLEAN | Only Maven distribution URL |
| `src/main/java/.../config/RagConfig.java` | CLEAN | Reads `apiKey` from injected `LlmProperties` |
| `src/main/java/.../config/properties/LlmProperties.java` | CLEAN | No hardcoded values |
| `src/main/resources/db/changelog/**` | CLEAN | No credentials in Liquibase migrations |
| `.idea/.gitignore` | CLEAN | IDE-specific ignores only |

---

## Planned Changes

### Change 1: Create `.gitignore`

**File to create**: `.gitignore`

```gitignore
# Secrets — never commit
.env
.env.local
.env.*.local

# Build output
target/

# IDE
.idea/
*.iml
*.iws
*.ipr

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/

# Local vector store cache
%TEMP%/sas-rag/
```

---

### Change 2: Add `api-key` to `application.yml`

**File**: `src/main/resources/application.yml`
**Location**: `rag.llm` section (after `base-url`)

Add:
```yaml
    api-key: ${OPEN_ROUTER_API_KEY}
```

Note: The `.env` currently uses the variable name `OPEN_ROUTER_API_KEY`
- **Option B**: Use `${OPEN_ROUTER_API_KEY}` to match the existing `.env` key exactly.

Either way, the variable name must be consistent between `.env`, `.env.example`, and `application.yml`.

---

### Change 3: Create `.env.example`

**File to create**: `.env.example`

```env
# PostgreSQL
DB_PASSWORD=

# LLM API (e.g. OpenRouter)
OPEN_ROUTER_API_KEY=

# LLM settings
LLM_BASE_URL=https://openrouter.ai/api/v1
LLM_MODEL=
LLM_HTTP_REFERER=

# Azure Blob Storage
AZURE_BLOB_URL=

# PDF guide URL
GUIDE_URL=
```

---

### Change 4: Create `README.md` (Environment Variables section)

**File to create**: `README.md`

Must include at minimum:

```markdown
## Environment Variables

Copy `.env.example` to `.env` and fill in the values:

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_PASSWORD` | Yes | PostgreSQL password for `bot_user` |
| `OPEN_ROUTER_API_KEY` | Yes | API key for the LLM provider (e.g. OpenRouter) |
| `LLM_BASE_URL` | No | LLM API base URL (default: `https://openrouter.ai/api/v1`) |
| `LLM_MODEL` | Yes | Model identifier (e.g. `openai/gpt-4o`) |
| `LLM_HTTP_REFERER` | No | HTTP Referer header sent with LLM requests |
| `AZURE_BLOB_URL` | No | Azure Blob Storage connection string (degraded mode if absent) |
| `GUIDE_URL` | No | URL to the PDF user guide passed in LLM payload |
```

---

## Execution Order

1. **Create `.gitignore`** — Prevents accidental secret commit going forward.
2. **Update `application.yml`** — Add `api-key: ${OPEN_ROUTER_API_KEY}` (agree on variable name first).
3. **Update `.env`** — Rename variable if adopting `OPEN_ROUTER_API_KEY`.
4. **Create `.env.example`** — Document all required variables.
5. **Create `README.md`** — Document environment variables section.
6. **Verify** — Run `mvnw.cmd test` to confirm nothing is broken.
