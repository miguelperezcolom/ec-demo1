#!/usr/bin/env bash
# Deletes the local cluster (deploy/local/up.sh). The images and volumes under $LOCAL_HOME stay:
# delete that directory too to start from nothing.
set -euo pipefail
LOCAL_HOME="${LOCAL_HOME:-$HOME/.local/share/ec-demo1-local}"
kind delete cluster --name ec-demo1-local --kubeconfig "$LOCAL_HOME/kubeconfig"
