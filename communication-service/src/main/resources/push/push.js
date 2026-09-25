// Web Push for the consoles: loaded by every shell (@Script), served by communication-service.
//
// Waits for the Keycloak token the bootstrap page keeps in localStorage, registers the service
// worker, and — once the person allowed notifications — tells the inbox where this browser is. It
// does so on every load, so the roles it receives for are always the current ones. The permission
// is only asked after a click: browsers ignore, or block, a request nobody asked for.
const BASE = '/_inbox/push';
const DISMISSED = '__ec_push_dismissed';

function token() {
  return localStorage.getItem('__mateu_auth_token');
}

async function waitForToken(ms) {
  const until = Date.now() + ms;
  while (!token() && Date.now() < until) {
    await new Promise(r => setTimeout(r, 500));
  }
  return token();
}

function urlBase64ToUint8Array(base64) {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  const raw = atob((base64 + padding).replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from([...raw].map(c => c.charCodeAt(0)));
}

async function subscribe(registration) {
  const auth = { Authorization: 'Bearer ' + token() };
  const answer = await fetch(BASE + '/public-key', { headers: auth });
  if (!answer.ok) return; // Web Push is not configured on this deployment
  const { publicKey } = await answer.json();
  let subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    });
  }
  await fetch(BASE + '/subscriptions', {
    method: 'POST',
    headers: { ...auth, 'Content-Type': 'application/json' },
    body: JSON.stringify(subscription.toJSON()),
  });
}

function offer(registration) {
  if (localStorage.getItem(DISMISSED)) return;
  const bar = document.createElement('div');
  bar.style.cssText = 'position:fixed;left:16px;bottom:16px;z-index:10000;display:flex;gap:8px;align-items:center;'
    + 'padding:8px 12px;border-radius:8px;background:#1f2937;color:#fff;font:14px system-ui,sans-serif;'
    + 'box-shadow:0 2px 8px rgba(0,0,0,.25)';
  const enable = document.createElement('button');
  enable.textContent = '🔔 Enable notifications';
  enable.style.cssText = 'border:0;border-radius:6px;padding:6px 10px;background:#2563eb;color:#fff;cursor:pointer;font:inherit';
  const later = document.createElement('button');
  later.textContent = '✕';
  later.title = 'Not now';
  later.style.cssText = 'border:0;background:transparent;color:#cbd5e1;cursor:pointer;font:inherit';
  enable.onclick = async () => {
    bar.remove();
    if (await Notification.requestPermission() === 'granted') {
      await subscribe(registration);
    }
  };
  later.onclick = () => {
    localStorage.setItem(DISMISSED, '1');
    bar.remove();
  };
  bar.append(enable, later);
  document.body.appendChild(bar);
}

(async () => {
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) return;
  if (!await waitForToken(60000)) return;
  try {
    const registration = await navigator.serviceWorker.register(BASE + '/sw.js');
    await navigator.serviceWorker.ready;
    if (Notification.permission === 'granted') {
      await subscribe(registration);
    } else if (Notification.permission === 'default') {
      offer(registration);
    }
  } catch (e) {
    console.log('Web Push not available', e);
  }
})();
