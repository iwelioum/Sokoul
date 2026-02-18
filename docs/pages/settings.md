# Page — Paramètres (`/settings`)

Route : `dashboard/src/routes/settings/+page.svelte`

## Ce qui est fait

### Chargement du profil
Appel à `/api/user/profile` avec token Bearer (`authToken` dans localStorage).

Données du profil :
- `id`, `username`, `email`, `first_name`, `last_name`, `created_at`

### Préférences utilisateur
État local avec valeurs par défaut :

| Préférence | Type | Valeurs |
|---|---|---|
| Thème | select | `dark` / `light` |
| Qualité | select | `auto` / `480p` / `720p` / `1080p` |
| Langue interface | select | `en` / `fr` / `es` / `de` |
| Notifications | booléen | true |
| Autoplay | booléen | true |
| Sous-titres | booléen | false |
| Langue sous-titres | select | `en` / `fr` / `es` / `de` |

### Changement de mot de passe
Champs :
- Mot de passe actuel
- Nouveau mot de passe
- Confirmation du nouveau mot de passe

### Composants utilisés
- `FormInput.svelte` — champs de formulaire réutilisables
- `Toast.svelte` — notifications de succès/erreur
- `Loading.svelte` — spinner pendant le chargement

### État de sauvegarde
- `saving` booléen pendant `handleSavePreferences()`
- Appel `success()` / `showError()` depuis `toastStore` selon le résultat

## Limites actuelles
- Le profil utilise `authToken` (ancienne clé) alors que le reste de l'app utilise `sokoul_token`
- Les préférences ne sont pas persistées côté backend (état local uniquement)
