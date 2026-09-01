#!/usr/bin/env bash
# Builds and pushes the ten images this repository owns: the four shells, the gateway, the four
# demo services and the IA control plane.
#
# Four shells and not two: each console is served by a Vaadin one and a Redwood one, differing
# only in which Mateu frontend artifact their pom depends on. They render the same backends
# through different renderers, which is the point — see deploy/manifests/31-shell-redwood.yaml.
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
TAG="${1:-0.8.0}"

APPS="shell shell-redwood gateway booking content users ia-agent ia-control-plane control-shell control-shell-redwood"

# grpc-interface first, and installed rather than packaged: it is not an application and gets no
# image, but `users` compiles against the protobuf stubs generated from its .proto, so it has to
# be in the local repository before that module is built. It is also the one module that reaches
# the network for something other than dependencies — the protobuf plugin downloads protoc.
echo "── installing grpc-interface (the stubs users compiles against) ──"
( cd grpc-interface && mvn -B -ntp -DskipTests install )

for app in $APPS; do
  echo "── building $app ──"
  # Built outside the image on purpose: the Dockerfiles copy target/*.jar, so Maven's cache works
  # and a code change does not re-resolve the whole dependency tree.
  ( cd "$app" && mvn -B -ntp -DskipTests package )
  docker buildx build --platform linux/amd64 \
    -t "$REGISTRY/ec-demo1-$app:$TAG" --push "$app"
done

echo
for app in $APPS; do echo "Pushed $REGISTRY/ec-demo1-$app:$TAG"; done
echo "Point the manifests in deploy/manifests/ at the tag if it is not $TAG."
