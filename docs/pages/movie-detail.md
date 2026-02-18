# Page — Détail film (`/movie/[tmdb_id]`)

Route : `dashboard/src/routes/movie/[tmdb_id]/+page.svelte`

## Ce qui est fait

### Chargement de données
5 requêtes parallèles via `Promise.allSettled` :

| Donnée | API |
|---|---|
| Détails du film | `tmdbMovieDetails(id)` |
| Casting | `tmdbCredits('movie', id)` → 20 premiers acteurs |
| Vidéos | `tmdbVideos('movie', id)` |
| Films similaires | `tmdbSimilar('movie', id)` |
| Statut bibliothèque | `getLibraryStatus(id, 'movie')` |

### Données d'enrichissement (non-bloquantes, chargées en arrière-plan)
- `getFanartMovie(id)` → images Fanart.tv
- `getOmdbByImdbId(movie.imdb_id)` → notes OMDb (Rotten Tomatoes, Metacritic, IMDb)

### Trailer
- `$derived` : trouve la première vidéo YouTube de type "Trailer", sinon la première vidéo YouTube

### Actions utilisateur
- **Lecture** → `goto('/watch/movie/<id>')`
- **Télécharger** → `goto('/downloads?query=<titre>&tmdbId=<id>&mediaType=movie')`
- **Toggle bibliothèque** → `addToLibrary()` ou `removeFromLibrary()` avec UI optimiste
- **Toggle watchlist** → `addToWatchlist()` ou `removeFromWatchlist()` avec UI optimiste
  - `libAdding` empêche les double-clics pendant la requête

### Ligne "Films similaires"
- Composant `MediaRow` avec les résultats de `tmdbSimilar`

### États UI
- Skeleton pendant le chargement (`Skeleton.svelte`)
- Message d'erreur si `tmdbMovieDetails` échoue
- `Skeleton.svelte` importé mais la structure exacte du hero dépend des données TMDB chargées

## État de l'UI de téléchargement inline
La page contient aussi un état `showDownloadResults` et `searchResults` mais le téléchargement inline a été remplacé par une redirection vers `/downloads`.
