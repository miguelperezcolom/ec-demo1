# Vendored copy of `charts/eventconductor`

Copied from [`miguelperezcolom/eventconductor`](https://github.com/miguelperezcolom/eventconductor)
at chart version **0.1.7** / appVersion 2.1.1, so this repository can deploy itself without a
checkout of the engine repo beside it.

## The changes

Two, both filling gaps that make a real deployment impossible rather than inconvenient.

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
