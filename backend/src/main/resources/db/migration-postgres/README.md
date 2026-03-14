Postgres-specific Flyway migrations live here.

Current status:
- Production/Render Postgres now defaults to `FLYWAY_ENABLED=true`
- Fresh PostgreSQL databases apply `V1__baseline_schema.sql` + `V2__align_constraints_and_indexes.sql`
- Existing production databases use `baseline-on-migrate=true` and `baseline-version=2` so Flyway records history without replaying the baseline DDL
