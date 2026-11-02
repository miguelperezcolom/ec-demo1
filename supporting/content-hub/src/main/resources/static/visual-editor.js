// <ec-visual-editor page-id="…" api="/_intranet/api/editor">: a page of the intranet edited on the page
// itself. Hosted by the backoffice (VisualEditorPage, an Element of Mateu) in both shells, loaded by
// embed.js with the user's token. Vanilla: no framework, its own shadow root, colours taken from the shell
// (light or dark).
//
// What it does, against VisualEditorController:
//  - draws the page's DRAFT blocks with the portal's markup (the server renders them: PageRenderer);
//  - HTML and BANNER blocks are edited in place: title and text, with a small toolbar; [[media:id]] marks
//    are shown as the medium and written back as marks; pasting keeps only clean markup;
//  - a side panel for the selected block: its type, the fields of that type (those BlockForm shows) and
//    its audience as a narrowing of the page's, with the head count;
//  - "+" between blocks, drag (or ↑/↓) to reorder, delete;
//  - "Ver como…" draws the page as a profile of the directory would see it;
//  - Save replaces the draft's blocks (ItemCommands.editBlocks). Review and publication stay in the form.
//  - the language switch edits the blocks' text in es / en / de: a block with no text of its own in that
//    language shows the default language's, marked «sin traducir» (left as it is, it stays untranslated);
//    «Traducir con IA» fills what the language lacks, for the editor to review and save.
(() => {
  if (!window.customElements || customElements.get('ec-visual-editor')) return;

  const TOKEN = '__mateu_auth_token';
  const RICH = new Set(['HTML', 'BANNER']);
  const LIMITED = new Set(['AUTOMATIC_LISTING', 'LATEST', 'NOTICES']);
  const MARK = /\[\[media:([A-Za-z0-9_-]+)]]/g;
  const PASTE_TAGS = new Set(['P', 'BR', 'B', 'STRONG', 'I', 'EM', 'U', 'A', 'UL', 'OL', 'LI', 'H2', 'H3', 'H4', 'BLOCKQUOTE']);

  const LANGUAGE_NAMES = { es: 'Español', en: 'English', de: 'Deutsch' };

  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
  const newId = () => 'b' + Math.random().toString(16).slice(2, 8);
  const safeUrl = (u) => /^(https?:|mailto:|\/|#)/i.test(String(u || '').trim());

  // The styles of PageRenderer's HTML and BANNER sections: the in-place editor draws those two itself.
  const SECTION_STYLE = {
    HTML: 'margin:1rem 0',
    BANNER: 'margin:1rem 0;padding:1.25rem;border-radius:12px;background:linear-gradient(90deg,#1d5c7a,#2f8f8f);color:#fff',
  };

  const CSS = `
    :host { display:block; width:100%; color:inherit; font:inherit;
      --ve-accent: var(--lumo-primary-color, #2f6fbf);
      --ve-line: rgba(128,128,128,.35);
      --ve-soft: rgba(128,128,128,.08);
      --ve-bg: var(--lumo-base-color, Canvas); }
    * { box-sizing:border-box; }
    button, select, input { font:inherit; color:inherit; }
    button { cursor:pointer; background:var(--ve-soft); border:1px solid var(--ve-line); border-radius:6px; padding:.3rem .6rem; }
    button:hover:not(:disabled) { border-color:var(--ve-accent); }
    button:disabled { opacity:.5; cursor:default; }
    button.primary { background:var(--ve-accent); border-color:var(--ve-accent); color:#fff; }
    button.danger { color:#c62828; }
    select, textarea, input[type=text], input[type=url], input[type=number], input[type=search] {
      background:transparent; border:1px solid var(--ve-line); border-radius:6px; padding:.3rem .45rem; width:100%; }
    select option { background:var(--ve-bg); color:inherit; }
    .bar { display:flex; flex-wrap:wrap; gap:.5rem .75rem; align-items:center; padding:.5rem 0 .75rem;
      border-bottom:1px solid var(--ve-line); position:sticky; top:0; z-index:5; background:var(--ve-bg); }
    .bar h2 { font-size:1.05rem; margin:0; font-weight:600; }
    .chip { font-size:.75rem; border-radius:999px; padding:.1rem .55rem; background:var(--ve-soft); border:1px solid var(--ve-line); }
    .dirty { color:#b26a00; font-weight:600; font-size:.85rem; }
    .bar select { max-width:12rem; }
    .spacer { flex:1; }
    .bar label { display:flex; gap:.35rem; align-items:center; font-size:.85rem; }
    .bar select { width:auto; max-width:22rem; }
    .msgs { margin:.5rem 0; }
    .msg { padding:.45rem .7rem; border-radius:8px; margin:.25rem 0; font-size:.9rem; }
    .msg.error { background:rgba(198,40,40,.12); border:1px solid rgba(198,40,40,.45); }
    .msg.ok { background:rgba(46,125,50,.12); border:1px solid rgba(46,125,50,.45); }
    .msg.warn { background:rgba(178,106,0,.12); border:1px solid rgba(178,106,0,.45); }
    .msg.info { background:var(--ve-soft); border:1px solid var(--ve-line); }
    .main { display:grid; grid-template-columns:minmax(0,1fr) 340px; gap:1.25rem; align-items:start; margin-top:.5rem; }
    @media (max-width: 1000px) { .main { grid-template-columns:1fr; } }
    .canvas { max-width:860px; width:100%; }
    .pagehead { opacity:.75; font-size:.85rem; margin:.25rem 0 .5rem; }
    .blk { position:relative; border-radius:8px; outline:2px solid transparent; outline-offset:4px; margin:.25rem 0; }
    .canvas.editing .blk:hover { outline-color:color-mix(in srgb, var(--ve-accent) 45%, transparent); outline-style:dashed; }
    .blk.sel { outline:2px solid var(--ve-accent) !important; }
    .blk.drop-before { box-shadow:0 -4px 0 0 var(--ve-accent); }
    .blk.drop-after { box-shadow:0 4px 0 0 var(--ve-accent); }
    .blk.hidden { opacity:.55; }
    .tag { position:absolute; left:0; top:-.85rem; font-size:.7rem; padding:0 .4rem; border-radius:4px;
      background:var(--ve-accent); color:#fff; display:none; z-index:2; white-space:nowrap; max-width:70%; overflow:hidden; text-overflow:ellipsis; }
    .tools { position:absolute; right:0; top:-.95rem; display:none; gap:.2rem; z-index:3; }
    .tools button { padding:0 .4rem; font-size:.8rem; background:var(--ve-bg); }
    .canvas.editing .blk:hover .tag, .blk.sel .tag, .canvas.editing .blk:hover .tools, .blk.sel .tools { display:flex; }
    .handle { cursor:grab; }
    .empty { padding:.75rem; border:1px dashed var(--ve-line); border-radius:8px; opacity:.75; font-size:.9rem; }
    .gone { padding:.4rem .75rem; border:1px dashed var(--ve-line); border-radius:8px; font-size:.85rem; opacity:.8; }
    .ins { display:flex; justify-content:center; height:1.1rem; position:relative; margin:.15rem 0; }
    .ins button { border-radius:999px; width:1.6rem; height:1.6rem; padding:0; line-height:1; opacity:.35; position:relative; top:-.25rem; }
    .ins:hover button, .ins button:focus { opacity:1; }
    .ins.alone { height:auto; } .ins.alone button { opacity:1; width:auto; height:auto; padding:.4rem .9rem; border-radius:8px; top:0; }
    .menu { position:absolute; top:1.5rem; z-index:10; background:var(--ve-bg); border:1px solid var(--ve-line); border-radius:8px;
      box-shadow:0 8px 24px rgba(0,0,0,.2); padding:.35rem; display:grid; grid-template-columns:1fr 1fr; gap:.25rem; min-width:20rem; }
    /* the menu sits inside .ins: its items must not take the round «+» button's look */
    .ins .menu button { text-align:left; width:auto; height:auto; border-radius:6px; padding:.4rem .6rem; opacity:1; top:0;
      line-height:1.2; white-space:nowrap; }
    [contenteditable=true] { outline:none; cursor:text; }
    [contenteditable=true]:focus { box-shadow:inset 0 0 0 1px color-mix(in srgb, var(--ve-accent) 35%, transparent); border-radius:4px; }
    h3[data-ph]:empty::before, div[data-ph]:empty::before { content:attr(data-ph); opacity:.5; }
    .rt { display:flex; flex-wrap:wrap; gap:.2rem; margin:0 0 .35rem; position:sticky; top:3.2rem; z-index:4; }
    .rt button { padding:.1rem .45rem; font-size:.85rem; background:var(--ve-bg); color:var(--lumo-body-text-color, inherit); }
    .ec-media { position:relative; border:1px dashed rgba(128,128,128,.6); border-radius:8px; padding:.25rem; margin:.5rem 0; user-select:none; }
    .ec-media::after { content:'medio · ' attr(data-ec-media); position:absolute; top:.2rem; right:.3rem; font-size:.7rem;
      background:rgba(0,0,0,.55); color:#fff; padding:0 .3rem; border-radius:4px; }
    span.ec-media { display:inline-block; }
    /* while editing, a player is a picture: a click on it selects its block (an iframe would swallow it) */
    .canvas.editing .blk iframe, .canvas.editing .blk video, .canvas.editing .blk audio { pointer-events:none; }
    .panel { position:sticky; top:3.5rem; border:1px solid var(--ve-line); border-radius:10px; padding:.75rem .9rem; background:var(--ve-bg);
      max-height:calc(100vh - 8rem); overflow:auto; }
    .panel h3 { margin:.1rem 0 .5rem; font-size:1rem; }
    .panel h4 { margin:.9rem 0 .35rem; font-size:.85rem; text-transform:uppercase; letter-spacing:.03em; opacity:.75; }
    .field { margin:.45rem 0; } .field > label { display:block; font-size:.8rem; opacity:.8; margin-bottom:.15rem; }
    .note { font-size:.8rem; opacity:.75; margin:.35rem 0; }
    .links { width:100%; border-collapse:collapse; } .links td { padding:.15rem .1rem; }
    .checks { display:flex; flex-wrap:wrap; gap:.25rem; max-height:8.5rem; overflow:auto; padding:.2rem; border:1px solid var(--ve-line); border-radius:6px; }
    .checks label { font-size:.8rem; display:inline-flex; gap:.25rem; align-items:center; padding:.1rem .4rem; border-radius:999px; background:var(--ve-soft); cursor:pointer; }
    .checks label.on { background:color-mix(in srgb, var(--ve-accent) 25%, transparent); }
    .aud { font-size:.85rem; padding:.4rem .55rem; border-radius:6px; background:var(--ve-soft); margin:.3rem 0; }
    .aud b { font-weight:600; }
    .row { display:flex; gap:.35rem; flex-wrap:wrap; margin-top:.75rem; }
    .modal { position:fixed; inset:0; background:rgba(0,0,0,.45); z-index:1000; display:flex; align-items:center; justify-content:center; }
    .dialog { background:var(--ve-bg); border-radius:12px; padding:1rem; width:min(760px, 94vw); max-height:84vh; overflow:auto; border:1px solid var(--ve-line); }
    .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(150px, 1fr)); gap:.5rem; margin-top:.5rem; }
    .grid button { display:flex; flex-direction:column; gap:.3rem; align-items:stretch; text-align:left; padding:.4rem; }
    .grid img, .grid .ph { width:100%; height:90px; object-fit:cover; border-radius:6px; background:var(--ve-soft);
      display:flex; align-items:center; justify-content:center; font-size:1.6rem; }
    .grid span { font-size:.8rem; }
    /* in the flow, above the block's content (never over its title or text) */
    .untr { display:inline-block; margin:.15rem 0 .1rem; font-size:.7rem; line-height:1.4; padding:0 .45rem; border-radius:999px;
      background:rgba(178,106,0,.9); color:#fff; pointer-events:none; }
    .blk.untranslated .ttl, .blk.untranslated .body { opacity:.75; }
    .sr { position:absolute; width:1px; height:1px; overflow:hidden; clip:rect(0 0 0 0); }
  `;

  class VisualEditor extends HTMLElement {
    constructor() {
      super();
      this.root = this.attachShadow({ mode: 'open' });
      this.state = { page: null, blocks: [], rendered: {}, media: {}, catalog: null, profiles: [], warnings: [],
        selected: null, dirty: false, viewAs: '', profileView: null, messages: [], busy: false, menuAt: null,
        language: null };
      this.range = null;
      this.onBeforeUnload = (e) => { if (this.state.dirty) { e.preventDefault(); e.returnValue = ''; } };
      this.onLeaveClick = (e) => this.guardLeave(e);
      this.onSelection = () => this.keepRange();
    }

    get api() { return this.getAttribute('api') || '/_intranet/api/editor'; }
    get pageId() { return this.getAttribute('page-id') || ''; }
    /** What is edited: 'pages' (a page of a site) or 'issues' (a newsletter issue) — the same blocks either way. */
    get resource() { return this.getAttribute('resource') === 'issues' ? 'issues' : 'pages'; }

    connectedCallback() {
      window.addEventListener('beforeunload', this.onBeforeUnload);
      document.addEventListener('click', this.onLeaveClick, true);
      document.addEventListener('selectionchange', this.onSelection);
      this.themeTimer = setInterval(() => this.followTheme(), 2000);
      if (!this.started) {
        this.started = true;
        this.root.innerHTML = '<style>' + CSS + '</style><div class="msg info">Cargando el editor visual…</div>';
        this.load();
      }
      this.followTheme();
    }

    disconnectedCallback() {
      window.removeEventListener('beforeunload', this.onBeforeUnload);
      document.removeEventListener('click', this.onLeaveClick, true);
      document.removeEventListener('selectionchange', this.onSelection);
      clearInterval(this.themeTimer);
    }

    // The shell's theme, from the colour of the text it gives us: light text → dark scheme for native controls.
    followTheme() {
      const c = getComputedStyle(this).color.match(/\d+(\.\d+)?/g);
      if (!c) return;
      const light = (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) > 140;
      this.style.colorScheme = light ? 'dark' : 'light';
    }

    // ── server ─────────────────────────────────────────────────────────────────────────────────────

    async call(method, path, body) {
      let token = null;
      try { token = localStorage.getItem(TOKEN); } catch (e) { /* private window */ }
      const headers = { Accept: 'application/json' };
      if (token) headers.Authorization = 'Bearer ' + token;
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      const answer = await fetch(this.api + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        credentials: 'same-origin' });
      let data = null;
      try { data = await answer.json(); } catch (e) { /* no body */ }
      if (!answer.ok) {
        const text = answer.status === 401 ? 'Tu sesión ha caducado: recarga la página.'
          : (data && data.error) || ('El servidor ha respondido ' + answer.status);
        const error = new Error(text);
        error.status = answer.status;
        throw error;
      }
      return data;
    }

    async load() {
      try {
        const data = await this.call('GET', '/' + this.resource + '/' + encodeURIComponent(this.pageId)
          + (this.state.language ? '?language=' + encodeURIComponent(this.state.language) : ''));
        this.take(data);
        this.state.dirty = false;
        this.render();
      } catch (e) {
        this.root.innerHTML = '<style>' + CSS + '</style><div class="msg error">' + esc(e.message) + '</div>';
      }
    }

    take(data) {
      const s = this.state;
      s.page = data.page;
      s.language = data.page.language || s.language;
      s.catalog = data.catalog;
      s.profiles = data.profiles || [];
      s.blocks = (data.blocks || []).map((b) => this.normal(b));
      s.rendered = {};
      (data.rendered || []).forEach((r) => { s.rendered[r.id] = r; });
      s.media = Object.assign({}, s.media, data.media || {});
      s.warnings = data.warnings || [];
      if (s.selected && !s.blocks.some((b) => b.id === s.selected)) s.selected = null;
    }

    normal(b) {
      return { id: b.id || newId(), type: b.type || 'HTML', title: b.title || '', html: b.html || '', itemId: b.itemId || '',
        sectionId: b.sectionId || '', limit: b.limit == null ? null : b.limit, links: (b.links || []).map((l) => ({ label: l.label || '', url: l.url || '' })),
        mediaId: b.mediaId || '', departments: b.departments || [], hotels: b.hotels || [], jobTitles: b.jobTitles || [],
        // a «Componente remoto»: a registered UI and its route, or its attributes (edited as name=value lines)
        remoteSourceId: b.remoteSourceId || '', remoteRoute: b.remoteRoute || '',
        remoteAttributes: Object.entries(b.remoteAttributes || {}).map(([k, v]) => k + '=' + v).join('\n'),
        // its text is the default language's: this language has none of its own (yet)
        untranslated: !!b.untranslated };
    }

    get translating() { const p = this.state.page || {}; return !!p.language && p.language !== p.defaultLanguage; }

    // name=value lines into the attributes the server checks (it refuses event handlers, javascript: and the like)
    attrsOf(text) {
      const out = {};
      (text || '').split(/\r?\n/).forEach((line) => {
        const i = line.indexOf('=');
        if (i > 0) out[line.slice(0, i).trim()] = line.slice(i + 1).trim();
      });
      return out;
    }

    wire() {
      return this.state.blocks.map((b) => ({
        id: b.id, type: b.type, title: b.title || null, html: RICH.has(b.type) ? (b.html || '') : null,
        itemId: b.type === 'FEATURED' ? (b.itemId || null) : null,
        sectionId: (b.type === 'AUTOMATIC_LISTING' || b.type === 'LATEST') ? (b.sectionId || null) : null,
        limit: LIMITED.has(b.type) ? (b.limit == null || b.limit === '' ? null : Number(b.limit)) : null,
        links: b.type === 'QUICK_LINKS' ? b.links.filter((l) => l.url && l.url.trim()) : [],
        mediaId: b.type === 'MEDIA' ? (b.mediaId || null) : null,
        departments: b.departments, hotels: b.hotels, jobTitles: b.jobTitles,
        remoteSourceId: b.type === 'REMOTE' ? (b.remoteSourceId || null) : null,
        remoteRoute: b.type === 'REMOTE' ? (b.remoteRoute || null) : null,
        remoteAttributes: b.type === 'REMOTE' ? this.attrsOf(b.remoteAttributes) : {},
      }));
    }

    // Draws the unsaved blocks again (fields that only the server can draw, the head counts), debounced.
    refresh(now) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = setTimeout(() => this.preview(), now ? 0 : 350);
    }

    async preview() {
      const s = this.state;
      const seq = (this.previewSeq = (this.previewSeq || 0) + 1);
      try {
        const data = await this.call('POST', '/' + this.resource + '/' + encodeURIComponent(this.pageId) + '/preview',
          { blocks: this.wire(), viewAs: s.viewAs || null, language: s.language });
        if (seq !== this.previewSeq) return;
        s.rendered = {};
        (data.blocks || []).forEach((r) => { s.rendered[r.id] = r; });
        s.media = Object.assign({}, s.media, data.media || {});
        s.warnings = data.warnings || [];
        s.profileView = s.viewAs ? { label: data.profile, pageVisible: data.pageVisible } : null;
        this.clearMessages('error');
        this.patchCanvas();
        this.patchPanel();
        this.renderMessages();
      } catch (e) {
        if (seq === this.previewSeq) this.message('error', e.message);
      }
    }

    async save() {
      const s = this.state;
      if (s.busy) return;
      this.syncEditing();
      s.busy = true;
      s.messages = []; // a new attempt: earlier errors and successes no longer hold
      this.renderBar();
      this.renderMessages();
      try {
        const data = await this.call('PUT', '/' + this.resource + '/' + encodeURIComponent(this.pageId) + '/blocks',
          { blocks: this.wire(), updatedAt: s.page && s.page.updatedAt, language: s.language });
        this.take(data);
        s.dirty = false;
        s.busy = false;
        s.messages = [];
        this.render();
        this.message('ok', 'Guardado en el borrador. Para que lo vean los lectores: «Volver al formulario» → enviar a revisión o publicar.');
      } catch (e) {
        s.busy = false;
        this.renderBar();
        this.message('error', 'No se ha guardado: ' + e.message);
      }
    }

    // Another language: its text of the blocks, from the server (what is not saved would be lost: asked first).
    async switchLanguage(language) {
      const s = this.state;
      if (language === s.language) return;
      this.syncEditing();
      if (s.dirty && !confirm('Hay cambios sin guardar. Si cambias de idioma se pierden: ¿cambiar igualmente?')) {
        this.renderBar();
        return;
      }
      s.language = language;
      s.dirty = false;
      s.messages = [];
      this.root.innerHTML = '<style>' + CSS + '</style><div class="msg info">Cargando los bloques en «' + esc(language) + '»…</div>';
      await this.load();
    }

    // «Traducir con IA»: what this language lacks of the blocks, filled by the server's translator; not saved —
    // the editor reviews it here and saves.
    async translate() {
      const s = this.state;
      if (s.busy) return;
      this.syncEditing();
      s.busy = true;
      s.messages = []; // a new attempt: what earlier ones said (an error, a success) no longer holds
      this.renderBar();
      this.message('info', 'Traduciendo con IA al «' + s.language + '»…');
      try {
        const data = await this.call('POST', '/' + this.resource + '/' + encodeURIComponent(this.pageId) + '/translate',
          { blocks: this.wire(), language: s.language });
        s.busy = false;
        this.clearMessages('info');
        if (!data.translated) {
          this.renderBar();
          this.message('info', 'No hay nada que traducir: todos los bloques tienen ya su texto en «' + s.language + '».');
          return;
        }
        s.blocks = (data.blocks || []).map((b) => this.normal(b));
        s.rendered = {};
        (data.rendered || []).forEach((r) => { s.rendered[r.id] = r; });
        s.media = Object.assign({}, s.media, data.media || {});
        s.dirty = true;
        this.render();
        this.message('ok', 'Traducido con IA al «' + s.language + '» (' + data.translated + ' texto(s)). Revísalo y guarda.');
      } catch (e) {
        s.busy = false;
        this.clearMessages('info');
        this.renderBar();
        this.message('error', 'No se ha traducido: ' + e.message);
      }
    }

    // ── messages ───────────────────────────────────────────────────────────────────────────────────

    message(kind, text) {
      this.state.messages = this.state.messages.filter((m) => m.kind !== kind);
      this.state.messages.push({ kind, text });
      this.renderMessages();
    }

    clearMessages(kind) {
      this.state.messages = this.state.messages.filter((m) => m.kind !== kind);
    }

    renderMessages() {
      const box = this.root.querySelector('.msgs');
      if (!box) return;
      const s = this.state;
      const all = s.messages.map((m) => '<div class="msg ' + m.kind + '" role="' + (m.kind === 'error' ? 'alert' : 'status') + '">'
        + esc(m.text) + '</div>');
      s.warnings.forEach((w) => all.push('<div class="msg warn">⚠️ ' + esc(w) + '</div>'));
      box.innerHTML = all.join('');
    }

    // ── drawing ────────────────────────────────────────────────────────────────────────────────────

    render() {
      this.root.innerHTML = '<style>' + CSS + '</style><div class="bar"></div><div class="msgs" aria-live="polite"></div>'
        + '<div class="main"><div class="canvas"></div><aside class="panel" aria-label="Bloque seleccionado"></aside></div>';
      this.renderBar();
      this.renderMessages();
      this.renderCanvas();
      this.renderPanel();
    }

    renderBar() {
      const bar = this.root.querySelector('.bar');
      if (!bar) return;
      const s = this.state;
      const p = s.page || {};
      bar.innerHTML = '<h2>' + esc(p.title || p.id) + '</h2><span class="chip">' + esc(p.status || '') + '</span>'
        + '<label title="El texto de los bloques en cada idioma; lo que un idioma no tiene, sus lectores lo ven en el idioma por defecto">'
        + 'Idioma <select class="lang"' + (s.busy ? ' disabled' : '') + '>'
        + (p.languages || [p.language || 'es']).map((l) => '<option value="' + esc(l) + '"' + (l === p.language ? ' selected' : '') + '>'
          + esc(LANGUAGE_NAMES[l] || l) + (l === p.defaultLanguage ? ' (por defecto)' : '') + '</option>').join('') + '</select></label>'
        + (this.translating && s.blocks.some((b) => b.untranslated)
          ? '<span class="chip" title="Sus lectores ven esos bloques en el idioma por defecto">'
            + s.blocks.filter((b) => b.untranslated).length + ' sin traducir</span>' : '')
        + (this.translating && p.canTranslate && p.canEdit
          ? '<button class="translate"' + (s.busy ? ' disabled' : '') + ' title="Rellena lo que falta en este idioma desde el idioma por defecto; revísalo y guarda">'
            + 'Traducir con IA</button>' : '')
        // always there, only hidden when clean: appearing would rewrap the bar and move what is under the cursor
        + '<span class="dirty" role="status"' + (s.dirty ? '' : ' style="visibility:hidden"') + '>● Cambios sin guardar</span>'
        + '<span class="spacer"></span>'
        + '<label>Ver como <select class="viewas"><option value="">yo, editando</option>'
        + s.profiles.map((o) => '<option value="' + esc(o.value) + '"' + (o.value === s.viewAs ? ' selected' : '') + '>'
          + esc(o.label) + '</option>').join('') + '</select></label>'
        + '<button class="primary save"' + (!s.dirty || s.busy || !p.canEdit ? ' disabled' : '') + '>'
        + (s.busy ? 'Guardando…' : 'Guardar') + '</button>';
      bar.querySelector('.viewas').addEventListener('change', (e) => this.viewAs(e.target.value));
      bar.querySelector('.lang').addEventListener('change', (e) => this.switchLanguage(e.target.value));
      bar.querySelector('.translate')?.addEventListener('click', () => this.translate());
      bar.querySelector('.save').addEventListener('click', () => this.save());
      if (!p.canEdit) this.message('info', 'Solo puedes verlo: guardar pide el permiso content:edit.');
    }

    viewAs(hash) {
      this.syncEditing();
      this.state.viewAs = hash;
      this.state.selected = hash ? null : this.state.selected;
      if (!hash) this.state.profileView = null;
      this.renderCanvas();
      this.renderPanel();
      this.refresh(true);
    }

    get editing() { return !this.state.viewAs; }

    renderCanvas() {
      const canvas = this.root.querySelector('.canvas');
      if (!canvas) return;
      const s = this.state;
      const p = s.page || {};
      canvas.classList.toggle('editing', this.editing);
      let html = '';
      if (this.editing) {
        html += '<div class="pagehead">La página la ven: <b>' + esc(p.audience) + '</b> — ' + p.count + ' empleado(s).'
          + ' Pasa el ratón por un bloque y haz clic para editarlo.'
          + (this.translating ? ' Escribes el texto en <b>' + esc(LANGUAGE_NAMES[p.language] || p.language) + '</b>: tipo, enlaces, medios'
            + ' y audiencia son los mismos en todos los idiomas.' : '') + '</div>';
      } else {
        const v = s.profileView;
        html += '<div class="msg info">👁 Así la ve el perfil <b>' + esc(v ? v.label : '…') + '</b> (sin guardar lo que no hayas guardado).'
          + ' <button class="back">Volver a editar</button></div>';
        if (v && !v.pageVisible) html += '<div class="msg warn">Este perfil no ve la página: su audiencia no lo incluye.</div>';
      }
      s.blocks.forEach((b, i) => {
        if (this.editing) html += this.insertRow(i);
        html += this.blockHtml(b, i);
      });
      if (this.editing) html += this.insertRow(s.blocks.length, s.blocks.length === 0);
      canvas.innerHTML = html;
      this.wireCanvas(canvas);
    }

    // After a preview: the blocks the server draws get their new HTML; the text blocks, being edited
    // (the caret is in them), are left alone.
    patchCanvas() {
      if (!this.editing) { this.renderCanvas(); return; }
      this.state.blocks.forEach((b) => {
        if (RICH.has(b.type)) return;
        const content = this.root.querySelector('.blk[data-id="' + b.id + '"] > .content');
        if (content) content.innerHTML = this.serverInner(b);
      });
    }

    serverInner(b) {
      const r = this.state.rendered[b.id];
      return r && r.html ? r.html : '<div class="empty">«' + esc(this.typeLabel(b.type)) + '»' + (b.title ? ' · ' + esc(b.title) : '')
        + ': nada que mostrar ahora' + (this.hint(b) ? ' — ' + this.hint(b) : '') + '.</div>';
    }

    insertRow(at, alone) {
      return '<div class="ins' + (alone ? ' alone' : '') + '" data-at="' + at + '"><button class="add" aria-label="Añadir un bloque aquí" title="Añadir un bloque aquí">'
        + (alone ? '+ Añadir el primer bloque' : '+') + '</button>'
        + (this.state.menuAt === at ? this.typeMenu() : '') + '</div>';
    }

    typeMenu() {
      return '<div class="menu" role="menu">' + this.state.catalog.types.map((t) =>
        '<button role="menuitem" data-type="' + esc(t.value) + '">' + esc(t.label) + '</button>').join('') + '</div>';
    }

    typeLabel(type) {
      const t = (this.state.catalog.types || []).find((x) => x.value === type);
      return t ? t.label : type;
    }

    narrowing(b) {
      const parts = [];
      if (b.departments.length) parts.push(b.departments.join(', '));
      if (b.hotels.length) parts.push(b.hotels.join(', '));
      if (b.jobTitles.length) parts.push(b.jobTitles.join(', '));
      return parts.join(' · ');
    }

    blockHtml(b, i) {
      const s = this.state;
      const r = s.rendered[b.id];
      const sel = this.editing && s.selected === b.id;
      const narrowed = this.narrowing(b);
      let inner;
      if (!this.editing) {
        if (r && !r.visible) {
          return '<div class="blk hidden" data-id="' + esc(b.id) + '"><div class="gone">🚫 No lo ve este perfil: «'
            + esc(b.title || this.typeLabel(b.type)) + '»' + (narrowed ? ' (solo ' + esc(narrowed) + ')' : '') + '</div></div>';
        }
        inner = r ? r.html : '';
        if (!inner) inner = '<div class="empty">«' + esc(this.typeLabel(b.type)) + '»: nada que mostrar a este perfil ahora.</div>';
        return '<div class="blk" data-id="' + esc(b.id) + '">' + inner + '</div>';
      }
      if (RICH.has(b.type)) {
        inner = '<section style="' + SECTION_STYLE[b.type] + '">'
          + (sel ? this.richToolbar() : '')
          + '<h3 class="ttl" style="margin:.25rem 0 .5rem" contenteditable="true" spellcheck="true" data-ph="Título (opcional)" aria-label="Título del bloque">'
          + esc(b.title) + '</h3>'
          + '<div class="body" contenteditable="true" spellcheck="true" data-ph="Escribe aquí…" aria-label="Contenido del bloque">'
          + this.toEditable(b.html) + '</div></section>';
      } else {
        inner = '<div class="content">' + this.serverInner(b) + '</div>';
      }
      const untranslated = this.translating && b.untranslated;
      return '<div class="blk' + (sel ? ' sel' : '') + (untranslated ? ' untranslated' : '') + '" data-id="' + esc(b.id)
        + '" data-i="' + i + '" tabindex="0" aria-label="Bloque '
        + (i + 1) + ': ' + esc(this.typeLabel(b.type)) + (untranslated ? ', sin traducir' : '') + '">'
        + (untranslated ? '<span class="untr" title="Sin texto propio en este idioma: se ve en el idioma por defecto ('
          + esc(s.page.defaultLanguage) + '). Escríbelo o usa «Traducir con IA».">sin traducir · '
          + esc(s.page.defaultLanguage) + '</span>' : '')
        + '<span class="tag">' + esc(this.typeLabel(b.type)) + (narrowed ? ' · solo ' + esc(narrowed) : '') + '</span>'
        + '<span class="tools"><button class="handle" draggable="true" title="Arrastra para mover" aria-label="Mover">⠿</button>'
        + '<button class="up" title="Subir" aria-label="Subir"' + (i === 0 ? ' disabled' : '') + '>↑</button>'
        + '<button class="down" title="Bajar" aria-label="Bajar"' + (i === s.blocks.length - 1 ? ' disabled' : '') + '>↓</button>'
        + '<button class="del danger" title="Eliminar" aria-label="Eliminar">✕</button></span>'
        + inner + '</div>';
    }

    hint(b) {
      switch (b.type) {
        case 'FEATURED': return b.itemId ? 'el item elegido no se ve con tu perfil' : 'elige el item en el panel';
        case 'MEDIA': return b.mediaId ? '' : 'elige el medio en el panel';
        case 'QUICK_LINKS': return 'añade enlaces en el panel';
        case 'PENDING': return 'se pinta solo a quien tiene algo pendiente de confirmar';
        case 'AUTOMATIC_LISTING': return 'no hay contenidos visibles en esa sección';
        case 'REMOTE': return b.remoteSourceId ? '' : 'elige la UI externa en el panel';
        default: return '';
      }
    }

    richToolbar() {
      const b = (cmd, label, title) => '<button data-cmd="' + cmd + '" title="' + title + '" aria-label="' + title + '">' + label + '</button>';
      return '<div class="rt" role="toolbar" aria-label="Formato">'
        + b('bold', '<b>B</b>', 'Negrita') + b('italic', '<i>I</i>', 'Cursiva') + b('link', '🔗', 'Enlace')
        + b('insertUnorderedList', '•', 'Lista') + b('insertOrderedList', '1.', 'Lista numerada')
        + b('h2', 'H2', 'Título') + b('h3', 'H3', 'Subtítulo') + b('p', '¶', 'Párrafo')
        + b('removeFormat', '⌫', 'Quitar formato') + b('media', '🖼 Medio', 'Insertar un medio de la biblioteca') + '</div>';
    }

    // [[media:id]] → the medium as drawn, not editable; a mark alone in its paragraph becomes a block.
    toEditable(html) {
      const media = this.state.media;
      const box = (id, tag) => '<' + tag + ' class="ec-media" contenteditable="false" data-ec-media="' + esc(id) + '">'
        + (media[id] || '<p style="opacity:.6">[medio ' + esc(id) + ']</p>') + '</' + tag + '>';
      return String(html || '')
        .replace(/<p>\s*\[\[media:([A-Za-z0-9_-]+)]]\s*<\/p>/g, (m, id) => box(id, 'div'))
        .replace(MARK, (m, id) => box(id, 'span'));
    }

    // …and back: each drawn medium becomes its mark again.
    fromEditable(el) {
      const copy = el.cloneNode(true);
      copy.querySelectorAll('[data-ec-media]').forEach((m) => {
        const mark = document.createTextNode('[[media:' + m.getAttribute('data-ec-media') + ']]');
        if (m.tagName === 'DIV') {
          const p = document.createElement('p');
          p.appendChild(mark);
          m.replaceWith(p);
        } else {
          m.replaceWith(mark);
        }
      });
      const html = copy.innerHTML.trim();
      return html === '<br>' ? '' : html;
    }

    wireCanvas(canvas) {
      const s = this.state;
      canvas.querySelector('.back')?.addEventListener('click', () => {
        const sel = this.root.querySelector('.viewas');
        if (sel) sel.value = '';
        this.viewAs('');
      });
      // links inside the drawn blocks lead nowhere while editing
      canvas.addEventListener('click', (e) => {
        const a = e.composedPath().find((n) => n.tagName === 'A');
        if (a && canvas.contains(a)) e.preventDefault();
      });
      if (!this.editing) return;
      canvas.querySelectorAll('.ins .add').forEach((btn) => btn.addEventListener('click', (e) => {
        const at = Number(btn.parentElement.dataset.at);
        s.menuAt = s.menuAt === at ? null : at;
        this.syncEditing();
        this.renderCanvas();
        e.stopPropagation();
      }));
      canvas.querySelectorAll('.menu button').forEach((btn) => btn.addEventListener('click', () => {
        this.add(Number(btn.closest('.ins').dataset.at), btn.dataset.type);
      }));
      canvas.querySelectorAll('.blk').forEach((el) => {
        const id = el.dataset.id;
        el.addEventListener('mousedown', () => { if (s.selected !== id) this.select(id, el); });
        el.addEventListener('focusin', () => { if (s.selected !== id) this.select(id, el); });
        el.addEventListener('keydown', (e) => {
          if (e.target === el && (e.key === 'Delete')) this.remove(id);
        });
        el.querySelector('.up')?.addEventListener('click', () => this.move(id, -1));
        el.querySelector('.down')?.addEventListener('click', () => this.move(id, 1));
        el.querySelector('.del')?.addEventListener('click', () => this.remove(id));
        const handle = el.querySelector('.handle');
        handle?.addEventListener('dragstart', (e) => {
          this.dragging = id;
          e.dataTransfer.effectAllowed = 'move';
          e.dataTransfer.setData('text/plain', id);
          e.dataTransfer.setDragImage(el, 20, 20);
        });
        handle?.addEventListener('dragend', () => { this.dragging = null; this.clearDrop(); });
        el.addEventListener('dragover', (e) => {
          if (!this.dragging || this.dragging === id) return;
          e.preventDefault();
          const box = el.getBoundingClientRect();
          const after = e.clientY > box.top + box.height / 2;
          this.clearDrop();
          el.classList.add(after ? 'drop-after' : 'drop-before');
        });
        el.addEventListener('drop', (e) => {
          if (!this.dragging) return;
          e.preventDefault();
          const after = el.classList.contains('drop-after');
          this.clearDrop();
          this.moveTo(this.dragging, id, after);
          this.dragging = null;
        });
        const ttl = el.querySelector('.ttl');
        const body = el.querySelector('.body');
        if (ttl) {
          ttl.addEventListener('input', () => {
            const b = this.block(id);
            b.title = ttl.textContent.replace(/\s+/g, ' ').trim();
            this.dirty();
            this.translated(id);
            const input = this.root.querySelector('.panel .f-title');
            if (input) input.value = b.title;
          });
          ttl.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); body && body.focus(); } });
          ttl.addEventListener('paste', (e) => {
            e.preventDefault();
            document.execCommand('insertText', false, (e.clipboardData.getData('text/plain') || '').replace(/\s+/g, ' '));
          });
        }
        if (body) {
          body.addEventListener('input', () => {
            this.block(id).html = this.fromEditable(body);
            this.dirty();
            this.translated(id);
          });
          body.addEventListener('paste', (e) => this.paste(e));
        }
        el.querySelectorAll('.rt button').forEach((btn) => {
          btn.addEventListener('mousedown', (e) => e.preventDefault()); // keeps the selection in the text
          btn.addEventListener('click', () => this.format(btn.dataset.cmd, body, id));
        });
      });
    }

    clearDrop() {
      this.root.querySelectorAll('.drop-before, .drop-after').forEach((n) => n.classList.remove('drop-before', 'drop-after'));
    }

    block(id) { return this.state.blocks.find((b) => b.id === id); }

    // What the open text editors hold, into the model (before redrawing them).
    syncEditing() {
      this.root.querySelectorAll('.blk').forEach((el) => {
        const b = this.block(el.dataset.id);
        if (b && b.untranslated) return; // the default language's text, as shown: not this language's
        const body = el.querySelector('.body');
        const ttl = el.querySelector('.ttl');
        if (b && body) b.html = this.fromEditable(body);
        if (b && ttl) b.title = ttl.textContent.replace(/\s+/g, ' ').trim();
      });
    }

    select(id, el) {
      const s = this.state;
      s.selected = id;
      s.menuAt = null;
      this.root.querySelectorAll('.blk.sel').forEach((n) => n.classList.remove('sel'));
      // the toolbar of a text block appears when it is selected: redraw only that block's toolbar
      this.root.querySelectorAll('.rt').forEach((n) => n.remove());
      if (el) {
        el.classList.add('sel');
        const b = this.block(id);
        if (b && RICH.has(b.type)) {
          const section = el.querySelector('section');
          const tmp = document.createElement('div');
          tmp.innerHTML = this.richToolbar();
          const rt = tmp.firstElementChild;
          section.insertBefore(rt, section.firstChild);
          const body = el.querySelector('.body');
          rt.querySelectorAll('button').forEach((btn) => {
            btn.addEventListener('mousedown', (e) => e.preventDefault());
            btn.addEventListener('click', () => this.format(btn.dataset.cmd, body, id));
          });
        }
      }
      this.renderPanel();
    }

    dirty() {
      if (!this.state.dirty) {
        this.state.dirty = true;
        this.renderBar();
      }
    }

    // Its text written in this language: no longer «sin traducir» (the server keeps what was left as shown untranslated).
    translated(id) {
      const b = this.block(id);
      if (!b || !b.untranslated) return;
      b.untranslated = false;
      const el = this.root.querySelector('.blk[data-id="' + id + '"]');
      if (el) {
        el.classList.remove('untranslated');
        el.querySelector('.untr')?.remove();
      }
      this.renderBar();
    }

    changed(redraw) {
      this.dirty();
      if (redraw) {
        this.syncEditing();
        this.renderCanvas();
      }
      this.renderPanel();
      this.refresh();
    }

    add(at, type) {
      const s = this.state;
      this.syncEditing();
      const b = this.normal({ id: newId(), type, title: '', html: RICH.has(type) ? '' : null, limit: LIMITED.has(type) ? 5 : null });
      s.blocks.splice(at, 0, b);
      s.menuAt = null;
      s.selected = b.id;
      this.changed(true);
      const el = this.root.querySelector('.blk[data-id="' + b.id + '"]');
      if (el) {
        el.scrollIntoView({ block: 'nearest' });
        (el.querySelector('.ttl') || el).focus();
      }
    }

    move(id, delta) {
      const s = this.state;
      const i = s.blocks.findIndex((b) => b.id === id);
      const j = i + delta;
      if (i < 0 || j < 0 || j >= s.blocks.length) return;
      this.syncEditing();
      const [b] = s.blocks.splice(i, 1);
      s.blocks.splice(j, 0, b);
      this.changed(true);
      this.root.querySelector('.blk[data-id="' + id + '"] ' + (delta < 0 ? '.up' : '.down'))?.focus();
    }

    moveTo(id, targetId, after) {
      const s = this.state;
      this.syncEditing();
      const i = s.blocks.findIndex((b) => b.id === id);
      if (i < 0) return;
      const [b] = s.blocks.splice(i, 1);
      let j = s.blocks.findIndex((x) => x.id === targetId);
      if (j < 0) j = s.blocks.length;
      s.blocks.splice(after ? j + 1 : j, 0, b);
      s.selected = id;
      this.changed(true);
    }

    remove(id) {
      const s = this.state;
      const b = this.block(id);
      if (!b) return;
      if (!confirm('¿Eliminar el bloque «' + (b.title || this.typeLabel(b.type)) + '»? Se quita de la página al guardar.')) return;
      this.syncEditing();
      s.blocks = s.blocks.filter((x) => x.id !== id);
      if (s.selected === id) s.selected = null;
      this.changed(true);
    }

    // ── text editing ───────────────────────────────────────────────────────────────────────────────

    selection() {
      return (this.root.getSelection && this.root.getSelection()) || document.getSelection();
    }

    keepRange() {
      const sel = this.selection();
      if (!sel || !sel.rangeCount) return;
      const range = sel.getRangeAt(0);
      let node = range.startContainer;
      while (node && !(node.classList && node.classList.contains('body'))) node = node.parentNode || node.host;
      if (node && this.root.contains(node)) this.range = { range: range.cloneRange(), body: node };
    }

    restoreRange(body) {
      body.focus();
      if (this.range && this.range.body === body) {
        const sel = this.selection();
        sel.removeAllRanges();
        sel.addRange(this.range.range);
      }
    }

    format(cmd, body, id) {
      if (!body) return;
      this.restoreRange(body);
      switch (cmd) {
        case 'link': {
          const url = prompt('Dirección del enlace (https://…, mailto:… o una ruta de la intranet como /intranet/buscar). Vacía: quita el enlace.');
          if (url === null) return;
          if (!url.trim()) document.execCommand('unlink');
          else if (!safeUrl(url)) { this.message('error', 'Ese enlace no vale: empieza por https://, mailto: o /'); return; }
          else document.execCommand('createLink', false, url.trim());
          break;
        }
        case 'h2': case 'h3': case 'p':
          document.execCommand('formatBlock', false, cmd.toUpperCase());
          break;
        case 'media':
          this.pickMedium((asset) => this.insertMedium(asset, body, id));
          return;
        default:
          document.execCommand(cmd);
      }
      this.block(id).html = this.fromEditable(body);
      this.dirty();
    }

    async insertMedium(asset, body, id) {
      try {
        if (!this.state.media[asset.value]) {
          const m = await this.call('GET', '/media/' + encodeURIComponent(asset.value));
          this.state.media[asset.value] = m.html;
        }
        this.restoreRange(body);
        const html = '<div class="ec-media" contenteditable="false" data-ec-media="' + esc(asset.value) + '">'
          + this.state.media[asset.value] + '</div><p><br></p>';
        if (!document.execCommand('insertHTML', false, html)) body.insertAdjacentHTML('beforeend', html);
        this.block(id).html = this.fromEditable(body);
        this.dirty();
      } catch (e) {
        this.message('error', e.message);
      }
    }

    // Pasting keeps the structure (paragraphs, lists, headings, emphasis, links) and drops the rest.
    paste(e) {
      e.preventDefault();
      const data = e.clipboardData;
      const html = data.getData('text/html');
      let clean;
      if (html) {
        const doc = new DOMParser().parseFromString(html, 'text/html');
        clean = this.cleanNodes(doc.body.childNodes);
      } else {
        clean = (data.getData('text/plain') || '').split(/\n{2,}/).map((p) => '<p>' + esc(p).replace(/\n/g, '<br>') + '</p>').join('');
      }
      document.execCommand('insertHTML', false, clean);
    }

    cleanNodes(nodes) {
      let out = '';
      nodes.forEach((n) => {
        if (n.nodeType === 3) { out += esc(n.textContent); return; }
        if (n.nodeType !== 1) return;
        let tag = n.tagName;
        if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'META' || tag === 'TITLE') return;
        if (tag === 'H1') tag = 'H2';
        if (tag === 'H5' || tag === 'H6') tag = 'H4';
        if (tag === 'DIV' || tag === 'SECTION' || tag === 'ARTICLE') tag = 'P';
        const inner = this.cleanNodes(n.childNodes);
        if (!PASTE_TAGS.has(tag)) { out += inner; return; }
        const t = tag.toLowerCase();
        if (t === 'br') { out += '<br>'; return; }
        if (t === 'a') {
          const href = n.getAttribute('href') || '';
          out += safeUrl(href) ? '<a href="' + esc(href) + '">' + inner + '</a>' : inner;
          return;
        }
        out += '<' + t + '>' + inner + '</' + t + '>';
      });
      return out;
    }

    pickMedium(chosen) {
      const media = (this.state.catalog && this.state.catalog.media) || [];
      const modal = document.createElement('div');
      modal.className = 'modal';
      modal.innerHTML = '<div class="dialog" role="dialog" aria-modal="true" aria-label="Insertar un medio">'
        + '<div style="display:flex;gap:.5rem;align-items:center"><b style="flex:1">Insertar un medio de la biblioteca</b>'
        + '<button class="close" aria-label="Cerrar">✕</button></div>'
        + '<input type="search" class="q" placeholder="Buscar por título…" style="margin-top:.5rem">'
        + '<div class="grid"></div>'
        + (media.length ? '' : '<p class="note">La biblioteca está vacía: sube medios en Editorial → Medios.</p>') + '</div>';
      const grid = modal.querySelector('.grid');
      const icon = { pdf: '📄', video: '🎬', audio: '🎵', external: '▶', image: '🖼' };
      const draw = (q) => {
        grid.innerHTML = media.filter((m) => !q || m.label.toLowerCase().includes(q.toLowerCase())).map((m) =>
          '<button data-id="' + esc(m.value) + '">' + (m.thumb ? '<img src="' + esc(m.thumb) + '" alt="">' : '<div class="ph">'
          + (icon[m.kind] || '📎') + '</div>') + '<span>' + esc(m.label) + ' · ' + esc(m.kind) + '</span></button>').join('');
        grid.querySelectorAll('button').forEach((btn) => btn.addEventListener('click', () => {
          close();
          chosen(media.find((m) => m.value === btn.dataset.id));
        }));
      };
      const close = () => modal.remove();
      modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
      modal.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
      modal.querySelector('.close').addEventListener('click', close);
      modal.querySelector('.q').addEventListener('input', (e) => draw(e.target.value));
      draw('');
      this.root.appendChild(modal);
      modal.querySelector('.q').focus();
    }

    // ── the side panel ─────────────────────────────────────────────────────────────────────────────

    renderPanel() {
      const panel = this.root.querySelector('.panel');
      if (!panel) return;
      const scroll = panel.scrollTop;
      requestAnimationFrame(() => { panel.scrollTop = scroll; });
      const s = this.state;
      const b = this.editing && s.selected ? this.block(s.selected) : null;
      if (!b) {
        panel.innerHTML = '<h3>Bloques</h3><p class="note">' + (this.editing
          ? 'Haz clic en un bloque para editarlo: el texto de los bloques HTML y Banner se escribe sobre la página; aquí, su tipo, '
            + 'sus campos y a quién se muestra. «+» entre bloques añade uno; ⠿ arrastra, ↑ ↓ mueven.'
          : 'Estás viendo la página como un perfil: vuelve a «yo, editando» para cambiarla.') + '</p>'
          + '<p class="note">Guardar cambia el <b>borrador</b>. La revisión y la publicación siguen en el formulario '
          + '(botón «Volver al formulario», arriba).</p>';
        return;
      }
      const cat = s.catalog;
      const type = cat.types.find((t) => t.value === b.type) || { fields: [] };
      const f = new Set(type.fields);
      const opt = (list, value, empty) => (empty !== undefined ? '<option value="">' + esc(empty) + '</option>' : '')
        + list.map((o) => '<option value="' + esc(o.value) + '"' + (o.value === value ? ' selected' : '') + '>' + esc(o.label) + '</option>').join('');
      const r = s.rendered[b.id];
      const p = s.page;
      let html = '<h3>' + esc(this.typeLabel(b.type)) + (b.title ? ' · ' + esc(b.title) : '') + '</h3>'
        + '<div class="field"><label for="f-type">Tipo</label><select id="f-type" class="f-type">' + opt(cat.types, b.type) + '</select></div>'
        + '<div class="field"><label for="f-title">Título</label><input id="f-title" type="text" class="f-title" value="' + esc(b.title) + '"></div>';
      if (f.has('html')) html += '<p class="note">✍️ El texto se escribe sobre la página, con la barra de formato del bloque.</p>';
      if (f.has('itemId')) {
        html += '<div class="field"><label for="f-item">Item destacado</label><select id="f-item" class="f-item">'
          + opt(cat.items, b.itemId, '— elige un item publicado —') + '</select></div>';
      }
      if (f.has('sectionId')) {
        html += '<div class="field"><label for="f-section">Sección listada</label><select id="f-section" class="f-section">'
          + opt(cat.sections, b.sectionId, '(todas)') + '</select></div>';
      }
      if (f.has('limit')) {
        html += '<div class="field"><label for="f-limit">Cuántos</label><input id="f-limit" type="number" min="1" max="50" class="f-limit" value="'
          + esc(b.limit == null ? '' : b.limit) + '"></div>';
      }
      if (f.has('mediaId')) {
        const m = cat.media.find((x) => x.value === b.mediaId);
        html += '<div class="field"><label for="f-media">Medio de la biblioteca</label><select id="f-media" class="f-media">'
          + opt(cat.media, b.mediaId, '— elige un medio —') + '</select>'
          + (m && m.thumb ? '<img src="' + esc(m.thumb) + '" alt="" style="width:100%;max-height:120px;object-fit:cover;border-radius:6px;margin-top:.35rem">' : '')
          + '<button class="pick" style="margin-top:.35rem">Elegir en la biblioteca…</button></div>';
      }
      if (f.has('remoteSourceId')) {
        html += '<div class="field"><label for="f-remote">UI externa</label><select id="f-remote" class="f-remote">'
          + opt(cat.uiSources || [], b.remoteSourceId, '— elige una UI registrada —') + '</select>'
          + '<p class="note">Las registra la configuración de la intranet. En el portal se ve en vivo, con los permisos de cada lector.</p></div>';
      }
      if (f.has('remoteRoute')) {
        html += '<div class="field"><label for="f-route">Ruta (UI de Mateu o iframe)</label><input id="f-route" type="text" class="f-route"'
          + ' placeholder="/loyalty/members" value="' + esc(b.remoteRoute) + '"></div>';
      }
      if (f.has('remoteAttributes')) {
        html += '<div class="field"><label for="f-attrs">Atributos (web component: nombre=valor, uno por línea)</label>'
          + '<textarea id="f-attrs" class="f-attrs" rows="3">' + esc(b.remoteAttributes) + '</textarea></div>';
      }
      if (f.has('links')) {
        html += '<div class="field"><label>Enlaces</label><table class="links"><tbody>' + b.links.map((l, i) =>
          '<tr><td><input type="text" aria-label="Texto del enlace ' + (i + 1) + '" placeholder="Texto" data-l="' + i + '" data-k="label" value="' + esc(l.label) + '"></td>'
          + '<td><input type="text" aria-label="URL del enlace ' + (i + 1) + '" placeholder="/intranet/… o https://…" data-l="' + i + '" data-k="url" value="' + esc(l.url) + '"></td>'
          + '<td><button class="rmlink" data-l="' + i + '" aria-label="Quitar el enlace ' + (i + 1) + '">✕</button></td></tr>').join('')
          + '</tbody></table><button class="addlink" style="margin-top:.3rem">+ Enlace</button>'
          + '<p class="note">Una ruta de la intranet (/intranet/buscar) se abre en la intranet; una dirección completa, en otra pestaña.</p></div>';
      }
      html += '<h4>Quién lo ve</h4>'
        + '<div class="aud">La página la ven: <b>' + esc(p.audience) + '</b> — ' + p.count + ' empleado(s).</div>'
        + '<div class="note">Este bloque, además, solo para… (vacío: toda la audiencia de la página)</div>'
        + this.checks('Departamentos', 'departments', cat.departments, b.departments)
        + this.checks('Hoteles', 'hotels', cat.hotels, b.hotels)
        + this.checks('Puestos', 'jobTitles', cat.jobTitles, b.jobTitles)
        + '<div class="aud">' + (this.narrowing(b) ? 'Este bloque, además, solo: <b>' + esc(this.narrowing(b)) + '</b>.<br>' : 'Sin restricción propia.<br>')
        + '<span class="aud-count">' + this.countText(r) + '</span></div>'
        + '<div class="row"><button class="pup">↑ Subir</button><button class="pdown">↓ Bajar</button>'
        + '<button class="pdel danger">Eliminar el bloque</button></div>';
      panel.innerHTML = html;
      const on = (sel, ev, fn) => panel.querySelector(sel)?.addEventListener(ev, fn);
      on('.f-type', 'change', (e) => {
        this.syncEditing();
        b.type = e.target.value;
        if (LIMITED.has(b.type) && b.limit == null) b.limit = 5;
        this.changed(true);
      });
      on('.f-title', 'input', (e) => {
        b.title = e.target.value;
        const ttl = this.root.querySelector('.blk[data-id="' + b.id + '"] .ttl');
        if (ttl) ttl.textContent = b.title;
        this.dirty();
        this.translated(b.id);
        if (!RICH.has(b.type)) this.refresh();
      });
      on('.f-item', 'change', (e) => { b.itemId = e.target.value; this.changed(); });
      on('.f-section', 'change', (e) => { b.sectionId = e.target.value; this.changed(); });
      on('.f-limit', 'change', (e) => { b.limit = e.target.value === '' ? null : Math.max(1, Math.min(50, Number(e.target.value))); this.changed(); });
      on('.f-media', 'change', (e) => { b.mediaId = e.target.value; this.changed(); });
      on('.f-remote', 'change', (e) => { b.remoteSourceId = e.target.value; this.changed(); });
      // typed: the model and the preview follow; the panel is not redrawn (see the links, below)
      on('.f-route', 'input', (e) => { b.remoteRoute = e.target.value; this.dirty(); this.refresh(); });
      on('.f-attrs', 'input', (e) => { b.remoteAttributes = e.target.value; this.dirty(); this.refresh(); });
      on('.pick', 'click', () => this.pickMedium((m) => { b.mediaId = m.value; this.changed(); }));
      // as the editor types: the model and the preview follow, the panel is NOT redrawn — redrawing it
      // swapped the inputs under the cursor, and what was typed next went to a detached one
      panel.querySelectorAll('.links input').forEach((input) => input.addEventListener('input', () => {
        b.links[Number(input.dataset.l)][input.dataset.k] = input.value;
        this.dirty();
        if (input.dataset.k === 'label') this.translated(b.id);
        this.refresh();
      }));
      panel.querySelectorAll('.rmlink').forEach((btn) => btn.addEventListener('click', () => {
        b.links.splice(Number(btn.dataset.l), 1);
        this.changed();
      }));
      on('.addlink', 'click', () => { b.links.push({ label: '', url: '' }); this.renderPanel(); this.dirty(); });
      panel.querySelectorAll('.checks input').forEach((input) => input.addEventListener('change', () => {
        const list = b[input.dataset.attr];
        const i = list.indexOf(input.value);
        if (input.checked && i < 0) list.push(input.value);
        if (!input.checked && i >= 0) list.splice(i, 1);
        this.changed(true);
      }));
      on('.pup', 'click', () => this.move(b.id, -1));
      on('.pdown', 'click', () => this.move(b.id, 1));
      on('.pdel', 'click', () => this.remove(b.id));
    }

    countText(r) {
      return r ? 'Lo ven <b>' + r.count + '</b> empleado(s)' + (r.count === 0 ? ' ⚠️ nadie cumple a la vez la audiencia de la página y la del bloque' : '') + '.'
        : 'Calculando…';
    }

    // After a preview: only the head count changes in the panel (whatever is being typed there stays).
    patchPanel() {
      const s = this.state;
      const count = this.root.querySelector('.panel .aud-count');
      if (count && s.selected) count.innerHTML = this.countText(s.rendered[s.selected]);
      if (!this.editing) this.renderPanel();
    }

    checks(label, attr, options, chosen) {
      return '<div class="field"><label>' + esc(label) + '</label><div class="checks" role="group" aria-label="' + esc(label) + '">'
        + (options.length ? options.map((o) => '<label class="' + (chosen.includes(o.value) ? 'on' : '') + '"><input type="checkbox" data-attr="'
          + attr + '" value="' + esc(o.value) + '"' + (chosen.includes(o.value) ? ' checked' : '') + '>' + esc(o.label) + '</label>').join('')
          : '<span class="note">El directorio no tiene valores</span>') + '</div></div>';
    }

    // ── leaving with unsaved changes ───────────────────────────────────────────────────────────────

    // A click outside the editor on something that navigates (the menu, a toolbar action, a link) asks first.
    guardLeave(e) {
      if (!this.state.dirty) return;
      const path = e.composedPath();
      if (path.includes(this)) return;
      const nav = path.find((n) => n instanceof Element && (n.tagName === 'A' || n.tagName === 'BUTTON'
        || n.getAttribute('role') === 'menuitem' || n.getAttribute('role') === 'link' || n.getAttribute('role') === 'button'
        || /^(VAADIN-(BUTTON|SIDE-NAV-ITEM|MENU-BAR-ITEM|TAB|ITEM)|OJ-(C-)?BUTTON|OJ-NAVIGATION-LIST|OJ-SP-.*|OJ-C-.*)$/.test(n.tagName)));
      if (!nav) return;
      if (!confirm('Hay cambios sin guardar en el editor visual. ¿Salir sin guardarlos?')) {
        e.preventDefault();
        e.stopImmediatePropagation();
      } else {
        this.state.dirty = false;
      }
    }
  }

  customElements.define('ec-visual-editor', VisualEditor);
})();
