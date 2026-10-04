// This console's colours in Redwood, so it does not look like the other consoles: the top bar in
// the front-office colour and our own colour strip under the page header, in its palette (Redwood's product
// lines each have a strip of their own; those images are Oracle's, so the strip here is drawn for
// ec-demo1). Vaadin takes the same colour from @App(accentColor); these selectors exist only in
// Redwood's shell, so in Vaadin this does nothing.
(() => {
  if (document.querySelector('style[data-ec-theme]')) return;
  const style = document.createElement('style');
  style.dataset.ecTheme = 'front-office';
  style.textContent = `
    #shell_SUISGlobalHeader,
    #shell_SUISGlobalHeader oj-sp-global-header > div { background-color: #04536f !important; }
    .oj-sp-header-general-overview-header-strip {
      background-image: url('/images/strip-front-office.svg') !important;
      background-size: 720px 12px !important;
      background-repeat: repeat-x !important;
    }`;
  document.head.appendChild(style);
})();
