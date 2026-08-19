#!/usr/bin/env bash
# Puts the engine under load, so you can watch it work rather than reason about it.
#
#   ./deploy/loadgen.sh [COUNT] [RATE] [WORKFLOW]
#   ./deploy/loadgen.sh 2000 20 notify-parallel      # 2000 processes, 20 a second
#   ./deploy/loadgen.sh 500 10 payment-review        # a human task the worker answers
#
# It runs as a Job in the cluster, producing ProcessCreationRequested events onto the same
# `upstream` topic everything else uses — so what it drives is the deployment you already have,
# not a rig of its own. No image to build: the Redpanda image is already on the node and ships
# `rpk`.
#
# Paced on purpose. Dumping N events at once measures how fast Kafka accepts a batch, which is not
# a question anyone has; holding an arrival rate shows whether the engine keeps up with it, which
# is. Watch `eventconductor_outbox_pending` — if it climbs and stays up, the rate is above what
# this deployment can drain.
set -euo pipefail
cd "$(dirname "$0")/.."

COUNT="${1:-1000}"
RATE="${2:-20}"
WORKFLOW="${3:-notify-parallel}"
NS=ec-demo1

# What every task should do. The worker plays this back — see the TEST_CONFIG note in the README.
# Short and successful by default: the point here is volume, not any one process's outcome.
#
# Assigned in its own statement, single-quoted, rather than as a ${VAR:-default}: the default is
# JSON, JSON is all braces, and closing one inside a parameter expansion ends the expansion. The
# escaping that works around that leaks a backslash into the value — which is how the first version
# of this script produced 1500 messages carrying an invalid \} escape.
TEST_CONFIG="${TEST_CONFIG-}"
if [ -z "$TEST_CONFIG" ]; then
  TEST_CONFIG='{"default":{"durationMs":200,"outcome":"COMPLETED"}}'
fi

# Checked here, because nothing downstream will tell you. A message the engine cannot parse is
# dropped in silence: no error logged, no dead-letter topic, and the offset committed anyway — so
# the producer reports success, consumer lag reads zero, and no process is ever created.
python3 -c 'import json,sys; json.loads(sys.argv[1])' "$TEST_CONFIG" 2>/dev/null || {
  echo "TEST_CONFIG is not valid JSON:" >&2
  echo "  $TEST_CONFIG" >&2
  exit 1
}

# Unique per run, so a second run does not collide with the business keys of the first.
RUN="load-$(date +%H%M%S)"
JOB="loadgen-$(date +%H%M%S)"

echo "── $COUNT processes of '$WORKFLOW' at $RATE/s (~$((COUNT / RATE))s), businessKey prefix $RUN"

kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: $JOB
  namespace: $NS
  labels: { app: loadgen }
spec:
  backoffLimit: 0
  ttlSecondsAfterFinished: 3600
  template:
    metadata:
      labels: { app: loadgen }
    spec:
      restartPolicy: Never
      nodeSelector: { kubernetes.io/arch: amd64 }
      containers:
        - name: loadgen
          image: docker.redpanda.com/redpandadata/redpanda:v24.1.7
          env:
            - { name: COUNT,       value: "$COUNT" }
            - { name: RATE,        value: "$RATE" }
            - { name: WORKFLOW,    value: "$WORKFLOW" }
            - { name: RUN,         value: "$RUN" }
            - { name: TEST_CONFIG, value: '$TEST_CONFIG' }
          command:
            - /bin/bash
            - -c
            - |
              set -eu
              # The scenario travels as a process variable, and a variable's value is a string —
              # so the JSON has to be escaped into it rather than nested.
              ESCAPED=\$(printf '%s' "\$TEST_CONFIG" | sed 's/"/\\\\"/g')
              i=0
              start=\$(date +%s)
              while [ \$i -lt \$COUNT ]; do
                n=0
                # Built into a file rather than piped straight from the loop: a pipeline runs its
                # left side in a subshell, so \$i would be incremented in a copy and lost, and the
                # outer loop would never advance. It did exactly that the first time.
                : > /tmp/chunk
                while [ \$n -lt \$RATE ] && [ \$i -lt \$COUNT ]; do
                  i=\$((i+1)); n=\$((n+1))
                  printf '{"type":"process-creation-requested","workflowDefinitionId":"%s","businessKey":"%s-%d","variables":[{"name":"TEST_CONFIG","value":"%s"}],"parentStepExecutionId":null}\n' \\
                    "\$WORKFLOW" "\$RUN" "\$i" "\$ESCAPED" >> /tmp/chunk
                done
                rpk topic produce upstream --brokers redpanda:19092 --compression none -Z < /tmp/chunk >/dev/null
                # Unkeyed on purpose: a creation event is one message, so nothing needs ordering
                # against it, and round-robin spreads intake over all six upstream partitions.
                [ \$((i % (RATE * 10))) -eq 0 ] && echo "produced \$i/\$COUNT (\$(( \$(date +%s) - start ))s)" || true
                sleep 1
              done
              echo "done: \$i processes of \$WORKFLOW in \$(( \$(date +%s) - start ))s"
          resources:
            requests: { memory: "128Mi", cpu: "100m" }
            limits:   { memory: "512Mi", cpu: "500m" }
EOF

echo
echo "Follow it:   kubectl logs -f job/$JOB -n $NS"
cat <<'TIPS'

While it runs, the three things worth watching:

  Console   https://ec1.mateu.io           Workflow -> Processes, and the dashboard counters
  Kafka     https://kafka.ec1.mateu.io     Groups tab: consumer lag is where a backlog shows first
  Grafana   https://grafana.ec1.mateu.io   Explore -> Prometheus:

    rate(eventconductor_process_started_total[1m])     arrivals the engine actually saw
    rate(eventconductor_process_completed_total[1m])   throughput; should meet arrivals in steady state
    eventconductor_process_running                     in flight — climbing means it is falling behind
    eventconductor_outbox_pending                      the relay's backlog, the earliest warning
    eventconductor_steps_stalled                       steps nobody will ever answer
    rate(eventconductor_step_retries_total[1m])        retries, i.e. work being done twice
TIPS
