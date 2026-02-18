# Page — Tâches (`/tasks`)

Route : `dashboard/src/routes/tasks/+page.svelte`

## Ce qui est fait

### Chargement
- `listTasks()` au montage + polling toutes les **5 secondes** via `setInterval`
- Nettoyage du timer au démontage

### Compteurs
`$derived` calculant les totaux par statut :
- `all`, `pending`, `running`, `completed`, `failed`

### Filtrage
Filtre actif parmi : `all` / `pending` / `running` / `completed` / `failed`
- `filtered` = dérivé de `tasks` selon le filtre actif

### Affichage des tâches
Chaque tâche affiche :
- Titre extrait depuis `task.payload.title` (string) ou fallback `Task <id_tronqué>`
- Badge de statut coloré :
  - `pending` → neutre
  - `running` → orange/warning
  - `completed` → vert
  - `failed` → rouge
- Progression en pourcentage si disponible
- Dates relatives calculées dynamiquement :
  - < 60s → "il y a Xs"
  - < 1h → "il y a Xmin"
  - < 24h → "il y a Xh"
  - ≥ 24h → "il y a Xj"
- Message d'erreur (expandable) pour les tâches `failed` — toggle via `expandedErrors` (`Set<string>`)

### État de chargement
- `loading = true` jusqu'au premier résultat retourné
