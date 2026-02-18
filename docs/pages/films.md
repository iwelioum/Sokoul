# Page — Films (`/films`)

Route : `dashboard/src/routes/films/+page.svelte`

## Ce qui est fait

### Vue par défaut (sans filtre)
- 8 lignes horizontales `MediaRow`, une par genre :
  - Action, Comédie, Drame, Thriller, Science-Fiction, Horreur, Fantastique, Animation
- Chaque ligne charge les 20 films les plus populaires du genre via `tmdbDiscover`
- Lien "Voir plus" sur chaque ligne → `/films?genre=<id>`
- Chargement en parallèle (`Promise.allSettled`)

### Mode filtré
Déclenché dès qu'un filtre est actif ou qu'une URL contient `?provider=` ou `?genre=` :

- Grille responsive (`repeat(auto-fill, minmax(180px, 1fr))`)
- Composant `MediaCard` avec badge "en bibliothèque" si le film est déjà ajouté
- Bouton **Charger plus** en bas (pagination incrémentale, `page++`)
- État vide si aucun résultat

### Panneau de filtres (`MegaFilter`)
Ouvert via le bouton **FILTRER** (badge indiquant le nombre de filtres actifs) :

| Filtre | Valeur par défaut |
|---|---|
| Genres (multi-select) | aucun |
| Tri | `popularity.desc` |
| Année min / max | 1980 – année courante |
| Fournisseur streaming | aucun |
| Type de média | `movie` |

### Tags de filtres actifs
- Affichage sous l'en-tête : plateforme, nb de genres, plage d'années, tri personnalisé
- Chaque tag a un bouton × pour retirer le filtre individuellement
- Bouton **Effacer** pour tout réinitialiser (recharge les lignes par genre)

### Intégration bibliothèque
- Au montage, `listLibrary()` est appelée si l'utilisateur est connecté
- Les IDs sont stockés dans un `Set<number>` → `MediaCard` reçoit `inLibrary=true`

## Paramètres URL supportés
- `?provider=<id>&provider_name=<label>` → filtre par plateforme
- `?genre=<id>` → filtre par genre
