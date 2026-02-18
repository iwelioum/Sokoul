# Page — Admin (`/admin`)

Route : `dashboard/src/routes/admin/+page.svelte`

## Ce qui est fait

### Tableau de bord sécurité
Appel à `/api/security/status` avec token Bearer au montage.

4 statistiques affichées en cards :

| Stat | Couleur | Description |
|---|---|---|
| Safe Downloads | Vert `#4ade80` | Nombre de téléchargements sans alerte |
| Warnings | Jaune `#facc15` | Nombre d'avertissements |
| Blocked | Rouge `#ef4444` | Nombre d'éléments bloqués |
| Whitelisted | Bleu `#64c8ff` | Nombre d'éléments en liste blanche |

### Logs récents
- `RecentLog` avec : `id`, `user_id`, `action`, `url`, `risk_level`, `created_at`
- `risk_level` : `safe` / `warning` / `critical`

### État de chargement
- `loading = true` pendant le fetch
- Gestion d'erreur si la requête échoue

---

# Sous-pages Admin

## `/admin/audit`
Route : `dashboard/src/routes/admin/audit/+page.svelte`
- Logs d'audit des actions utilisateurs

## `/admin/reputation`
Route : `dashboard/src/routes/admin/reputation/+page.svelte`
- Gestion des scores de réputation des fournisseurs Prowlarr

## `/admin/whitelist`
Route : `dashboard/src/routes/admin/whitelist/+page.svelte`
- Gestion de la liste blanche des fournisseurs/sources

## `/admin/blacklist`
Route : `dashboard/src/routes/admin/blacklist/+page.svelte`
- Gestion de la liste noire des fournisseurs/sources
