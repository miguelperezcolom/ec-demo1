# Vendored copy of `charts/eventconductor`

Copied from [`miguelperezcolom/eventconductor`](https://github.com/miguelperezcolom/eventconductor)
at chart version **0.1.7** / appVersion 2.1.1, so this repository can deploy itself without a
checkout of the engine repo beside it.

## The one change

`forms` and `rules` gained the `extraEnv` map that `orchestrator` already had:

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

Everything else is byte-identical to upstream. `diff -r` against
`eventconductor/charts/eventconductor` should show only the blocks above and this file.
