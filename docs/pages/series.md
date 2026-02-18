# Page — Séries (`/series`)

Route : `dashboard/src/routes/series/+page.svelte`

## Ce qui est fait

Identique à la page Films dans son fonctionnement, mais orientée séries TV.

### Vue par défaut (sans filtre)
- 7 lignes horizontales `MediaRow`, une par genre TV :
  - Drame, Comédie, Crime, Action & Aventure, SF & Fantastique, Mystère, Animation
- Chaque ligne charge les 20 séries les plus populaires du genre via `tmdbDiscover('tv', ...)`
- Lien "Voir plus" → `/series?genre=<id>`

### Mode filtré
- Grille responsive (`repeat(auto-fill, minmax(180px, 1fr))`)
- Pagination incrémentale avec **Charger plus**
- État vide si aucun résultat
- Badge bibliothèque sur les cartes déjà ajoutées

### Panneau de filtres (`MegaFilter`)
Mêmes options que la page Films, mais type de média par défaut = `tv`.

### Tags de filtres actifs
- Même comportement que Films : affichage, suppression individuelle, réinitialisation

### Intégration bibliothèque
- `listLibrary()` appelée au montage si connecté
- IDs stockés dans un `Set<number>` → badge sur les cartes

## Paramètres URL supportés
- `?provider=<id>&provider_name=<label>` → filtre par plateforme
- `?genre=<id>` → filtre par genre
