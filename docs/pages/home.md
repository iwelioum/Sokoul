# Page — Accueil (`/`)

Route : `dashboard/src/routes/+page.svelte`

---

## Ce qui est fait

### Hero Carousel (plein écran)
- Affiche les 5 premiers éléments tendances du jour (films + séries)
- Transitions automatiques entre les slides
- Bouton **Lecture** → redirige vers `/watch/<type>/<id>`
- Bouton **Plus d'infos** → redirige vers la page de détail (`/movie/<id>` ou `/tv/<id>`)
- Skeleton affiché pendant le chargement

### Brand Tiles
- Section avec les logos des plateformes (Netflix, Disney+, Amazon Prime, etc.)
- Composant `BrandTiles.svelte` dédié

### Reprendre la lecture
- Section conditionnelle : visible uniquement si l'historique contient des entrées
- Carousel horizontal avec scroll-snap, 160px par carte
- Chaque carte affiche : poster, titre, pourcentage vu
- Barre de progression colorée en bas du poster (couleur accent)
- Flèches gauche/droite apparaissent au hover (masquées sous 900px)
- Clic → reprend la lecture là où on s'était arrêté

### Lignes de tendances
Six `MediaRow` chargés en parallèle via `Promise.allSettled` :

| Titre | Source |
|---|---|
| Tendances du jour | `tmdbTrending('all', 'day')` |
| Films populaires | `tmdbTrending('movie', 'week')` |
| Séries populaires | `tmdbTrending('tv', 'week')` |
| Populaires sur Netflix | `tmdbDiscover` provider=8, région FR |
| Populaires sur Disney+ | `tmdbDiscover` provider=337, région FR |
| Populaires sur Amazon Prime | `tmdbDiscover` provider=119, région FR |

- Liens "Voir plus" sur les lignes par plateforme → `/films?provider=<id>`
- Les items sans `media_type` jouable sont filtrés

---

## À implémenter — Immersion visuelle

### Hero — Trailer silencieux
- Après 2–3 s d'inactivité sur une slide, démarrer automatiquement le trailer en arrière-plan (muet par défaut)
- L'image de fond reste affichée jusqu'à ce que la vidéo soit prête (évite le flash)
- Bouton **Son** (icône haut-parleur) dans le coin bas-droit du Hero, état persistant dans `localStorage`
- Stopper/mettre en pause la vidéo dès que la slide change ou que l'utilisateur quitte le Hero (IntersectionObserver)
- Fallback : si aucun trailer disponible via TMDB, rester sur le backdrop statique

### Hover Preview Cards (composant `HoverPreviewCard`)
- Au `mouseenter`, déclencher un timer de **400 ms** avant toute action (réduit les déclenchements accidentels)
- Si la souris est toujours sur la carte après le délai : monter la carte (zoom léger + élévation), afficher un mini-player qui lit un extrait court (~5–10 s) en boucle silencieux
- Au `mouseleave`, annuler le timer + pause vidéo + retour au poster statique avec un leave-delay de ~150 ms
- Les métadonnées affichées au survol : titre, genres, durée/nb saisons, badge (film/série), boutons **Lecture**, **Ajouter à ma liste**, **Plus d'infos**
- `preload="none"` sur les `<video>` des cartes ; la `src` n'est injectée qu'après expiration du hover delay (évite les téléchargements en masse au scroll)
- Un seul extrait joué à la fois via un `VideoPreviewManager` global (singleton Svelte store/context) qui met en pause tous les autres au `start(id)`
- Désactivé sur mobile/touch (pas de hover natif) — fallback : tap ouvre directement la fiche détail

### Skeleton Screens raffinés
- Structure identique aux cartes finales : mêmes dimensions, mêmes border-radius, même layout
- Effet shimmer CSS (gradient animé de gauche à droite) sur tous les placeholders
- Transition douce (`opacity 0.3s`) entre skeleton et contenu réel

---

## À implémenter — Personnalisation algorithmique

### "Parce que vous avez vu…"
- Analyser les genres dominants des N derniers titres de l'historique utilisateur (`getContinueWatching`)
- Générer dynamiquement 1 à 2 lignes `MediaRow` par genre dominant via `tmdbDiscover` filtré
- Titre de ligne : `"Parce que vous avez vu [Titre]"` ou `"Parce que vous aimez [Genre]"`
- Chargé en lazy (IntersectionObserver) après les lignes de tendances

