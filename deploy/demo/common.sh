# Shared by snapshot.sh and reset.sh: where the baseline lives, what it covers, how to reach the database.
NS=ec-demo1
BASELINE=${EC_DEMO_BASELINE:-$HOME/.local/share/ec-demo1/demo-baseline}

# The services' databases, whole: their state is the demo's state. (content, users and keycloak are not.)
DATABASES="audit booking communication crs_integration customer_mdm front_office integrations mapping partners"
# The engine's database only by its state tables: processes, their steps, the forms' tasks, locks and
# overrides. Its history — logs, the outbox, the task dedup store — is gigabytes and not the demo's state.
ENGINE_DB=workflow
ENGINE_TABLES="process_entity step_execution_entity form_execution_entity process_lock process_lock_waiter resource_entity process_index task_override"

# What stops while the state is put back, and starts again after.
SERVICES="audit-service booking communication-service crs-integration-service customer-mdm-service front-office integrations-service mapping-service partners pms-integration-service ec-eventconductor-orchestrator ec-eventconductor-forms"

pg_pod() { kubectl -n $NS get pod -o name | grep eventconductor-postgres | head -1; }
# psql against one database, reading SQL from stdin
psql_in() { kubectl -n $NS exec -i "$(pg_pod)" -- sh -c "psql -U \"\$POSTGRES_USER\" -v ON_ERROR_STOP=1 -q -d $1"; }
# one value, or one column
psql_value() { kubectl -n $NS exec "$(pg_pod)" -- sh -c "psql -U \"\$POSTGRES_USER\" -Atc \"$2\" -d $1"; }
