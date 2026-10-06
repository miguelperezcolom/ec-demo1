// This console's colours in Redwood, so it does not look like the other consoles: the top bar in
// the control-plane colour, and the home's welcome banner in the same colour. The strip under the page
// header is Redwood's own (Spectra). Vaadin takes the colour from @App(accentColor), and draws its strip
// from it; these selectors exist only in Redwood's shell, so in Vaadin this does nothing.
(() => {
  if (document.querySelector('style[data-ec-theme]')) return;
  const style = document.createElement('style');
  style.dataset.ecTheme = 'control-plane';
  style.textContent = `
    #shell_SUISGlobalHeader,
    #shell_SUISGlobalHeader oj-sp-global-header > div { background-color: #464c68 !important; }
    /* the home's welcome banner (oj-sp-header-welcome-banner): Redwood paints it in one of its own
       colours, set inline; ours instead, like the top bar */
    .oj-sp-header-welcome-banner-container {
      --oj-sp-header-welcome-banner-background-color: #464c68 !important;
      background-color: #464c68 !important;
    }`;
  document.head.appendChild(style);
})();
