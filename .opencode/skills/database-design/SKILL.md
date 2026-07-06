---
name: database-design
description: |
  Database schema design, indexing strategy, migration planning, and SQL
  optimization for Spring Boot 4 + MyBatis 4 + PostgreSQL 16 / SQLite projects.
  Covers normalization, query anti-patterns, Flyway migrations, and
  performance tuning for medical-record domain schemas.
category: backend
risk: safe
source: self
source_type: self
date_added: "2026-06-29"
tags: [database, postgresql, sqlite, schema-design, indexing, migration, mybatis, flyway]
tools: [opencode, claude, cursor]
---

# Database Design

## Overview

Database design guidance tailored to the MRR medical-record system. Covers the full lifecycle: schema modeling → indexing → query optimization → migration → monitoring. Designed for PostgreSQL 16 (production) with SQLite compatibility (dev/test). Assumes Spring Boot 4 + MyBatis 4 + Flyway.

## When to Use This Skill

- Designing a new table or modifying an existing schema
- Adding indexes to fix slow queries
- Writing or reviewing MyBatis Mapper SQL
- Planning a Flyway migration
- Auditing database performance (slow queries, missing indexes, N+1)
- Choosing between normalization levels or denormalization strategies

## Do Not Use When

- The question is about application-level caching (use Redis/application cache instead)
- Only need ORM configuration (Spring Data JPA, not MyBatis)
- The schema already exists and only needs simple CRUD operations

## Core Principles

1. **Schema follows access patterns** — design tables around how the application queries, not just how the domain model looks
2. **Index late, measure first** — add indexes based on real query patterns, not speculation
3. **Migrations are code** — version-controlled, reviewed, tested, reversible
4. **SQLite dev / PG prod** — use a subset that works on both; avoid PG-only features in migrations if the test suite targets SQLite
5. **No premature denormalization** — normalize first, denormalize only when proven necessary

## Decision Checklist

Before writing DDL:

- [ ] Identified the query patterns (read-heavy / write-heavy / mixed)?
- [ ] Chosen proper data types (avoid TEXT for dates, avoid VARCHAR(255) as default)?
- [ ] Planned the index strategy (which columns in WHERE/JOIN/ORDER BY)?
- [ ] Defined foreign key relationships with ON DELETE behavior?
- [ ] Added created_at / updated_at?
- [ ] Considered partitioning for tables expected to exceed 10M rows?
- [ ] Checked SQLite compatibility for dev/test?

---

## Schema Design

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Table | `mr_` prefix for domain, snake_case | `mr_scan`, `mr_patient`, `access_log` |
| Primary key | `id` | `id BIGSERIAL PRIMARY KEY` |
| Foreign key | `{table}_id` | `scan_id` |
| Timestamp | `_at` suffix | `created_at`, `migrated_at` |
| Boolean flag | `_flag` or `is_` prefix | `upload_flag`, `is_deleted` |
| Index | `idx_{table}_{column}` | `idx_mr_scan_bah` |

### Column Types

| Use | PostgreSQL | SQLite |
|-----|-----------|--------|
| Auto-increment PK | `BIGSERIAL` | `INTEGER PRIMARY KEY AUTOINCREMENT` |
| Integer | `INTEGER`, `BIGINT` | `INTEGER` |
| Variable string | `VARCHAR(n)` | `TEXT` |
| Fixed timestamp | `TIMESTAMP` | `TEXT` (ISO 8601) |
| Large text | `TEXT` | `TEXT` |
| Boolean | `BOOLEAN` | `INTEGER` (0/1) |

### Anti-Patterns

❌ **TEXT for dates** — prevents date functions, wastes space, breaks sorting
❌ **VARCHAR(255) as default** — always pick a domain-appropriate length
❌ **No foreign keys** — referential integrity should be in the DB, not just the app
❌ **SELECT \* in production** — breaks covering indexes, wastes I/O
❌ **JSONB for relational data** — JSONB is for semi-structured, not a substitute for normalized tables
❌ **Column-per-date pattern** — (e.g., `day_1, day_2, ...`) use a normalized time-series table instead
❌ **Generic ID columns named `object_id`** — be specific: `scan_id`, `patient_id`

---

## Indexing Strategy

### Rule of Thumb

- **B-tree** for equality + range queries (default, covers most cases)
- **GIN** for JSONB / full-text search
- **BRIN** for large append-only tables with naturally clustered data (e.g., time-series)
- **Partial indexes** for sparsely-queried subsets (e.g., pending migrations)
- **Covering indexes** (`INCLUDE`) for hot query paths

### When to Index

| Scenario | Index Type |
|----------|-----------|
| `WHERE bah = ?` | B-tree on `bah` |
| `WHERE folder = ? AND bah = ?` | Composite B-tree on `(folder, bah)` |
| `WHERE access_time > ?` | B-tree on `access_time` |
| `WHERE upload_flag != 0 AND oss_url IS NULL` | Partial index on `(id)` with `WHERE` clause |
| `ORDER BY created_at DESC LIMIT 20` | B-tree on `created_at DESC` |
| `WHERE filename ILIKE 'prefix%'` | B-tree on `filename` (ILIKE is sargable only for prefix match) |

### Anti-Patterns

