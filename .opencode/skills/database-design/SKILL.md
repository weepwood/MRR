---
name: database-design
description: |
  Database schema design, migration strategy, indexing, normalization,
  and SQL optimization for Spring Boot 4 + MyBatis 4 projects.
  Covers PostgreSQL 16 (production) and SQLite (dev/test).
license: MIT
---

# Database Design Skill

## Domain Context

This skill targets medical-record systems. Core domain concepts:
- **BAH** (病案号) — primary medical record identifier, 6-8 digit numeric string
- **BRXH** (病人序号) — patient sequence number
- **SJH** (上架号) — shelf number, alternative identifier
- **Scan** — a scanned image record with metadata
- **Patient** — patient demographic info linked via BAH

---

## 1. Naming Conventions

### Tables
- Lowercase with underscores: `mr_scan`, `mr_patient`, `access_log`
- Prefix domain tables with `mr_` (medical record)
- Prefix auth tables with `mr_auth_`
- Prefix operational/log tables without prefix

### Columns
- **Prefer snake_case**: `upload_flag`, `migration_status`, `last_login_at`
- **Avoid** mixing cases like `BRXH`, `BAH`, `openerno` — these are legacy carry-overs
- Use descriptive names: `password_hash` not `pwd`, `created_at` not `crt_dt`
- Boolean flags: `is_deleted`, `upload_flag`, `is_active`
- Foreign key: `scan_id` referencing `mr_scan.id`

### Indexes
- `idx_{table}_{column}` for single-column
- `idx_{table}_{col1}_{col2}` for composite

---

## 2. Normalization & Schema Design

### Always add
- `id BIGSERIAL PRIMARY KEY` (PostgreSQL) or `INTEGER PRIMARY KEY AUTOINCREMENT` (SQLite)
- `created_at` / `updated_at` timestamps on every table
- `NOT NULL` + defaults for columns that have sensible defaults

### Constraints
- Add **foreign keys** for referential integrity:
  ```sql
  scan_id INTEGER NOT NULL REFERENCES mr_scan(id) ON DELETE CASCADE
  ```
- Add **CHECK** constraints for enum-like columns:
  ```sql
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active', 'disabled'))
  ```
- Add **UNIQUE** for business keys:
  ```sql
  username TEXT NOT NULL UNIQUE
  ```

### Avoid
- Polymorphic associations (type + id pattern)
- JSON columns unless the data genuinely has no fixed schema
- TEXT for dates — use `TIMESTAMP` / `DATE` / `DATETIME`
- Wide tables (>20 columns) — consider vertical partitioning

---

## 3. Indexing Strategy

### Always index
- Primary keys (auto-indexed)
- Foreign key columns
- Columns in `WHERE`, `JOIN`, `ORDER BY`, `GROUP BY`
- Columns in `LIKE 'prefix%'` patterns (use B-tree, not hash)

### Common patterns in this project
```sql
-- BAH is the most frequent query filter
CREATE INDEX idx_mr_scan_bah ON mr_scan(BAH);

-- Composite for folder-based queries
CREATE INDEX idx_mr_scan_folder_bah ON mr_scan(folder, BAH);

-- Lookup by patient sequence
CREATE INDEX idx_mr_scan_brxh ON mr_scan(BRXH);

-- Stats queries by date
CREATE INDEX idx_mr_statistics_date ON mr_statistics(date);

-- Access log cleanup queries
CREATE INDEX idx_access_log_time ON access_log(access_time);

-- Pending OSS migration queries
CREATE INDEX idx_mr_scan_migration ON mr_scan(migration_status, upload_flag)
  WHERE upload_flag != 0;
```

### Index Guidelines
- **Partial indexes** for sparsely-queried subsets (e.g., pending migrations)
- **Covering indexes** for hot query paths (include all selected columns)
- Avoid indexes on low-cardinality columns (<100 distinct values) alone
- Monitor index size vs query benefit; remove unused indexes
- Use `pg_stat_user_indexes` on PostgreSQL to find unused indexes

---

