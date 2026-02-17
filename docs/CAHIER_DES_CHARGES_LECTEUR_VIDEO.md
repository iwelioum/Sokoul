# Cahier des Charges — Lecteur Vidéo Custom Sokoul

## 1. Objectif

Lire du contenu vidéo (films & séries) **directement dans le lecteur natif de Sokoul** (CustomPlayer) avec nos propres contrôles (play/pause, barre de progression, volume, qualité, piste audio, sous-titres, vitesse, plein écran), au lieu d'utiliser des iframes de lecteurs tiers.

---

## 2. Le Problème Actuel

### 2.1 Pourquoi c'est complexe

Les sites d'embed (VidSrc, AutoEmbed, Embed.su, etc.) ne fournissent **pas d'API publique** pour obtenir l'URL directe du flux vidéo (m3u8/mp4). Ils :

1. **Chargent une page HTML** avec du JavaScript obfusqué
2. **Le JS déchiffre/construit** l'URL du flux vidéo côté client
3. **Le player JS intégré** (souvent JWPlayer ou un player custom) charge le flux
4. **Les URLs sont protégées** par des tokens temporaires, des vérifications de Referer/Origin, et parfois du DRM

→ On ne peut pas simplement faire un `GET` sur la page et parser le HTML pour trouver l'URL.

### 2.2 Ce qui a été tenté

| Approche | Résultat |
|---|---|
| **Extracteurs HTTP** (regex sur HTML) | ❌ Échoue car les URLs sont construites en JS, pas dans le HTML |
| **MoviesAPI** (API tierce) | ⚠️ Fonctionne parfois, mais instable et limité |
| **Playwright (headless browser)** | ⚠️ Lourd (~300 Mo de Chromium), lent (12s timeout), et les providers détectent souvent les headless browsers |

### 2.3 Schéma du problème

```
L'UTILISATEUR clique sur "Regarder"
       │
       ▼
Frontend appelle  POST /api/streaming/extract/movie/12345
       │
       ▼
Backend lance Playwright (Chromium headless)
       │
       ▼
Chromium navigue vers https://vidsrc.cc/v2/embed/movie/12345
       │
       ▼
Intercepte les requêtes réseau pendant 12 secondes
       │
       ▼
Cherche des URLs contenant .m3u8 ou .mp4
       │                                        │
       ▼                                        ▼
   TROUVÉ → renvoie l'URL             PAS TROUVÉ → fallback iframe
       │
       ▼
Frontend charge l'URL dans <video> + HLS.js
       │
       ▼
Problème : le serveur du provider REFUSE la requête
car le Referer/Origin ne vient pas de son domaine
       │
       ▼
Solution : passer par notre proxy /api/streaming/proxy
qui ajoute les bons headers (Referer, Origin)
       │
       ▼
Le proxy réécrit aussi les URLs dans les playlists m3u8
pour que les segments .ts passent aussi par le proxy
```

---

## 3. Architecture Actuelle (ce qui existe déjà)

### 3.1 Backend (Rust / Axum)

| Fichier | Rôle |
|---|---|
| `src/extractors/mod.rs` | Trait `StreamExtractor` + types `ExtractedStream`, `SubtitleTrack`, `ExtractionResult` |
| `src/extractors/headless.rs` | `HeadlessExtractor` — Playwright qui intercepte les requêtes réseau |
| `src/extractors/moviesapi.rs` | Extracteur HTTP pour MoviesAPI (API gratuite) |
| `src/extractors/autoembed.rs` | Extracteur HTTP pour AutoEmbed (⚠️ non utilisé, code mort) |
| `src/extractors/embed_su.rs` | Extracteur HTTP pour Embed.su (⚠️ non utilisé, code mort) |
| `src/extractors/vidsrc.rs` | Extracteur HTTP pour VidSrc (⚠️ non utilisé, code mort) |
| `src/extractors/registry.rs` | `ExtractorRegistry` — exécute tous les extracteurs, trie par priorité FR |
| `src/api/streaming.rs` | Routes API : `/streaming/extract/`, `/streaming/direct/`, `/streaming/proxy` |
| `src/clients/subtitles.rs` | Client SubDL pour les sous-titres |

**Routes API existantes :**

```
GET  /api/streaming/direct/{media_type}/{tmdb_id}     → liens embed (iframes)
GET  /api/streaming/extract/{media_type}/{tmdb_id}    → extraction directe (Playwright)
GET  /api/streaming/subtitles/{media_type}/{tmdb_id}  → sous-titres (SubDL)
GET  /api/streaming/proxy?url=...&referer=...         → proxy CORS pour m3u8/ts
```

### 3.2 Frontend (Svelte 5)