❌ **Index every column** — each index slows writes
❌ **Index low-cardinality columns alone** — e.g., `status` with 3 values; compose with a higher-cardinality column
❌ **Over-indexing small tables** — tables < 1000 rows are often faster with full scans
❌ **Function on column in WHERE** — `WHERE LOWER(name) = 'x'` defeats index; use expression index or keep data normalized

### SQL Examples

```sql
-- Partial index for pending OSS migration queries
CREATE INDEX IF NOT EXISTS idx_mr_scan_pending_migration
  ON mr_scan(id)
  WHERE upload_flag != 0 AND (oss_url IS NULL OR oss_url = '');

-- Composite index for folder-based search
CREATE INDEX IF NOT EXISTS idx_mr_scan_folder_bah
  ON mr_scan(folder, BAH);

-- Covering index for listing queries
CREATE INDEX IF NOT EXISTS idx_mr_scan_bah_cover
  ON mr_scan(BAH) INCLUDE (filename, pages, btype);

-- BRIN index for time-ordered access logs (large, append-only)
CREATE INDEX IF NOT EXISTS idx_access_log_time_brin
  ON access_log USING BRIN(access_time);
```

---

## Query Optimization

### MyBatis Patterns

```java
// ✅ Good: pagination with LIMIT/OFFSET
@Select("SELECT * FROM mr_scan ORDER BY id LIMIT #{size} OFFSET #{offset}")

// ✅ Good: dynamic SQL with script tag
@Select("<script>"
    + "SELECT * FROM mr_scan "
    + "<where>"
    + "  <if test='bah != null'>BAH = #{bah}</if>"
    + "  <if test='folder != null'>AND folder = #{folder}</if>"
    + "</where>"
    + "ORDER BY id</script>")

// ❌ Bad: string concatenation (SQL injection risk)
@Select("SELECT * FROM mr_scan WHERE BAH = '" + bah + "'")
```

### N+1 Prevention

```java
// ❌ N+1: query in a loop
for (Scan scan : scanList) {
    Patient patient = patientMapper.findByBah(scan.getBah());
}

// ✅ Batch: single query with IN clause
List<String> bahList = scanList.stream().map(Scan::getBah).toList();
List<Patient> patients = patientMapper.findByBahList(bahList);
```

### Key EXPLAIN ANALYZE Signals

| Signal | Meaning |
|--------|---------|
| `Seq Scan on large_table` | Missing index or query not sargable |
| `Sort Method: external merge` | Sort spills to disk; increase `work_mem` or add index |
| `Nested Loop` with many rows | Missing index on inner table join column |
| `Parallel Seq Scan` | Good for analytics, bad for OLTP if unexpected |

---

## Migrations (Flyway)

### Naming

```
V1__init.sql                      — baseline
V2__add_scan_indexes.sql          — additive changes
V3__alter_scan_add_oss_url.sql    — ALTER TABLE
V4__migrate_legacy_data.sql       — data migration
```

### Rules

- One migration per logical change
- Never edit a committed migration — create a new one
- Use `IF NOT EXISTS` / `IF EXISTS` for idempotency
- Test migrations against a copy of production data before deploying

### Safe DDL Patterns

```sql
-- ✅ Safe: additive change
ALTER TABLE mr_scan ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT FALSE;

-- ✅ Safe: index creation (use CONCURRENTLY on PG to avoid table lock)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mr_scan_new
  ON mr_scan(BAH);

-- ⚠️ Risky: ALTER with default on large table (locks table, rewrites rows)
--   Workaround: add column as nullable, backfill, then set NOT NULL
ALTER TABLE mr_scan ADD COLUMN department TEXT;
UPDATE mr_scan SET department = 'unknown' WHERE department IS NULL;
ALTER TABLE mr_scan ALTER COLUMN department SET NOT NULL;
```

---

## Partitioning

Use when a table exceeds 10M rows or query performance degrades:

```sql
-- Range partition by date
CREATE TABLE mr_scan_partitioned (
    LIKE mr_scan INCLUDING ALL
) PARTITION BY RANGE (uploaddate);

CREATE TABLE mr_scan_2024 PARTITION OF mr_scan_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
CREATE TABLE mr_scan_2025 PARTITION OF mr_scan_partitioned
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
```

---

## Common Pitfalls

| Problem | Root Cause | Fix |
|---------|-----------|-----|
| Slow pagination at high offsets | `OFFSET 100000` scans all preceding rows | Use keyset pagination (`WHERE id > ? LIMIT ?`) |
| Lock contention on hot rows | Too many concurrent updates to same row | Batch updates, reduce transaction scope |
| Bloated indexes | High write volume without autovacuum | Tune `autovacuum` settings, consider `pg_repack` |
| Inconsistent data | No foreign keys in schema | Add FK constraints |

## Limitations

- This skill provides design guidance and SQL patterns, not automated schema modification.
- Index recommendations are advisory; always verify with `EXPLAIN ANALYZE` before production deployment.
- Does not replace load testing — a good schema still needs realistic traffic validation.
- SQLite compatibility notes apply only when the test suite uses SQLite; production PG can use PG-specific features freely.

## Related Skills

- `@karpathy-guidelines` — Simplicity principles apply to schema design too: don't over-index, don't over-abstract
- `@sql-sentinel` — Static analysis for SQL anti-patterns (SELECT *, missing WHERE, Cartesian joins)
