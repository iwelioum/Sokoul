# Page — Profil artiste (`/person/[id]`)

Route : `dashboard/src/routes/person/[id]/+page.svelte`

## Ce qui est fait

### Chargement de données
2 requêtes parallèles via `Promise.allSettled` :
- `tmdbPerson(id)` → biographie, photo de profil, métadonnées
- `tmdbPersonCredits(id)` → filmographie complète (films + séries)

### Traitement des crédits
- Déduplication par `id + media_type` (même titre en cast et crew compté une fois)
- Tri par date décroissante (`release_date` ou `first_air_date`)

### Biographie
- Troncature à 600 caractères avec "…"
- `bioExpanded` : état pour afficher la biographie complète
- Bouton "Voir plus" / "Voir moins"

### Skeleton de chargement
- Photo ronde (200×200px, `border-radius: 50%`)
- Lignes de texte en skeleton pour le nom, les métadonnées
- Lignes skeleton pour les crédits

### États d'interface
- Skeleton pendant le chargement
- Message d'erreur + lien retour accueil si `tmdbPerson` échoue
- Titre de page dynamique : `<nom> — SOKOUL`

## Fonctionnalités
- Affichage photo de profil TMDB
- Biographie tronquable
- Filmographie triée chronologiquement (plus récent en premier)
- Liens depuis chaque crédit vers la page de détail du média correspondant
