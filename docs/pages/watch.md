# Page — Lecteur vidéo (`/watch/[media_type]/[tmdb_id]`)

Route : `dashboard/src/routes/watch/[media_type]/[tmdb_id]/+page.svelte`

## Ce qui est fait

### Paramètres de route
- `media_type` : `movie` ou `tv`
- `tmdb_id` : identifiant TMDB numérique
- Query params : `?season=<N>&episode=<N>` pour les séries

### Chargement
Au montage (`onMount`) :
1. Récupération du titre en parallèle :
   - Film → `tmdbMovieDetails(tmdbId).title`
   - Série → `tmdbTvDetails(tmdbId).name`
2. Résolution des flux via `resolveConsumetStreams(mediaType, tmdbId, season, episode)`
   - Retourne : `streams[]` + `subtitles[]`

### États d'interface
- **Chargement** : spinner noir plein écran (anneau rouge `border-top-color: #e50914`)
- **Erreur / aucun flux** : message + bouton "← Retour" vers la page de détail
- **Succès** : passe tous les props au composant `CustomPlayer`

### Composant `CustomPlayer`
Props transmis :
- `streams` — liste des flux extraits (HLS ou MP4)
- `subtitles` — pistes de sous-titres
- `title` — titre du média
- `mediaType`, `tmdbId`, `season`, `episode` — contexte de lecture
- `onBack` — callback → `goto(/<mediaType>/<tmdbId>)`

Fonctionnalités du player (implémentées dans `CustomPlayer.svelte`) :
- Lecture HLS via HLS.js avec fallback MP4
- Sélection de qualité (multi-résolutions)
- Sélection de piste audio (avec préférence française automatique)
- Sous-titres activables/désactivables par piste
- Contrôles : play/pause, seek, volume, mute, vitesse (0.5×–2×), plein écran
- Changement de flux automatique en cas d'erreur HLS
- Support proxy pour les headers `Referer`
- Autoplay à l'arrivée sur la page
