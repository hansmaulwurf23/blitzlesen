// Offline-Betrieb: alles wird beim Installieren vorgeladen. Danach erst das Netz fragen (damit neue
// Versionen und Wortlisten sofort ankommen), ohne Netz oder bei langsamer Antwort den Cache.

/** Beim Veröffentlichen durch den Commit ersetzt; ein neuer Wert gibt einen frischen Cache. */
const VERSION = '__VERSION__';
const CACHE = `lesen-${VERSION}`;
const NETWORK_TIMEOUT_MS = 3000;

const SHELL = [
  './',
  'app.css',
  'app.js',
  'lesen.js',
  'manifest.webmanifest',
  'icons/favicon.svg',
  'icons/icon.svg',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/apple-touch-icon.png',
  'listen/index.json',
];

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    await cache.addAll(SHELL);
    // Die Wortlisten stehen im Index; ohne sie geht offline nichts.
    const index = await (await cache.match('listen/index.json')).json();
    await cache.addAll(index.lists.map((id) => `listen/${id}.json`));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) {
      if (key.startsWith('lesen-') && key !== CACHE) await caches.delete(key);
    }
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET' || new URL(request.url).origin !== location.origin) return;
  event.respondWith(networkFirst(request, event));
});

async function networkFirst(request, event) {
  const cache = await caches.open(CACHE);
  // no-cache: beim Server nachfragen statt dem HTTP-Cache zu glauben (GitHub Pages: 10 Minuten),
  // sonst erscheint eine neue Wortliste erst später. Unverändert kostet das nur ein 304.
  const network = fetch(request.url, { cache: 'no-cache', credentials: 'same-origin' }).then((response) => {
    if (response.ok) {
      event.waitUntil(cache.put(request, response.clone()));
    }
    return response;
  });
  const cached = cache.match(request, { ignoreSearch: true })
    .then((hit) => hit || (request.mode === 'navigate' ? cache.match('./') : undefined));

  // Wer zuerst eine brauchbare Antwort hat: das Netz, oder nach ein paar Sekunden der Cache.
  const timeout = new Promise((resolve) => setTimeout(resolve, NETWORK_TIMEOUT_MS));
  const fallback = timeout.then(() => cached).then((hit) => hit || network);
  try {
    return await Promise.race([network, fallback]);
  } catch {
    const hit = await cached;
    if (hit) return hit;
    throw new Error(`Offline und nicht im Cache: ${request.url}`);
  }
}
