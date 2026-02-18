# Page — Recherche (`/search`)

Route : `dashboard/src/routes/search/+page.svelte`

## Ce qui est fait

### Champ de recherche
- Input texte centré avec placeholder "Rechercher un film ou une série..."
- Debounce de 400 ms via `setTimeout` / `$effect` (chaque changement de filtre déclenche aussi une recherche)

### Logique de recherche
Deux modes :
- **Texte saisi** → `tmdbSearch(query)` + filtrage client-side par `media_type` si type ≠ `all`
- **Sans texte** → `tmdbDiscover(type, { with_genres, sort_by, year })` (découverte par filtres)

### Filtres
| Filtre | Options |
|---|---|
| Type | Tout / Films / Séries |
| Genre | Action, Drame, Comédie (par type) — désactivé si type = `all` |
| Année | input numérique, défaut = année courante |
| Tri | Popularité / Date de sortie / Note |

Note : le genre n'est disponible qu'avec 3 valeurs par type (liste partielle).

### Affichage des résultats
- Grille `repeat(auto-fill, minmax(160px, 1fr))`
- Composant `MediaCard` pour chaque résultat
- 20 skeleton cards pendant le chargement
- Message "Commencez à taper pour rechercher." si résultats vides et pas de chargement

## Limites actuelles
- Pas de pagination (une seule page de résultats)
- Genres limités à 3 options par type
- Pas de lien depuis la navbar (la search overlay utilise un autre composant)
