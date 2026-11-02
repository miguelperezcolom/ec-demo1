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

  // Mateu UIs embedded by content-hub (a MicroFrontend: a «Componente remoto» block, a remote page),
  // marked with CSS classes by RemoteUiRenderer. Mateu draws the island's page as a top-level one — an
  // automatic breadcrumb worked out from the HOST's menu and URL, and the remote's SetWindowTitle on the
  // browser tab — and has no "embedded" option yet. Until it has, this takes that chrome off:
  //  - every island (ec-remote-ui): no breadcrumb of its own — the intranet's is the page's;
  //  - a titled block (ec-remote-notitle): no page title either (the block has its own): a compact header;
  //  - a block (ec-remote-block): the tab keeps the host page's title (see the title guard below).
  // A remote page (ec-remote-page) may name the window.
  const ISLAND_CSS = '.breadcrumbs-bar{display:none!important}';
  const NOTITLE_CSS = '.form-header h2{display:none!important}';
  const dressed = new WeakMap();
  const watched = new WeakSet();
  const shadowRootsIn = (el, fn) => {
    const visit = (node) => {
      if (!node.shadowRoot) return;
      fn(node.shadowRoot);
      node.shadowRoot.querySelectorAll('*').forEach(visit);
    };
    visit(el);
    el.querySelectorAll('*').forEach(visit);
  };
  const dressIsland = (island) => {
    const css = ISLAND_CSS + (island.classList.contains('ec-remote-notitle') ? NOTITLE_CSS : '');
    shadowRootsIn(island, (root) => {
      if (dressed.get(root) !== css) {
        let style = [...root.children].find((c) => c.tagName === 'STYLE' && c.hasAttribute('data-ec-remote'));
        if (!style) {
          style = document.createElement('style');
          style.setAttribute('data-ec-remote', '');
          root.appendChild(style);
        }
        style.textContent = css;
        dressed.set(root, css);
      }
      // the island re-renders inside its own shadow roots, which the page's observer does not see
      if (!watched.has(root)) {
        watched.add(root);
        new MutationObserver(() => schedule()).observe(root, { childList: true, subtree: true });
      }
    });
  };

  // The title guard: a block's remote names the window when it loads (SetWindowTitle). When the tab's
  // title changes to a heading drawn inside a block island, it goes back to what it was — the host page's.
  // Checked a moment later, once the island has drawn the heading it named the window after.
  const blockHeadings = () => {
    const texts = new Set();
    eachDeep(document, '.ec-remote-ui.ec-remote-block', (island) => {
      const take = (root) => root.querySelectorAll('h1,h2,h3,[role=heading]')
        .forEach((h) => texts.add((h.textContent || '').trim()));
      take(island);
      shadowRootsIn(island, take);
    });
    return texts;
  };
  let shownTitle = document.title;
  const onTitle = () => {
    const t = document.title;
    const before = shownTitle;
    shownTitle = t;
    if (t === before || !t.trim()) return;
    const check = () => {
      if (document.title === t && blockHeadings().has(t.trim())) document.title = before;
    };
    setTimeout(check, 250);
    setTimeout(check, 1200);
  };
  if (document.head) {
    new MutationObserver(onTitle).observe(document.head, { childList: true, subtree: true, characterData: true });
  }

  let pending = false;
  const schedule = () => {
    if (!pending) {
      pending = true;
      requestAnimationFrame(scan);
    }
  };
  const scan = () => {
    pending = false;
    eachDeep(document, '.ec-remote-ui', dressIsland);
    eachDeep(document, '[data-ec-embed]', upgrade);
    if (window.customElements && !customElements.get(EDITOR) && !editorLoading) {
      eachDeep(document, EDITOR, loadEditor);
    }
  };
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  setInterval(scan, 1500); // shadow roots the observer does not see into
  scan();
})();