## 4. Query Optimization

### MyBatis-Specific
- Use `<script>` tags for dynamic SQL, not string concatenation
- Prefer `@Select` annotations for simple queries, XML for complex ones
- Use `@Param` annotations on all Mapper method parameters
- Pagination: always use `LIMIT #{limit} OFFSET #{offset}`, never `OFFSET` alone

### Anti-Patterns
```
❌ SELECT * FROM mr_scan  -- pulls all columns, breaks covering indexes
❌ WHERE folder LIKE '%2024%'  -- leading wildcard prevents index usage
❌ ORDER BY RANDOM() LIMIT 1  -- full scan for random row
❌ N+1 queries in loops — batch with IN clause instead
❌ Implicit type coercion (comparing TEXT to INTEGER)
```

### Pagination
```sql
-- Good: keyset pagination for large offsets
SELECT * FROM mr_scan WHERE id > #{cursor} ORDER BY id LIMIT #{size}

-- Acceptable: offset pagination with small page numbers
SELECT * FROM mr_scan ORDER BY id LIMIT #{size} OFFSET #{offset}
```

---

## 5. Migration & Versioning

### Flyway conventions (this project uses Flyway)
- Files: `V1__init.sql`, `V2__add_scan_indexes.sql`, `V3__alter_scan_add_oss_url.sql`
- One migration per logical change
- Never edit a committed migration — create a new one
- Use `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` for idempotency

### Schema changes
```sql
-- Safe column addition
ALTER TABLE mr_scan ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT FALSE;

-- Safe index creation (concurrent to avoid table lock on PostgreSQL)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mr_scan_bah_date
  ON mr_scan(BAH, uploaddate);
```

---

## 6. PostgreSQL-Specific Features

### Use when on PostgreSQL 16 (production)
- `GENERATED AS IDENTITY` instead of `SERIAL`
- `INCLUDE` in indexes for covering indexes:
  ```sql
  CREATE INDEX idx_mr_scan_bah_cover ON mr_scan(BAH) INCLUDE (filename, pages);
  ```
- `RETURNING` clause for INSERT/UPDATE/DELETE:
  ```java
  @Insert("INSERT INTO mr_scan (...) VALUES (...) RETURNING *")
  Scan insertAndReturn(Scan scan);
  ```
- `pg_stat_statements` for query performance analysis

### SQLite compatibility (dev/test)
- Use `INTEGER PRIMARY KEY AUTOINCREMENT` (not `BIGSERIAL`)
- Avoid PostgreSQL-only data types (`JSONB`, `ARRAY`, `TSVECTOR`)
- Avoid `CREATE INDEX CONCURRENTLY`
- Use `CURRENT_TIMESTAMP` not `NOW()`

---

## 7. Entity Relationship Principles

### One-to-Many
```sql
-- mr_patient 1→* mr_scan (via BAH)
-- Add FK when BAH becomes a proper identifier table
ALTER TABLE mr_scan ADD CONSTRAINT fk_scan_bah
  FOREIGN KEY (BAH) REFERENCES mr_patient(BAH);
```

### Many-to-Many
```sql
-- Use a junction table, not comma-separated IDs
CREATE TABLE mr_scan_tag (
    scan_id INTEGER NOT NULL REFERENCES mr_scan(id),
    tag     TEXT NOT NULL,
    PRIMARY KEY (scan_id, tag)
);
```

### Soft Delete
- Use a `deleted_at TIMESTAMP` column (nullable)
- Add `WHERE deleted_at IS NULL` to all query methods
- Include a partial index: `CREATE INDEX ... WHERE deleted_at IS NULL`

---

## 8. Checklist for New Tables

1. Does it have `id`, `created_at`, `updated_at`?
2. Are foreign keys properly constrained?
3. Are query-filtered columns indexed?
4. Is the naming convention consistent with existing tables?
5. Are CHECK constraints added for enum columns?
6. Does it need a Flyway migration?
7. Is there a corresponding entity + mapper in the Java code?
8. Are text-based date columns avoided in favor of proper date types?