| Fichier | Rôle |
|---|---|
| `dashboard/src/lib/components/CustomPlayer.svelte` | Lecteur vidéo complet (~800 lignes) |
| `dashboard/src/routes/watch/[media_type]/[tmdb_id]/+page.svelte` | Page de lecture |
| `dashboard/src/lib/api/client.ts` | Fonctions `extractStreams()`, `getDirectStreamLinks()`, `getSubtitles()`, `getProxyUrl()` |

**Fonctionnalités du CustomPlayer :**
- ✅ Lecture HLS via hls.js + lecture MP4 native
- ✅ Barre de progression avec buffering visible
- ✅ Contrôles play/pause, skip ±10s, volume, mute
- ✅ Sélection qualité (auto + niveaux détectés)
- ✅ Sélection piste audio (FR auto-détecté)
- ✅ Sous-titres (FR auto-activé si pas d'audio FR)
- ✅ Vitesse de lecture (0.25x à 2x)
- ✅ Plein écran
- ✅ Raccourcis clavier (espace, flèches, M, F, K)
- ✅ Sauvegarde progression toutes les 15s
- ✅ Changement de source (natif ↔ iframe fallback)
- ✅ Auto-fallback vers iframe si extraction échoue

### 3.3 Dépendances

```toml
# Backend (Cargo.toml)
playwright = "0.0.20"    # ~300 Mo de Chromium à télécharger

# Frontend (package.json)
hls.js                   # Lecteur HLS pour navigateur
```

---

## 4. Les Vrais Obstacles

### 4.1 Playwright est trop lourd et peu fiable

| Problème | Détail |
|---|---|
| **Taille** | Chromium = ~300 Mo. Inacceptable pour un serveur léger |
| **Performance** | 12s de timeout par provider × 7 providers = jusqu'à 84s d'attente |
| **Détection** | Les providers détectent les navigateurs headless (fingerprinting) |
| **Mémoire** | Chromium consomme ~200 Mo de RAM par instance |
| **Séquentiel** | Les extracteurs tournent l'un après l'autre (pas en parallèle, car le browser est partagé) |
| **Instabilité** | Les providers changent leur code régulièrement → les extracteurs cassent |

### 4.2 CORS & Protection des providers

Même quand on obtient l'URL m3u8, le provider peut :
- Refuser les requêtes sans le bon `Referer`
- Utiliser des tokens qui expirent après quelques minutes
- Servir les segments `.ts` depuis un CDN différent avec ses propres protections

→ Le proxy `streaming/proxy` existe pour contourner ça, mais ajoute de la latence.

### 4.3 Les extracteurs HTTP sont inefficaces

Les extracteurs `autoembed.rs`, `embed_su.rs`, `vidsrc.rs` font du parsing HTML/regex mais ne fonctionnent **pas** car les URLs vidéo sont construites dynamiquement en JavaScript, pas présentes dans le HTML initial.

---

## 5. Solutions Possibles (à explorer)

### 5.1 ⭐ Option A : API d'extraction tierces (Recommandé)

Utiliser des **API gratuites qui font déjà l'extraction** et renvoient des URLs m3u8 directes.

**Exemples :**

| API | Description | Avantage |
|---|---|---|
| **VidSrc API v2** | Certains providers ont des endpoints JSON cachés | Rapide, pas de browser |
| **2embed API** | API REST qui renvoie des sources | Léger |
| **Cobalt** (self-hosted) | Outil open-source d'extraction vidéo | Contrôle total, supporte beaucoup de sites |
| **yt-dlp** (self-hosted) | Extracteur vidéo en ligne de commande | Supporte 1000+ sites, communauté très active |

**Comment intégrer yt-dlp :**
```
# Installer yt-dlp
pip install yt-dlp

# Extraire l'URL directe d'un embed
yt-dlp --get-url "https://vidsrc.cc/v2/embed/movie/12345"

# Obtenir toutes les infos en JSON
yt-dlp -j "https://vidsrc.cc/v2/embed/movie/12345"
```

→ Le backend lance `yt-dlp -j <url>` en subprocess, parse le JSON, et renvoie l'URL m3u8 au frontend.

**Avantages :**
- Pas besoin de Playwright/Chromium (économie de ~300 Mo)
- yt-dlp est maintenu par une communauté très active
- Supporte les cookies, les proxies, les user-agents
- Extraction rapide (quelques secondes)

**Inconvénients :**
- Nécessite Python installé sur le serveur
- yt-dlp ne supporte pas forcément tous les providers d'embed

### 5.2 Option B : Reverse-engineering des API cachées des providers

Certains providers ont des **API internes non documentées** que leur player JS appelle. On peut les reverse-engineerer :

```
1. Ouvrir DevTools → onglet Network
2. Charger la page embed du provider
3. Filtrer par ".m3u8" ou "application/json"
4. Trouver l'endpoint qui renvoie l'URL du flux
5. Reproduire la requête dans notre backend
```

**Exemple VidSrc :**
```
GET https://vidsrc.cc/api/source/{tmdb_id}
Headers: Referer: https://vidsrc.cc/, User-Agent: ...
→ Réponse JSON avec l'URL m3u8
```

**Avantages :**
- Très rapide (simple requête HTTP)
- Pas de dépendance externe

**Inconvénients :**
- Les API changent sans préavis → maintenance constante
- Chaque provider a un mécanisme différent
- Certains utilisent du chiffrement côté client

### 5.3 Option C : Garder Playwright mais optimiser

- Réutiliser le même contexte de navigateur (pas en recréer un à chaque requête)
- Lancer les extracteurs en **parallèle** (un onglet par provider)
- Ajouter des profils de navigateur réalistes (anti-détection)
- Limiter à 2-3 providers au lieu de 7

### 5.4 Option D : Utiliser uniquement les iframes avec interface améliorée

Abandonner l'extraction directe et se concentrer sur une **bonne UX autour des iframes** :
- Barre de sources en overlay au-dessus de l'iframe
- Bouton "Source suivante" rapide
- Mémorisation de la source préférée de l'utilisateur
- L'iframe prend 100% de la place avec contrôle minimal autour

**Avantages :**
- Zéro maintenance côté extraction
- Fonctionne toujours

**Inconvénients :**
- Pas de contrôle sur le player (pas de raccourcis clavier, pas de sauvegarde précise de progression)
- Pubs dans les iframes
- UX dégradée

---

## 6. Plan d'Action Recommandé

### Phase 1 — Remplacer Playwright par yt-dlp (Priorité haute)

1. Installer `yt-dlp` sur le serveur
2. Créer un nouveau extracteur `YtDlpExtractor` qui appelle `yt-dlp -j <embed_url>`
3. Parser la sortie JSON pour extraire `url`, `format`, `headers`
4. Retirer la dépendance Playwright du `Cargo.toml`
5. Tester avec les 7 providers actuels

### Phase 2 — Reverse-engineering des API prioritaires (Priorité moyenne)

1. Analyser les 3 providers les plus fiables (VidSrc, AutoEmbed, SuperEmbed)
2. Documenter leurs API internes
3. Créer des extracteurs HTTP légers et rapides
4. Fallback vers yt-dlp si l'extracteur HTTP échoue

### Phase 3 — Améliorer le proxy et le player (Priorité basse)

1. Ajouter un cache intelligent pour les URLs extraites (déjà fait, Redis 30min)
2. Pré-extraire les streams quand l'utilisateur ouvre la page de détails
3. Ajouter le support Picture-in-Picture
4. Améliorer la gestion d'erreurs dans le player (retry automatique)

---

## 7. Config Actuelle (.env)

```env
# Streaming
STREAMING_ENABLED=true          # Active le système d'extraction
STREAMING_HEADLESS=true         # Chromium en mode headless (pas de fenêtre)

# Sous-titres
SUBDL_API_KEY=...               # Clé API SubDL pour les sous-titres

# Proxy (pour contourner CORS)
# Le proxy /api/streaming/proxy est toujours actif
```

---

## 8. Structure des Fichiers Concernés

```
src/
├── extractors/
│   ├── mod.rs              # Trait StreamExtractor + types
│   ├── headless.rs         # HeadlessExtractor (Playwright) ← À REMPLACER
│   ├── moviesapi.rs        # Extracteur HTTP MoviesAPI
│   ├── autoembed.rs        # ⚠️ Code mort (ne fonctionne pas)
│   ├── embed_su.rs         # ⚠️ Code mort (ne fonctionne pas)
│   ├── vidsrc.rs           # ⚠️ Code mort (ne fonctionne pas)
│   └── registry.rs         # Registre + tri par priorité
├── api/
│   └── streaming.rs        # Routes API streaming
├── clients/
│   └── subtitles.rs        # Client sous-titres SubDL
└── main.rs                 # Initialisation Playwright (lignes 575-608)

dashboard/
├── src/lib/components/
│   └── CustomPlayer.svelte # Lecteur vidéo custom (complet, fonctionnel)
├── src/lib/api/
│   └── client.ts           # extractStreams(), getProxyUrl(), etc.
└── src/routes/watch/
    └── [media_type]/[tmdb_id]/
        └── +page.svelte    # Page de lecture
```

---

## 9. Résumé

| Élément | État |
|---|---|
| **CustomPlayer (frontend)** | ✅ Complet et fonctionnel |
| **Proxy CORS** | ✅ Fonctionnel avec réécriture m3u8 |
| **Sous-titres** | ✅ SubDL intégré |
| **Extraction via Playwright** | ⚠️ Fonctionnel mais lourd, lent et peu fiable |
| **Extraction via HTTP** | ❌ Les 3 extracteurs HTTP sont du code mort |
| **Solution recommandée** | 🎯 Remplacer Playwright par yt-dlp ou API tierces |

**Le frontend est prêt.** Le problème est uniquement **comment obtenir l'URL m3u8/mp4 directe** côté backend de manière fiable et légère.
