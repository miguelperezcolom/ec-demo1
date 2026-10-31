// content-hub's embeds: Mateu's sanitiser drops <iframe>, so content pages carry placeholders —
// <div data-ec-embed="URL" data-ec-embed-kind="video|pdf"> holding a link (and a thumbnail) — and this
// turns each into the player, after the page is drawn. Only for the hosts below and for content-hub's
// own signed media; anything else keeps its link. Loaded by the shells (@Script), like push.js; walks
// the shadow roots, where Mateu draws.
(() => {
  if (window.__ecEmbed) return;
  window.__ecEmbed = true;
  const HOSTS = ['www.youtube-nocookie.com', 'player.vimeo.com', 'drive.google.com'];
  const OWN = /^\/_(intranet|content-hub)\/media\//;

  const allowed = (src) => {
    try {
      const u = new URL(src, location.href);
      if (u.origin === location.origin) return OWN.test(u.pathname);
      return u.protocol === 'https:' && HOSTS.includes(u.hostname);
    } catch (e) {
      return false;
    }
  };

  const upgrade = (el) => {
    if (el.dataset.ecEmbedDone) return;
    el.dataset.ecEmbedDone = '1';
    const src = el.getAttribute('data-ec-embed');
    if (!src || !allowed(src)) return;
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

  const eachDeep = (root, fn) => {
    root.querySelectorAll('[data-ec-embed]').forEach(fn);
    root.querySelectorAll('*').forEach((node) => {
      if (node.shadowRoot) eachDeep(node.shadowRoot, fn);
    });
  };

  let pending = false;
  const scan = () => {
    pending = false;
    eachDeep(document, upgrade);
  };
  new MutationObserver(() => {
    if (!pending) {
      pending = true;
      requestAnimationFrame(scan);
    }
  }).observe(document.documentElement, { childList: true, subtree: true });
  setInterval(scan, 1500); // shadow roots the observer does not see into
  scan();
})();
