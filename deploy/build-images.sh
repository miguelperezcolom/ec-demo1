#!/usr/bin/env bash
# Builds and pushes the two images this repository owns: the shell and the gateway.
#
# Everything else runs from published images — the engine's orchestrator/forms/rules/worker from
# Docker Hub, Keycloak from Quay, Postgres and Redpanda from their own registries.
#
#   ./deploy/build-images.sh [TAG]        # default: the version in each pom
#
# linux/amd64 only, matching the nodeSelector every workload here carries: the Karpenter pool can
# provision arm64 and a single-arch image on an arm64 node is an unschedulable pod, not an error
# you find out about at build time.
set -euo pipefail
cd "$(dirname "$0")/.."

REGISTRY="${REGISTRY:-miguelperezcolom}"
TAG="${1:-0.2.0}"

for app in shell gateway; do
  echo "── building $app ──"
  # Built outside the image on purpose: the Dockerfiles copy target/*.jar, so Maven's cache works
  # and a code change does not re-resolve the whole dependency tree.
  ( cd "$app" && mvn -B -ntp -DskipTests package )
  docker buildx build --platform linux/amd64 \
    -t "$REGISTRY/ec-demo1-$app:$TAG" --push "$app"
done

echo
echo "Pushed $REGISTRY/ec-demo1-shell:$TAG and $REGISTRY/ec-demo1-gateway:$TAG"
echo "Point deploy/manifests/30-shell.yaml and 35-gateway.yaml at the tag if it is not $TAG."
