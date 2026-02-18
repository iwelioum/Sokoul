# Page — Bibliothèque (`/library`)

Route : `dashboard/src/routes/library/+page.svelte`

## Ce qui est fait

### Structure en onglets
Trois onglets avec badge compteur :

#### Ma bibliothèque
- `listLibrary(page, 30)` → 30 items par page
- Grille `repeat(auto-fill, minmax(150px, 1fr))`
- Chaque card affiche : poster, badge type (Film/Série), note étoile, titre, année
- Bouton ✕ au hover → `removeFromLibrary(tmdbId, mediaType)` avec mise à jour optimiste de la liste
- Clic sur la card → page de détail

#### Liste d'envies
- `listWatchlist(1, 30)` → 30 items
- Même grille que la bibliothèque
- Chaque card a une barre d'actions en bas :
  - Bouton **Bibliothèque** → `addToLibrary()` + `removeFromWatchlist()` (déplacement)
  - Bouton ✕ → `removeFromWatchlist()`

#### Historique
- `getContinueWatching(50)` → 50 entrées
- Affichage en liste verticale (pas en grille)
- Chaque ligne : poster miniature (56×80px), titre, type, barre de progression, pourcentage
- Icône play à droite, colorée à l'accent au hover
- Clic → page de détail du média

### Chargement lazy par onglet
- Chaque onglet ne charge ses données que la première fois qu'il est activé
- Skeleton de 12 cards (bibliothèque/watchlist) ou 8 lignes (historique) pendant le chargement

### États vides
Chaque onglet a un état vide avec :
- Icône SVG décorative
- Message principal + sous-titre explicatif
- Lien "Découvrir le catalogue" → `/`

### Calcul de progression
- `progress` stocké en décimal (0.0 à 1.0) → converti en pourcentage entier
- Barre de progression CSS (`width: {pct}%`) avec transition 0.3s
