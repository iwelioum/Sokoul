# Sokoul — Module Streaming

**Version :** 1.0 — Février 2026
**Stack :** Rust (Axum) · SvelteKit · HLS.js · Consumet API

---

## Objectif

Lire films et séries directement dans `CustomPlayer.svelte` (lecteur HLS.js natif) sans iframe externe.

---

## Architecture

```
Utilisateur clique "Regarder"
        │
        ▼
[SvelteKit — watch/+page.svelte]
  → GET /api/streaming/consumet/:media_type/:tmdb_id
        │
        ▼
[Rust — src/api/streaming.rs — resolve_consumet_handler]
  → src/clients/consumet.rs
        │
        ├─ GET /meta/tmdb/info/:tmdb_id?type=movie&provider=HiMovies
        │   → récupère l'episodeId du provider
        │
        └─ GET /meta/tmdb/watch?episodeId=...&id=...&provider=HiMovies
            → sources HLS + headers Referer
        │
        ▼
[Réponse JSON]
  { sources: [{ url, quality, headers }], subtitles: [...] }
        │
        ▼
[CustomPlayer.svelte — HLS.js]
  Si headers.Referer → proxy via /api/streaming/proxy?url=...&referer=...
  Sinon → URL directe
```

---

## Fichiers clés

### Backend Rust

| Fichier | Rôle |
|---------|------|
| `src/clients/consumet.rs` | Client HTTP Consumet — `resolve_movie()`, `resolve_episode()` |
| `src/api/streaming.rs` | Handler API streaming + proxy m3u8 |
| `src/models.rs` | `StreamSource`, `ExtractedStream` (avec champ `headers`) |

### Frontend

| Fichier | Rôle |
|---------|------|
| `dashboard/src/lib/components/CustomPlayer.svelte` | Lecteur HLS.js + fallback sources |
| `dashboard/src/lib/api/client.ts` | `resolveConsumetStreams()`, `getProxyUrl()` |
| `dashboard/src/routes/watch/[media_type]/[tmdb_id]/+page.svelte` | Page de lecture |

---

## Consumet API

Instance locale lancée manuellement sur le port 3002.

```bash
cd /path/to/consumet
NODE_TLS_REJECT_UNAUTHORIZED=0 \
  TMDB_KEY=your_tmdb_key \
  PORT=3002 \
  node -r ts-node/register src/main.ts
```

### Endpoints utilisés

```
GET /meta/tmdb/info/:tmdb_id?type=movie&provider=HiMovies
GET /meta/tmdb/watch?episodeId=:id&id=:media_id&provider=HiMovies
GET /movies/himovies/servers?episodeId=:id&mediaId=:media_id
GET /movies/himovies/info?id=:media_id
```

### État des providers (Février 2026)

| Provider | Recherche | Serveurs | Extraction sources | Statut |
|----------|-----------|----------|--------------------|--------|
| **HiMovies** | ✅ | ✅ (`/servers`) | ❌ MegaCloud cassé | Bloqué extracteur |
| **Goku** | ✅ | ✅ | ❌ Cookies requis | Inutilisable sans browser |
| **FlixHQ** | ❌ | — | — | Scraping bloqué |
| **SFlix** | ❌ | — | — | Scraping bloqué |

**Blocage racine :** Le service `crawlr.cc` utilisé par les extracteurs `MegaCloud` et `VideoStr` de Consumet est hors service. Ces extracteurs retournent systématiquement des sources vides.

---

## Proxy m3u8

Le backend expose un proxy pour contourner les vérifications Referer/Origin des CDN de streaming.

```
GET /streaming/proxy?url=<encoded_url>&referer=<encoded_referer>
```

Le backend ajoute les headers `Referer` et `Origin` avant de relayer la requête.

---

## Propagation des headers

Les headers Referer/Origin transitent du provider jusqu'au player :

```
ConsometWatchResponse.headers
    → StreamSource.headers (Option<HashMap<String, String>>)
    → ExtractedStream.headers
    → JSON response → CustomPlayer.svelte
    → si headers présents → getProxyUrl(url, referer)
```

---

## Prochaines étapes

1. **Remplacer l'extracteur MegaCloud/VideoStr cassé** — implémenter l'extraction directe sans crawlr.cc, ou utiliser Puppeteer/Playwright pour exécuter le JS des embed players
2. **Tester d'autres providers** — chercher un provider Consumet avec un extracteur fonctionnel
3. **Réduire le TTL Redis** — les URLs de stream expirent rapidement (actuellement 3600s)
4. **Améliorer le fallback player** — afficher un message clair quand aucune source n'est disponible
