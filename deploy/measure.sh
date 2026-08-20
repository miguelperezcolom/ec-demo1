#!/usr/bin/env bash
# Reports the two numbers a load run can produce, and keeps them apart.
#
#   ./deploy/measure.sh                 # the most recent load-* run
#   ./deploy/measure.sh load-083027     # a specific one
#
# ── Why two numbers ──────────────────────────────────────────────────────────
# **Cost per transition** is the gap between one step finishing and the next
# starting. Nothing but the engine happens in that window — writing the
# transition, publishing it to the outbox, relaying it, routing it, dispatching
# the next task — so it does not move when the workers get faster or slower. It
# is the answer to "does the orchestrator resolve steps quickly".
#
# **Throughput** is steps and processes per second across the whole run. It is
# the answer to "how much can this deployment absorb", and it includes the
# worker's simulated work and every second a step spent queued.
#
# They cannot be measured by the same run. Under saturation the transition gap
# is dominated by queueing and stops describing the engine at all: measured here,
# 37ms unsaturated against 4415ms at 20 arrivals/s — a factor of 120, none of it
# the engine getting slower. So:
#
#   ./deploy/loadgen.sh 20 1     -> then measure the transition cost
#   ./deploy/loadgen.sh 5000 50  -> then measure throughput
#
# Reading a low arrival rate as "the engine is fast" or a saturated one as "the
# engine is slow" are the same mistake in opposite directions.
set -euo pipefail
cd "$(dirname "$0")/.."

NS=ec-demo1
PGPOD=$(kubectl get pod -n "$NS" -l app.kubernetes.io/component=postgres -o name | head -1)
[ -n "$PGPOD" ] || { echo "no postgres pod in $NS" >&2; exit 1; }

psql() { kubectl exec -n "$NS" "$PGPOD" -- psql -U workflow -d workflow "$@"; }

PREFIX="${1:-}"
if [ -z "$PREFIX" ]; then
  PREFIX=$(psql -tAc "select split_part(business_key,'-',1)||'-'||split_part(business_key,'-',2)
                      from process_entity where business_key like 'load-%'
                      order by created desc limit 1" | tr -d '[:space:]')
  [ -n "$PREFIX" ] || { echo "no load-* run found" >&2; exit 1; }
fi

echo "── run: $PREFIX"
echo
echo "── Cost per transition (engine only — meaningful when unsaturated)"
psql -c "
with s as (
  select se.process_id, se.started_at,
         lag(se.finished_at) over (partition by se.process_id order by se.started_at) prev_fin
  from step_execution_entity se join process_entity p on se.process_id = p.id
  where p.business_key like '${PREFIX}%' and se.started_at is not null
)
select count(*)                                                                              as transitions,
       round(avg(extract(epoch from (started_at - prev_fin))) * 1000)                         as mean_ms,
       round((percentile_cont(0.50) within group (order by extract(epoch from (started_at - prev_fin)))) * 1000) as p50_ms,
       round((percentile_cont(0.95) within group (order by extract(epoch from (started_at - prev_fin)))) * 1000) as p95_ms,
       round((percentile_cont(0.99) within group (order by extract(epoch from (started_at - prev_fin)))) * 1000) as p99_ms
from s
where prev_fin is not null and started_at >= prev_fin;"

echo "── Throughput (whole run, includes the worker's simulated work and all queueing)"
psql -c "
select count(*)                                                              as processes,
       count(*) filter (where status = 'COMPLETED')                          as completed,
       count(*) filter (where status = 'ERROR')                              as errored,
       round(extract(epoch from (max(finished) - min(created))))             as seconds,
       round(count(*) / nullif(extract(epoch from (max(finished) - min(created))), 0), 2) as processes_s
from process_entity where business_key like '${PREFIX}%';"

psql -c "
select count(*)                                                                              as step_executions,
       round(count(*) / nullif(extract(epoch from (max(p.finished) - min(p.created))), 0), 1) as steps_s,
       count(*) filter (where se.status = 'TIMEOUT')                                          as timed_out,
       sum(se.attempt_count)                                                                  as retries
from step_execution_entity se join process_entity p on se.process_id = p.id
where p.business_key like '${PREFIX}%';"
