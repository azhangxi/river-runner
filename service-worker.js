importScripts("./precache.js");

const scope = self.registration.scope;
const cachePrefix = `river-runner:${scope}:`;
const cacheName = cachePrefix + self.riverRunnerPrecache.version;
const assets = new Set(self.riverRunnerPrecache.files.map((file) => new URL(file, scope).href));
const index = new URL("./index.html", scope).href;

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(cacheName);
    await cache.addAll([...assets].map((url) => new Request(url, { cache: "reload" })));
  })());
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    const oldCaches = (await caches.keys()).filter((name) => {
      return name.startsWith(cachePrefix) && name !== cacheName;
    });
    await Promise.all(oldCaches.map((name) => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET" || !request.url.startsWith(scope)) return;

  const url = new URL(request.url);
  url.search = "";
  const isEntry = request.mode === "navigate" && (url.href === scope || url.href === index);
  const asset = isEntry ? index : url.href;
  if (!assets.has(asset)) return;

  event.respondWith((async () => {
    const cache = await caches.open(cacheName);
    return (await cache.match(asset)) ?? fetch(request);
  })());
});
