# Page — Téléchargements (`/downloads`)

Route : `dashboard/src/routes/downloads/+page.svelte`

## Ce qui est fait

### Paramètres URL attendus
- `?query=<titre>` — terme de recherche torrent
- `?tmdbId=<id>` — ID TMDB du média
- `?mediaType=movie|tv`

### Workflow complet

**Étape 1 — Création du média en base**
`createMedia({ title, media_type, tmdb_id })` → retourne un `media_id` UUID

**Étape 2 — Recherche de torrents**
`directSearch(query, mediaId)` → interrogation de Prowlarr/Jackett en temps réel
- Spinner + message "Interrogation de Prowlarr et Jackett en temps réel..."
- Résultats affichés dans une grille de cards (`repeat(auto-fill, minmax(350px, 1fr))`)

### Cards de torrents
Chaque card affiche :
- Titre du torrent
- Fournisseur (provider)
- Qualité / protocole
- Taille formatée (`formatBytes`)
- Seeders (vert) / Leechers (orange) / Score /100 (jaune)
- Badge "✓ Sélectionné" si la card est active

### Panneau de confirmation
Apparaît avec animation `slideIn` quand un torrent est sélectionné :
- Récapitulatif : titre, provider, qualité, taille, seeders, protocole, score
- Bouton **Annuler** / **Confirmer le téléchargement**
- `startDownload({ media_id, search_result_id })` → alerte succès ou échec

### Section téléchargements actifs
- `listDownloads()` au montage + intervalle toutes les **5 secondes**
- Liste de cards avec : titre, badge de statut (completed / running / failed), pourcentage
- Barre de progression animée pour les téléchargements `running`

### Gestion d'erreur
- Bloc rouge si la recherche échoue avec bouton **Réessayer la recherche**
- Authentification vérifiée (`isLoggedIn()`) avant toute action
- Message d'erreur si non connecté : "Connexion requise pour télécharger."

## États UI
- Spinner pendant la recherche de torrents
- Bloc info bleu si aucun torrent trouvé (avec bouton lancer la recherche manuellement)
- Responsive : grille 1 colonne + boutons pleine largeur sous 768px
