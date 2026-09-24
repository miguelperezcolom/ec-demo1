# Vendored copy of `charts/eventconductor`

Copied from [`miguelperezcolom/eventconductor`](https://github.com/miguelperezcolom/eventconductor)
at chart version **0.1.7** / appVersion 2.1.1, so this repository can deploy itself without a
checkout of the engine repo beside it.

> **Never upgrade the `ec` release with the upstream chart.** This copy has diverged from
> `charts/eventconductor` while both still say version 0.1.7 — above all, upstream knows nothing
> about `postgres.localDisk`. A `helm upgrade ec <engine repo>/charts/eventconductor --reuse-values`
> renders PostgreSQL on a new PVC, replaces its pod, and the `emptyDir` holding **every** database
> on it — the engine's, Keycloak's and the demo services' — is gone (it happened on 2026-09-24,
> Helm revision 52). Upgrade only with this chart and `deploy/values/eventconductor.yaml`, changing
> the `imageTag`s, and check first that the PostgreSQL Deployment does not change:
>
> ```bash
> helm -n ec-demo1 get manifest ec > /tmp/current.yaml
> helm template ec deploy/chart/eventconductor -n ec-demo1 -f deploy/values/eventconductor.yaml > /tmp/next.yaml
> diff /tmp/current.yaml /tmp/next.yaml     # expect only image lines
> ```
>
> If it does happen: re-run `keycloak-db-init` and `demo-db-init` (delete + apply, as `deploy.sh`
> does), restart the services that use this PostgreSQL, and restore the baseline with
> `deploy/demo/reset.sh`.

## The changes

Three. The first two fill gaps that make a real deployment impossible rather than inconvenient;
the third is a default of Kubernetes' that is wrong for a JVM.

### 1. `extraEnv` on `forms` and `rules`

They gained the map `orchestrator` already had:

```yaml
{{- range $k, $v := .Values.forms.extraEnv }}
- name: {{ $k }}
  value: {{ $v | quote }}
{{- end }}
```

Without it there is no way to set `FORMS_GITIMPORT_REPOSITORIES_0_*`, and the forms engine
cannot read its definitions from Git — which is the whole point of this demo. It is an
omission rather than a decision (the orchestrator block it copies is identical), so it is
worth upstreaming; when it lands, re-vendor and delete this file.

### 2. `extraEnvFromSecret` on all three

```yaml
{{- if .Values.orchestrator.extraEnvFromSecret }}
envFrom:
  - secretRef:
      name: {{ .Values.orchestrator.extraEnvFromSecret }}
{{- end }}
```

`extraEnv` renders its values as literals into the manifest, so it cannot carry anything that
must not be in version control. The git webhook secret is exactly that: leave it blank and the
engine skips signature verification entirely, so anyone who finds the URL can trigger re-imports
at will. A token for a private definitions repository has the same problem. One `envFrom` is
enough for both.

Everything else is byte-identical to upstream. `diff -r` against
`eventconductor/charts/eventconductor` should show only the blocks above and this file.

### 3. `timeoutSeconds` on every probe

The chart sets `initialDelaySeconds` and `periodSeconds` and leaves `timeoutSeconds` alone, so
Kubernetes applies its default of **one second**. One second is not a health check on a JVM; it is a
check that the JVM is idle. A single expensive request is enough to push `/actuator/health` past it,
and for a *liveness* probe the consequence is a SIGKILL of a pod that was working rather than stuck
— which is what `/workflow/analytics` did here, three missed probes and `exitCode: 137`, twice.

Readiness now allows 5s (take it out of rotation while it is busy, which is correct), and liveness
allows 5s with `failureThreshold: 6` — two full minutes of silence before restarting anything.
Liveness has to mean wedged, never busy.

Worth upstreaming as configurable values rather than as these numbers; every deployment's idea of
"wedged" is different.
