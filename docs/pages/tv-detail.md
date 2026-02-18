# Page — Détail série (`/tv/[tmdb_id]`)

Route : `dashboard/src/routes/tv/[tmdb_id]/+page.svelte`

## Ce qui est fait

### Chargement de données
5 requêtes parallèles via `Promise.allSettled` :

| Donnée | API |
|---|---|
| Détails de la série | `tmdbTvDetails(id)` |
| Casting | `tmdbCredits('tv', id)` → 20 premiers acteurs |
| Vidéos | `tmdbVideos('tv', id)` |
| Séries similaires | `tmdbSimilar('tv', id)` |
| Statut bibliothèque | `getLibraryStatus(id, 'tv')` |

### Données d'enrichissement (non-bloquantes)
- `getFanartMovie(id)` → images Fanart.tv
- `searchOmdb(...)` → métadonnées OMDb

### Sélecteur de saison/épisode
- `selectedSeason` initialisé avec la première saison dont `season_number > 0`
- `$effect` sur `selectedSeason` → `tmdbSeasonDetails(tmdbId, season)` rechargé automatiquement
- `seasonDetail` contient les épisodes de la saison sélectionnée
- `loadingSeason` pour l'état de chargement des épisodes

### Trailer
- Même logique que la page film : première vidéo YouTube "Trailer"

### Actions utilisateur
- **Lecture** → redirection vers le lecteur avec la saison/épisode sélectionné
- **Télécharger** → `goto('/downloads?query=<titre>&tmdbId=<id>&mediaType=tv')`
- **Toggle bibliothèque** → `addToLibrary()` / `removeFromLibrary()` avec UI optimiste
- **Toggle watchlist** → `addToWatchlist()` / `removeFromWatchlist()` avec UI optimiste

### Ligne "Séries similaires"
- Composant `MediaRow` avec les résultats de `tmdbSimilar`

### États UI
- Skeleton pendant le chargement
- Message d'erreur si `tmdbTvDetails` échoue
