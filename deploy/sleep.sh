#!/usr/bin/env bash
# Puts ec1 to sleep: no Hetzner node left, so no server cost. The Cloudfleet cluster, its volumes
# (the databases), the load balancer's address and the DNS stay; deploy/wake.sh brings it back as it was.
#
#   deploy/sleep.sh
#
# 1. Every Deployment and StatefulSet in the namespaces below goes to 0, its replica count kept in the
#    annotation ec-demo1/sleep-replicas for wake.sh. Deployments first: the Prometheus operator is one,
#    and with it running it would put its StatefulSets back up.
# 2. The fleet's CPU limit goes to 0 (Fleet API — a NodePool is not editable with kubectl on
#    Cloudfleet), so Karpenter cannot start a node for what is left (Cloudfleet's own kube-system pods,
#    which then wait Pending), and the nodes are deleted through their NodeClaims.
#
# Run it against ec1 only: it refuses any other kube context.
set -euo pipefail
CLUSTER_ID=1ad4d183-bf34-48a2-af42-31856e031729
FLEET=mateu-fleet
NAMESPACES="ec-demo1 observability ingress-nginx cert-manager"

ctx=$(kubectl config current-context)
[ "$ctx" = "$CLUSTER_ID/default" ] || { echo "kube context is $ctx, not ec1: refusing"; exit 1; }

echo "══ Workloads to 0 ══"
for kind in deployment statefulset; do
  for ns in $NAMESPACES; do
    kubectl -n "$ns" get "$kind" -o jsonpath='{range .items[*]}{.metadata.name} {.spec.replicas}{"\n"}{end}' |
    while read -r name replicas; do
      [ -n "$name" ] || continue
      if [ "${replicas:-0}" != "0" ]; then
        kubectl -n "$ns" annotate "$kind" "$name" ec-demo1/sleep-replicas="$replicas" --overwrite >/dev/null
        kubectl -n "$ns" scale "$kind" "$name" --replicas=0 >/dev/null
        echo "  $ns/$kind/$name: $replicas → 0"
      fi
    done
  done
done

echo "══ Fleet $FLEET: CPU limit 0 ══"
cloudfleet clusters fleets describe "$CLUSTER_ID" "$FLEET" -o json \
  | jq '{constraints, limits: {cpu: 0}, hetzner: {enabled: .hetzner.enabled}, aws: {enabled: .aws.enabled}, gcp: {enabled: .gcp.enabled}, scalingProfile}' \
  | cloudfleet clusters fleets update "$CLUSTER_ID" "$FLEET" -f - >/dev/null
cloudfleet clusters fleets describe "$CLUSTER_ID" "$FLEET" -o json | jq -c '{limits}'

echo "══ Nodes ══"
kubectl delete nodeclaims --all --wait=false
# The last node never drains by itself: hcloud-csi-controller's PodDisruptionBudget (minAvailable 1)
# refuses its eviction when there is no other node to go to. Deleting a pod is not an eviction, and by
# now no volume is attached (every pod that had one is gone). Cloudfleet's Deployments recreate them,
# Pending until wake.sh lets a node start.
sleep 30
kubectl -n kube-system get pods -o json \
  | jq -r '.items[] | select(.metadata.ownerReferences[0].kind != "DaemonSet") | .metadata.name' \
  | xargs -r kubectl -n kube-system delete pod --wait=false
for i in $(seq 1 60); do
  n=$(kubectl get nodes --no-headers 2>/dev/null | wc -l)
  [ "$n" = "0" ] && break
  sleep 10
done
kubectl get nodes --no-headers 2>/dev/null | wc -l | xargs echo "  nodes left:"
echo "Asleep. deploy/wake.sh brings it back."
