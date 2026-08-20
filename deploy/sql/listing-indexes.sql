-- The indexes the engine's own migrations create, applied here because nothing else applies them.
--
-- deploy/values/eventconductor.yaml runs all three engines with flywayEnabled=false and
-- ddlAuto=update, so Hibernate builds the schema and no migration ever runs. Hibernate creates the
-- indexes declared in JPA annotations and none of the ones that live only in SQL. The engine says
-- so itself, at every startup, in as many words:
--
--   workflow.persistence=jpa but the engine is NOT managing its schema: its migrations were not
--   applied, so the database may be missing the engine's indexes (Hibernate's ddl-auto=update
--   creates none)
--
-- These three are workflow-engine's V22 and V23, copied verbatim but for CONCURRENTLY. Without
-- them the process and step listings are a sequential scan plus a top-N sort of the whole table —
-- 6.9-7.7 s per page turn measured on 37 651 processes, against 0.13 ms with them.
--
-- The names match the migrations exactly and every statement is IF NOT EXISTS, so this is a no-op
-- against a database that already has them, and if flywayEnabled is ever turned on, V22 and V23
-- find their own indexes in place and skip.
--
-- CONCURRENTLY because this runs against a live deployment: step_execution_entity is the engine's
-- largest table — 453 MB here — and a plain CREATE INDEX holds an ACCESS EXCLUSIVE lock for as
-- long as it takes to build, blocking every write to it. It also means these cannot be wrapped in
-- a transaction, so do not add BEGIN or run this with psql --single-transaction.

-- The ordering has to match the query's "order by created desc nulls last" exactly, or the planner
-- cannot read the index in order and sorts anyway: created is nullable, and a plain DESC index in
-- Postgres is NULLS FIRST.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_process_created
    ON process_entity (created DESC NULLS LAST);

-- "Only errors", and the status counts on the home dashboard.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_process_status_created
    ON process_entity (status, created DESC NULLS LAST);

-- The step listing, on that largest table. "Only errors" there is served by idx_step_exec_status,
-- which is declared in JPA annotations and so does get created.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_step_exec_started_at
    ON step_execution_entity (started_at DESC NULLS LAST);
