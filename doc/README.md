# ec-demo1 — documentation site

The project's documentation, in Spanish, as an Astro + Starlight site — the same stack and layout as
EventConductor's and Mateu's (`doc/` in each).

```sh
npm install
npm run dev       # http://localhost:4321
npm run build     # → dist/
```

Pages are Markdown under `src/content/docs/`; the sidebar is in `astro.config.mjs`. A ```` ```mermaid ````
block is drawn in the browser (the rehype plugin in `astro.config.mjs` and the script in the head).

On ec1 it is served at https://doc.ec1.mateu.io, behind a username and password: `Dockerfile` builds it
with Node and serves `dist/` with an unprivileged nginx (`nginx.conf`); `deploy/build-images.sh` pushes
`miguelperezcolom/ec-demo1-docs` and `deploy/manifests/81-docs.yaml` deploys it.
