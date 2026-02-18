# Page — TV en direct (`/tv`)

Route : `dashboard/src/routes/tv/+page.svelte`

## Ce qui est fait

### Chargement des chaînes
- `getTvChannels()` appelée au montage
- Extraction automatique des catégories et pays uniques depuis les données
- Skeleton de 24 cards pendant le chargement

### Grille de chaînes
- Grille responsive (`repeat(auto-fill, minmax(160px, 1fr))`)
- Chaque card affiche :
  - Logo (format 16:9, `object-fit: contain`)
  - Icône TV placeholder si pas de logo
  - Overlay lecture (play icon) au hover, visible uniquement si `stream_url` est présent
  - Nom de la chaîne
  - Drapeau emoji du pays (mappé manuellement : FR, US, GB, DE, ES, IT, MA, DZ, TN, BE, CA, CH, PT, NL, AR)
  - Badge de catégorie
  - Badge vert "Gratuit" si `is_free = true`
- Cards sans `stream_url` sont désactivées (opacité 45%, curseur `not-allowed`)

### Filtres
Appliqués en temps réel via `$effect` :

| Filtre | Type |
|---|---|
| Recherche par nom | input texte avec bouton ✕ |
| Catégorie | select (valeurs dynamiques) |
| Pays | select (avec drapeaux emoji) |
| Gratuites uniquement | checkbox |

### Lecteur inline (modal)
Au clic sur une chaîne disponible :
- Overlay modal centré (`min(900px, 95vw)`)
- Header avec logo, nom de la chaîne, bouton fermer
- Balise `<video>` native avec `controls` et `autoplay`
- Fond sombre semi-transparent derrière le modal
- Fermeture par clic sur le fond ou le bouton ✕

### États d'interface
- Skeleton shimmer pendant le chargement
- Message d'erreur si l'API échoue
- État vide si aucune chaîne ne correspond aux filtres
- Compteur "N chaîne(s)" affiché dans l'en-tête après chargement