### Top 10 en France
- Nouvelle ligne avec overlay numérique sur les posters (chiffres 1 à 10 en grand, style Netflix)
- Source : `tmdbTrending('all', 'day')` filtré région FR, limité à 10 résultats
- Composant dédié `Top10Row.svelte` avec rendu poster numéroté

### Ma Liste (Watchlist)
- Section positionnée juste après "Reprendre la lecture" (avant les tendances)
- Visible uniquement si la watchlist contient au moins 1 élément
- Même style carousel que "Reprendre la lecture"

---

## À implémenter — Éditorialisation

### Collections thématiques
- Lignes ponctuelles définies côté serveur ou en config statique : `"Sélection du week-end"`, `"Primés aux Oscars"`, etc.
- Rotation possible selon la date ou des tags éditoriaux
- Source flexible : liste d'IDs TMDB curatée manuellement ou endpoint dédié backend Rust

### Pills de filtrage (sous le Hero)
- Boutons rapides : **Films** / **Séries** / **Nouveautés** / **Genres**
- Alimentent un `$derived` `activeFilter` qui réorganise ou filtre les sections visibles de la page
- Persistance de l'onglet actif en `sessionStorage` (retour arrière conserve le filtre)

---

## À implémenter — UX Pro

### Gestion globale du son (`muteStore`)
- Store Svelte `muteStore` : `Writable<boolean>`, initialisé depuis `localStorage`
- Toutes les `<video>` (Hero + Hover Cards) s'abonnent : `muted={$muteStore}`
- Toggle unique dans le Hero → propage instantanément à toutes les previews actives
- Aucune régression de l'état entre les navigations (SvelteKit layout-level store)

### Deep Linking amélioré — Bouton "Lecture"
- **Film** → navigation directe vers `/watch/movie/<id>`
- **Série** → interroger l'API `GET /resume/:mediaId` pour connaître le dernier épisode vu
  - Si trouvé → `/watch/tv/<id>?season=S&episode=E`
  - Si non trouvé → afficher un micro-popover "Choisir saison/épisode" plutôt qu'une redirection aveugle vers S01E01

### Recherche prédictive (overlay)
- Déclenchée par clic sur l'icône loupe ou scroll vers le haut depuis la page
- Interface plein écran avec suggestions de tendances (TMDB search) et historique récent
- Résultats en temps réel dès 2 caractères saisis

---

## Comportement technique

### Existant
- Svelte 5 `$state` / `$effect` / `$derived`
- Deux batches de fetch parallèles : `loadTrending()` + `loadPlatforms()`
- L'historique est chargé séparément via `getContinueWatching(20)`
- Gestion d'erreur silencieuse sur l'historique (pas de message d'erreur visible)

### À ajouter
- **`VideoPreviewManager`** : store/context global qui centralise l'ID de la preview en cours, expose `start(id)` / `stop(id)`, enforce "une seule vidéo à la fois"
- **`muteStore`** : store persistant (`localStorage`) pour l'état du son, partagé entre Hero et toutes les HoverPreviewCards
- **IntersectionObserver par row** : les `<video>` ne sont instanciées qu'une fois la row dans le viewport ; les rows du bas sont fetchées en lazy pour améliorer le LCP
- **`autoplayStore`** : booléen persistant permettant à l'utilisateur de désactiver les previews automatiques (accessibilité, économie de données)
- Format vidéo extrait : WebM/AV1 en priorité, fallback MP4 H.264 ; durée cible ~5–10 s, poids cible ~400–600 Ko par extrait
- Ordre des sections (cible) :
  1. Hero Carousel (+ trailer autoplay)
  2. Pills de filtrage
  3. Reprendre la lecture *(si historique)*
  4. Ma Liste *(si watchlist non vide)*
  5. Top 10 en France
  6. Tendances du jour
  7. "Parce que vous avez vu…" *(lazy, si historique)*
  8. Films populaires / Séries populaires
  9. Collections thématiques *(éditorial)*
  10. Plateformes (Netflix, Disney+, Amazon Prime)
  11. Brand Tiles
