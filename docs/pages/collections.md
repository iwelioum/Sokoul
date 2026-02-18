# Page — Collections (`/collections`)

Route : `dashboard/src/routes/collections/+page.svelte`

## Ce qui est fait

### Affichage
- Grille responsive (`repeat(auto-fill, minmax(320px, 1fr))`, gap 28px)
- En-tête avec titre "Collections" (48px) et sous-titre

### Cards de collections
Chaque card contient :
- Zone image 16:9 avec placeholder SVG (icône film)
- Nom de la collection (20px, 600)
- Description courte
- Hover : `scale(1.05)` + border blanche + ombre renforcée
- Clic → `/collection/<id>` (route non implémentée)

### Données actuelles
6 collections en dur (données statiques, pas d'API) :

| ID | Nom | Description |
|---|---|---|
| 1 | Harry Potter | La saga complète Harry Potter |
| 2 | Avatar | L'univers Avatar |
| 3 | Marvel Cinematic Universe | Toutes les phases de l'univers Marvel |
| 4 | Star Wars | La saga Star Wars complète |
| 5 | Lord of the Rings | Le Seigneur des Anneaux et le Hobbit |
| 6 | DC Universe | Les héros de DC Comics |

## Limites actuelles
- Données statiques, pas d'appel API
- Pas d'images (placeholder uniquement)
- Les routes `/collection/<id>` n'existent pas encore
- Pas de pagination
