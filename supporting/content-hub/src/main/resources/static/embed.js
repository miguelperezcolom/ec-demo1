// content-hub's embeds: Mateu's sanitiser drops <iframe>, so content pages carry placeholders —
// <div data-ec-embed="URL" data-ec-embed-kind="video|pdf"> holding a link (and a thumbnail) — and this
// turns each into the player, after the page is drawn. Only for the hosts below and for content-hub's
// own signed media; anything else keeps its link. Loaded by the shells (@Script), like push.js; walks
// the shadow roots, where Mateu draws. It also defines <ec-ui-source>: the external UIs of the registry
// that are web components or iframes (see below).
(() => {
  if (window.__ecEmbed) return;
  window.__ecEmbed = true;
  const HOSTS = ['www.youtube-nocookie.com', 'player.vimeo.com', 'drive.google.com'];
  const OWN = /^\/_(intranet|content-hub)\/media\//;

  // kind "ui": another UI of the platform a menu entry hosts (MenuEntryPage) — only behind our own gateway
  const allowed = (src, kind) => {
    try {
      const u = new URL(src, location.href);
      if (u.origin === location.origin) return kind === 'ui' ? /^\/_[a-z0-9-]+(\/|$)/.test(u.pathname) : OWN.test(u.pathname);
      if (kind === 'ui') return false;
      return u.protocol === 'https:' && HOSTS.includes(u.hostname);
    } catch (e) {
      return false;
    }
  };

  const upgrade = (el) => {
    if (el.dataset.ecEmbedDone) return;
    el.dataset.ecEmbedDone = '1';
    const src = el.getAttribute('data-ec-embed');
    if (!src || !allowed(src, el.dataset.ecEmbedKind)) return;
    const frame = document.createElement('iframe');
    frame.src = src;
    frame.loading = 'lazy';
    frame.referrerPolicy = 'strict-origin-when-cross-origin';
    frame.allow = 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen';
    frame.allowFullscreen = true;
    frame.style.cssText = 'width:100%;height:100%;border:0;border-radius:8px';
    const keep = el.querySelector('a');
    el.textContent = '';
    el.appendChild(frame);
    if (keep && el.dataset.ecEmbedKind === 'pdf') {
      keep.style.cssText = 'display:inline-block;margin-top:.25rem;font-size:.85rem';
      el.after(keep);
    }
  };

  const eachDeep = (root, selector, fn) => {
    root.querySelectorAll(selector).forEach(fn);
    root.querySelectorAll('*').forEach((node) => {
      if (node.shadowRoot) eachDeep(node.shadowRoot, selector, fn);
    });
  };

  // The backoffice's visual editor (<ec-visual-editor>, VisualEditorPage): its code is for editors, so it
  // is not public at the gateway like this script — it is fetched with the user's token (the one the
  // bootstrap page keeps in localStorage) the first time the tag shows up, and the element upgrades.
  const EDITOR = 'ec-visual-editor';
  let editorLoading = false;
  const loadEditor = async (el) => {
    if (editorLoading || !window.customElements || customElements.get(EDITOR)) return;
    editorLoading = true;
    const api = el.getAttribute('api') || '/_intranet/api/editor';
    try {
      let token = null;
      for (let i = 0; i < 40 && !(token = localStorage.getItem('__mateu_auth_token')); i++) {
        await new Promise((r) => setTimeout(r, 250));
      }
      const answer = await fetch(api + '/visual-editor.js', {
        headers: token ? { Authorization: 'Bearer ' + token } : {},
        credentials: 'same-origin',
      });
      if (!answer.ok) throw new Error('HTTP ' + answer.status);
      const code = await answer.text();
      const script = document.createElement('script');
      script.src = URL.createObjectURL(new Blob([code], { type: 'text/javascript' }));
      document.head.appendChild(script);
    } catch (e) {
      editorLoading = false;
      el.textContent = 'No se ha podido cargar el editor visual (' + e.message + '). Recarga la página.';
    }
  };

  // External UIs from content-hub's registry (Configuración → UIs externas) that are not Mateu UIs — those
  // the renderer federates itself (MicroFrontend). The server draws <ec-ui-source kind="web-component"
  // src="/_svc/x.js" tag="x-y" attrs='{"a":"b"}'> or <ec-ui-source kind="iframe" src="https://…">, only
  // for a source the reader may use, and only from what the registry validated; this checks the shape again:
  //  - web-component: the script is a path behind the gateway, fetched with the user's token (the gateway
  //    asks for one on every service prefix) the first time; then the element, with its attributes (never
  //    an event handler);
  //  - iframe: an https URL, in a sandbox, without the token (the other site signs in on its own).
  const UI_SCRIPT = /^\/_[a-z0-9][a-z0-9-]*(\/[A-Za-z0-9_~-][A-Za-z0-9._~-]*)+\.m?js$/;
  const UI_TAG = /^[a-z][a-z0-9._]*-[a-z0-9._-]*$/;
  const UI_ATTR = /^[a-z][a-z0-9-]{0,40}$/;
  const UI_SKIP = new Set(['style', 'is', 'slot', 'class', 'id']);
  const uiScripts = {};

  const userToken = async () => {
    let token = null;
    for (let i = 0; i < 40 && !(token = localStorage.getItem('__mateu_auth_token')); i++) {
      await new Promise((r) => setTimeout(r, 250));
    }
    return token;
  };

  const loadUiScript = (src) => uiScripts[src] || (uiScripts[src] = (async () => {
    const token = await userToken();
    const answer = await fetch(src, { headers: token ? { Authorization: 'Bearer ' + token } : {}, credentials: 'same-origin' });
    if (!answer.ok) throw new Error('HTTP ' + answer.status);
    const code = await answer.text();
    await new Promise((ok, ko) => {
      const script = document.createElement('script');
      if (src.endsWith('.mjs')) script.type = 'module';
      script.onload = ok;
      script.onerror = () => ko(new Error('el script no carga'));
      script.src = URL.createObjectURL(new Blob([code], { type: 'text/javascript' }));
      document.head.appendChild(script);
    });
  })().catch((e) => { delete uiScripts[src]; throw e; }));

  class EcUiSource extends HTMLElement {
    static get observedAttributes() { return ['kind', 'src', 'tag', 'attrs']; }

    connectedCallback() { this.schedule(); }

    attributeChangedCallback() { if (this.isConnected) this.schedule(); }

    schedule() {
      clearTimeout(this.timer);
      this.timer = setTimeout(() => this.draw(), 0);
    }

    fail(message) {
      this.textContent = 'No se ha podido cargar «' + (this.getAttribute('label') || 'la UI externa') + '» (' + message + ').';
      this.style.opacity = '.7';
    }

    async draw() {
      const kind = this.getAttribute('kind');
      const src = this.getAttribute('src') || '';
      const key = kind + '|' + src + '|' + this.getAttribute('tag') + '|' + this.getAttribute('attrs');
      if (!kind || this.drawn === key) return;
      this.drawn = key;
      this.textContent = '';
      if (kind === 'iframe') {
        let url;
        try { url = new URL(src); } catch (e) { return this.fail('URL no válida'); }
        if (url.protocol !== 'https:') return this.fail('solo https');
        const frame = document.createElement('iframe');
        frame.src = url.href;
        frame.title = this.getAttribute('label') || '';
        frame.loading = 'lazy';
        frame.referrerPolicy = 'strict-origin-when-cross-origin';
        frame.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox');
        frame.style.cssText = 'width:100%;height:' + (/^\d{2,4}$/.test(this.getAttribute('height') || '') ? this.getAttribute('height') : '600') + 'px;border:0;border-radius:8px';
        this.appendChild(frame);
        return;
      }
      if (kind !== 'web-component') return this.fail('tipo desconocido');
      const tag = this.getAttribute('tag') || '';
      if (!UI_SCRIPT.test(src) || !UI_TAG.test(tag) || tag === 'ec-ui-source' || tag === EDITOR) return this.fail('fuente no válida');
      try {
        if (!customElements.get(tag)) {
          await loadUiScript(src);
          await Promise.race([customElements.whenDefined(tag),
            new Promise((r, ko) => setTimeout(() => ko(new Error('el script no define <' + tag + '>')), 10000))]);
        }
      } catch (e) {
        return this.fail(e.message);
      }
      let attrs = {};
      try { attrs = JSON.parse(this.getAttribute('attrs') || '{}') || {}; } catch (e) { attrs = {}; }
      const el = document.createElement(tag);
      Object.entries(attrs).forEach(([name, value]) => {
        if (UI_ATTR.test(name) && !name.startsWith('on') && !UI_SKIP.has(name)) el.setAttribute(name, String(value));
      });
      this.textContent = '';
      this.appendChild(el);
    }
  }
  if (window.customElements && !customElements.get('ec-ui-source')) customElements.define('ec-ui-source', EcUiSource);

  let pending = false;
  const schedule = () => {
    if (!pending) {
      pending = true;
      requestAnimationFrame(scan);
    }
  };
  const scan = () => {
    pending = false;
    eachDeep(document, '[data-ec-embed]', upgrade);
    if (window.customElements && !customElements.get(EDITOR) && !editorLoading) {
      eachDeep(document, EDITOR, loadEditor);
    }
  };
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  setInterval(scan, 1500); // shadow roots the observer does not see into
  scan();
})();
