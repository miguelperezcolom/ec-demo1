#!/usr/bin/env bash
# Wakes ec1 up after deploy/sleep.sh: the fleet's CPU limit back, then every workload to the replica
# count sleep.sh kept in its ec-demo1/sleep-replicas annotation. Karpenter starts the nodes for them
# (a few minutes); the databases come back from their volumes as they were.
#
#   deploy/wake.sh            # FLEET_CPU=40 by default
#
# Then deploy/demo/demo-prep.sh for the health checks.
set -euo pipefail
CLUSTER_ID=1ad4d183-bf34-48a2-af42-31856e031729
FLEET=mateu-fleet
FLEET_CPU="${FLEET_CPU:-40}"
NAMESPACES="cert-manager ingress-nginx ec-demo1 observability"

ctx=$(kubectl config current-context)
[ "$ctx" = "$CLUSTER_ID/default" ] || { echo "kube context is $ctx, not ec1: refusing"; exit 1; }

echo "══ Fleet $FLEET: CPU limit $FLEET_CPU ══"
cloudfleet clusters fleets describe "$CLUSTER_ID" "$FLEET" -o json \
  | jq --argjson cpu "$FLEET_CPU" '{constraints, limits: {cpu: $cpu}, hetzner: {enabled: .hetzner.enabled}, aws: {enabled: .aws.enabled}, gcp: {enabled: .gcp.enabled}, scalingProfile}' \
  | cloudfleet clusters fleets update "$CLUSTER_ID" "$FLEET" -f - >/dev/null
cloudfleet clusters fleets describe "$CLUSTER_ID" "$FLEET" -o json | jq -c '{limits}'

echo "══ Workloads back ══"
# StatefulSets first (the databases, Redpanda), then the Deployments that wait for them.
for kind in statefulset deployment; do
  for ns in $NAMESPACES; do
    kubectl -n "$ns" get "$kind" -o json \
      | jq -r '.items[] | select(.metadata.annotations["ec-demo1/sleep-replicas"] != null)
               | "\(.metadata.name) \(.metadata.annotations["ec-demo1/sleep-replicas"])"' |
    while read -r name replicas; do
      kubectl -n "$ns" scale "$kind" "$name" --replicas="$replicas" >/dev/null
      kubectl -n "$ns" annotate "$kind" "$name" ec-demo1/sleep-replicas- >/dev/null
      echo "  $ns/$kind/$name → $replicas"
    done
  done
done

echo "══ Waiting for the nodes and the workloads ══"
for ns in $NAMESPACES; do
  for d in $(kubectl -n "$ns" get deploy -o jsonpath='{.items[*].metadata.name}'); do
    kubectl -n "$ns" rollout status "deploy/$d" --timeout=900s | tail -1
  done
done
kubectl get nodes -L node.kubernetes.io/instance-type,topology.kubernetes.io/region
echo "Awake. Check it with deploy/demo/demo-prep.sh."
