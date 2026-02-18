# Page — Connexion / Inscription (`/login`)

Route : `dashboard/src/routes/login/+page.svelte`

## Ce qui est fait

### Modes
Deux modes sur la même page, basculement par bouton lien :
- **Connexion** (`login`) : email + mot de passe
- **Inscription** (`register`) : nom d'utilisateur + email + mot de passe

### Formulaire de connexion
- Email (input type `email`, requis)
- Mot de passe (input type `password`, requis, min 8 caractères)
- Appel : `login(email, password)` → stocke le token, redirige vers `/`

### Formulaire d'inscription
- Nom d'utilisateur (input text, requis, min 3 / max 32 caractères)
- Email (input type `email`, requis)
- Mot de passe (input type `password`, requis, min 8 caractères)
- Appel : `register(username, email, password)` → stocke le token, redirige vers `/`

### Gestion d'erreur
- Bloc rouge sous le titre si erreur API
- Tentative de parse JSON pour extraire le champ `error` du body de réponse
- Fallback sur le message brut si le parse échoue

### États UI
- Bouton submit affiche "Chargement..." et est désactivé pendant la requête
- Fond dégradé sombre (`#0f172a → #1e293b`)
- Card glassmorphique centrée (max 420px), backdrop-filter blur 16px
- Logo Sokoul affiché en haut de la card (64×64px)
- Lien de basculement login/register sous le formulaire
- Changement de mode réinitialise le message d'erreur
