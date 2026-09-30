#!/usr/bin/env bash
# Checks and unit-tests the alert rules with promtool, the way Prometheus will read them.
#
#   deploy/observability/test-rules.sh
#
# promtool reads plain rule files, not the operator's PrometheusRule objects, so the groups of every
# PrometheusRule in prometheus-rules.yaml are extracted first into a temporary rules file; the tests
# (prometheus-rules.test.yaml) name that file. promtool comes from the prom/prometheus image, so
# nothing needs installing but Docker (or a local promtool on the PATH, used when there is one).
set -euo pipefail
cd "$(dirname "$0")"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

python3 - "$tmp/rules.yaml" <<'PY'
import sys, yaml
groups = []
for doc in yaml.safe_load_all(open("prometheus-rules.yaml")):
    if doc and doc.get("kind") == "PrometheusRule":
        groups += doc["spec"]["groups"]
yaml.safe_dump({"groups": groups}, open(sys.argv[1], "w"), sort_keys=False, allow_unicode=True)
PY
cp prometheus-rules.test.yaml "$tmp/"
# The image runs promtool as nobody: a mktemp directory is its owner's alone.
chmod 755 "$tmp" && chmod 644 "$tmp"/*

if command -v promtool >/dev/null 2>&1; then
  (cd "$tmp" && promtool check rules rules.yaml && promtool test rules prometheus-rules.test.yaml)
else
  docker run --rm -v "$tmp:/rules:Z" -w /rules --entrypoint /bin/sh prom/prometheus:latest \
    -c "promtool check rules rules.yaml && promtool test rules prometheus-rules.test.yaml"
fi
