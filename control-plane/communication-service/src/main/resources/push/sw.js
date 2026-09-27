// The consoles' service worker: shows what the inbox pushes, and a click opens the screen that
// resolves it — in the console tab already open on that page if there is one.
self.addEventListener('push', event => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = { title: 'Inbox', body: event.data ? event.data.text() : '' };
  }
  event.waitUntil(self.registration.showNotification(data.title || 'Inbox', {
    body: data.body || '',
    tag: data.tag,
    icon: '/images/riu.svg',
    requireInteraction: !!data.urgent,
    data: { url: data.url || '/' },
  }));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const url = new URL(event.notification.data && event.notification.data.url || '/', self.location.origin).href;
  event.waitUntil((async () => {
    const windows = await clients.matchAll({ type: 'window', includeUncontrolled: true });
    const open = windows.find(w => w.url === url);
    if (open) return open.focus();
    return clients.openWindow(url);
  })());
});
