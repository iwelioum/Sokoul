# Bilan de Santé Global (Executive Summary)

*   **Criticité Élevée - Sécurité & Architecture:**
    *   **Couplage Fort Backend:** Le mélange de la logique métier, de l'accès aux données (`db`), et de la couche de présentation (`api`) dans le répertoire `src/api` du backend Rust crée un couplage élevé. Cela rend le code difficile à tester, à maintenir, à faire évoluer et augmente les risques de vulnérabilités en mêlant les préoccupations de sécurité de différentes couches.
    *   **Manque de Séparation des Responsabilités (SoC):** L'absence d'une architecture explicitement définie et respectée pour le backend (ex: Clean Architecture) rend complexe l'identification des responsabilités de chaque module, pouvant mener à des effets de bord inattendus et à une surface d'attaque plus large.
    *   **Gestion de l'État Frontend Ambigüe:** Sans une stratégie claire de gestion de l'état global et serveur dans le dashboard SvelteKit, le projet risque de souffrir de "prop drilling", de duplications de logique de fetching, et d'une difficulté à maintenir la cohérence des données, impactant l'UX et la performance.

*   **Criticité Moyenne - Qualité du Code & Performance:**
    *   **Potential de Dette Technique:** La structure actuelle ne prévient pas intrinsèquement le code mort, la duplication, ou l'introduction d'anti-patterns. Un audit de code approfondi sera nécessaire pour quantifier cette dette.
    *   **Optimisations Non Évidentes:** Les stratégies d'optimisation (memoization, lazy loading, code splitting) pour le frontend SvelteKit ne sont pas directement discernables de la structure des fichiers, suggérant qu'elles pourraient ne pas être appliquées de manière systématique.

*   **Criticité Faible - UI/UX, Accessibilité & Tests:**
    *   **Design System Manquant:** L'absence d'un Design System formalisé (couleurs, typographie, composants interactifs) peut mener à des incohérences visuelles et à une expérience utilisateur fragmentée.
    *   **Accessibilité Non Garantie:** Sans directives claires, l'accessibilité (WCAG, attributs `aria-*`, navigation clavier) est difficile à assurer, limitant l'audience potentielle et la conformité légale.
    *   **Stratégie de Test Implicite:** La structure des répertoires ne révèle pas de stratégie de test explicite (unitaires, intégration, E2E), ce qui peut compromettre la robustesse et la fiabilité des nouvelles fonctionnalités et des refactorisations.

# Nouvelle Architecture Proposée

## Principes Généraux
L'objectif est d'implémenter une architecture axée sur la séparation des préoccupations (Separation of Concerns - SoC) et l'inversion de dépendance, inspirée de la Clean Architecture. Cela permettra une plus grande maintenabilité, testabilité, flexibilité et sécurité pour les deux applications (backend Rust et frontend SvelteKit).

## 1. Backend Rust (Projet Sokoul)

```
sokoul/
├── src/
│   ├── main.rs                 # Point d'entrée de l'application
│   ├── config.rs               # Configuration globale
│   ├── error.rs                # Gestion centralisée des erreurs (types d'erreurs, conversions)
│   ├── shared/                 # Utilitaires et traits partagés (ex: logging, metrics, traits pour repositories)
│   │   ├── mod.rs
│   │   ├── utils/
│   │   ├── traits/             # Interfaces/traits pour le domaine et l'application (ex: IUserRepository)
│   │   └── security/           # Fonctions et types liés à la sécurité (hashing, JWT - sans logique métier)
│   │
│   ├── domain/                 # Cœur de la logique métier, agnostique à l'infrastructure
│   │   ├── mod.rs
│   │   ├── entities/           # Structures de données métier pures (ex: User, Media, Collection)
│   │   ├── services/           # Logique métier pure (ex: AuthService, MediaService)
│   │   ├── repositories/       # Définition des traits pour l'accès aux données (interfaces)
│   │   └── events/             # Définition des événements de domaine
│   │
│   ├── application/            # Cas d'utilisation de l'application, orchestre le domaine
│   │   ├── mod.rs
│   │   ├── commands/           # Commandes DTOs pour les actions (ex: CreateUserCommand)
│   │   ├── queries/            # Queries DTOs pour la lecture (ex: GetUserQuery)
│   │   ├── handlers/           # Implémentation des cas d'utilisation (ex: CreateUserHandler)
│   │   ├── providers/          # Orchestre les appels aux clients externes (ex: TMDBProvider, JackettProvider)
│   │   └── scheduler/          # Logique d'ordonnancement de tâches (utilise le domaine)
│   │
│   ├── infrastructure/         # Implémentations concrètes des abstractions du domaine/application
│   │   ├── mod.rs
│   │   ├── persistence/        # Implémentations des traits 'repositories' (ex: PgUserRepository, RedisCache)
│   │   │   ├── mod.rs
│   │   │   ├── postgres/
│   │   │   ├── redis/
│   │   │   └── models/         # Modèles de base de données (séparés des entités de domaine)
│   │   ├── api/                # Implémentations des clients externes (ex: TmdbClient, JackettClient)
│   │   │   ├── mod.rs
│   │   │   ├── tmdb/
│   │   │   └── jackett/
│   │   ├── telemetry/          # Logging, monitoring (metrics.rs actuel)
│   │   ├── security/           # Implémentation des mécanismes de sécurité (filtres JWT, bcrypt)
│   │   └── events/             # Implémentations des diffuseurs d'événements (NATS)
│   │
│   └── presentation/           # Couche HTTP (API REST/WebSocket)
│       ├── mod.rs
│       ├── controllers/        # Gestionnaires de routes (utilisent les handlers de l'application)
│       ├── middlewares/        # Middlewares Axum (authentification, CORS, rate limiting)
│       ├── websocket/          # Gestion des connexions WebSocket
│       └── dtos/               # Data Transfer Objects (requêtes et réponses HTTP)
│
└── tests/                      # Tests d'intégration et end-to-end
```

## 2. Frontend SvelteKit (Projet Dashboard)

```
dashboard/
├── src/
│   ├── app.html                  # Template HTML principal
│   ├── app.css                   # Styles globaux / Tailwind CSS directives
│   │
│   ├── lib/                      # Composants réutilisables, utilitaires, services
│   │   ├── components/           # Composants UI agnostiques à la page
│   │   │   ├── ui/               # Composants de base (Button, Input, Card) - Design System
│   │   │   ├── features/         # Composants plus complexes (UserMenu, MediaCard)
│   │   │   └── layouts/          # Layouts généraux (Header, Sidebar)
│   │   │
│   │   ├── api/                  # Clients API générés ou manuels (Axios/Fetch wrappers)
│   │   │   ├── mod.ts            # Index des clients
│   │   │   └── types.ts          # Types partagés avec le backend (via OpenAPI/codegen si possible)
│   │   │
│   │   ├── stores/               # Svelte Stores pour la gestion de l'état global du client
│   │   │   ├── authStore.ts
│   │   │   ├── userStore.ts
│   │   │   └── settingsStore.ts
│   │   │
│   │   ├── utils/                # Fonctions utilitaires diverses (formatters, helpers)
│   │   │
│   │   ├── domain/               # Types et interfaces spécifiques au domaine frontend
│   │   │   ├── mod.ts
│   │   │   └── media.ts          # Ex: Interface Media avec des propriétés calculées frontend
│   │   │
│   │   └── services/             # Logique métier frontend ou coordination de l'API/stores
│   │       ├── authService.ts
│   │       └── mediaService.ts
│   │
│   └── routes/                   # Pages et logique spécifique aux pages (routées par SvelteKit)
│       ├── +layout.svelte        # Layout global de l'application
│       ├── +layout.ts            # Logique de layout
│       ├── +page.svelte          # Page d'accueil
│       ├── (auth)/               # Groupe de routes pour l'authentification
│       │   ├── login/+page.svelte
│       │   └── register/+page.svelte
│       ├── (app)/                # Groupe de routes pour l'application principale
│       │   ├── films/
│       │   │   ├── [id]/+page.svelte # Page de détail d'un film
│       │   │   └── +page.svelte      # Page liste de films
│       │   ├── tv/
│   │   │   ├── +page.svelte
│   │   │   └── [id]/+page.svelte
│       │   ├── settings/
│       │   └── ...
│
└── tests/                        # Tests unitaires de composants et intégration de services

## 1.2 Gestion de l'état et Flux de données (Frontend SvelteKit)

### Identification des Bottlenecks et Mauvaises Pratiques Potentielles
*   **Prop Drilling:** La transmission de données via de multiples niveaux de composants, rendant le code difficile à maintenir et à refactoriser.
*   **Duplication de Logique de Fetching:** Le code de récupération de données (`fetch`) répété à travers différentes pages ou composants, sans mécanisme centralisé de cache ou de revalidation.
*   **Gestion Ad-hoc du Cache Serveur:** Absence d'une stratégie cohérente pour gérer le cache des données provenant du backend, pouvant mener à l'affichage d'informations obsolètes ou à des requêtes inutiles.
*   **Complexité de l'État Local:** L'utilisation excessive de l'état local des composants pour des données qui devraient être partagées ou gérées globalement, compliquant la synchronisation et la réactivité de l'UI.

### Stratégie Proposée de Gestion de l'État

L'approche pour la gestion de l'état dans SvelteKit sera divisée en trois catégories principales pour assurer clarté, performance et maintenabilité :

1.  **État Client Global (Client-Side Global State):**
    *   **Objectif:** Gérer les informations qui sont globales à l'application frontend et ne dépendent pas directement du backend ou de requêtes API fréquentes (ex: statut d'authentification, préférences utilisateur, thème de l'application, état d'un panier).
    *   **Recommandation:** Utiliser les **Stores Svelte** natifs (`writable`, `readable`, `derived`). Ils sont légers, réactifs et parfaitement intégrés à l'écosystème Svelte.
    *   **Mise en œuvre:** Les stores seront définis dans `dashboard/src/lib/stores/`. Par exemple, `authStore.ts` pour gérer le token JWT et l'état de connexion de l'utilisateur, `settingsStore.ts` pour les paramètres de l'interface. Ces stores seront importés et utilisés directement là où ils sont nécessaires, évitant ainsi le "prop drilling" pour ces types de données.

2.  **État Serveur (Server-Side Data State):**
    *   **Objectif:** Gérer les données qui proviennent du backend (via API REST) et qui nécessitent des fonctionnalités avancées comme le cache, la revalidation, le traitement des erreurs, le chargement, et la gestion des mutations.
    *   **Recommandation:** Adopter une approche combinant les **fonctions `load` de SvelteKit** pour l'initialisation des données de page/layout, et des **stores Svelte personnalisés encapsulant la logique de fetching et de cache** pour les données dynamiques ou partagées.
    *   **Mise en œuvre:**
        *   **`+page.ts` / `+layout.ts` `load` functions:** Pour le chargement initial des données d'une page ou d'un layout. SvelteKit gère automatiquement la sérialisation, la désérialisation, et un certain niveau de cache.
        *   **Services et Stores personnalisés (`dashboard/src/lib/services`):** Pour les données qui doivent être rafraîchies, mutées ou partagées de manière réactive au-delà du cycle de vie d'une page, des "service stores" seront créés. Un `mediaService.ts` pourrait par exemple contenir un store Svelte (`writable`) qui stocke la liste des films/séries, avec des méthodes pour `fetchMedia()`, `addMedia()`, `updateMedia()`. Ces méthodes interagiraient avec `dashboard/src/lib/api` pour les appels backend et mettraient à jour le store en conséquence. Ces services pourront implémenter des stratégies de cache (ex: "stale-while-revalidate" simplifié).
        *   **Client API (`dashboard/src/lib/api`):** Sera le point d'entrée unique pour toutes les requêtes HTTP vers le backend, gérant l'authentification, la gestion des erreurs HTTP standardisées, et la transformation des données.

3.  **État Local des Composants (Local Component State):**
    *   **Objectif:** Gérer l'état interne et temporaire d'un composant, qui n'a pas besoin d'être partagé avec d'autres parties de l'application (ex: état d'un formulaire, visibilité d'un modal, statut de chargement local).
    *   **Recommandation:** Utiliser la réactivité native de Svelte avec les déclarations `let` et les blocs `$:`.
    *   **Mise en œuvre:** Directement dans les fichiers `.svelte` pour une encapsulation maximale.

### Avantages de cette Stratégie
*   **Réduction du Prop Drilling:** L'état global et l'état serveur seront accessibles via stores ou services, réduisant la nécessité de passer des props via de multiples composants.
*   **Cohérence des Données:** Centralisation de la logique de fetching et de cache pour les données serveur, garantissant que l'UI affiche toujours les informations les plus à jour (ou une version mise en cache avec un mécanisme de rafraîchissement).
*   **Performances Améliorées:** Grâce aux fonctions `load` de SvelteKit (SSR/SSG), au cache des stores personnalisés et à la réactivité fine de Svelte.
*   **Maintenance Simplifiée:** Chaque type d'état a une place et une méthode de gestion claires, rendant le code plus prévisible et facile à déboguer.
*   **Meilleure Expérience Développeur:** En exploitant les points forts de Svelte et SvelteKit, tout en apportant une structure pour les défis de gestion d'état complexes.

Cette stratégie assure une séparation claire entre l'état du client (authentification, préférences) et l'état du serveur (données de l'API), tout en tirant parti des fonctionnalités intégrées de SvelteKit pour une gestion efficace et performante des données.

## PHASE 2 : QUALITÉ DU CODE ET CYBERSÉCURITÉ

### 2.1 Audit de Code et Dette Technique (Observations Préliminaires)
*   **Code Mort / Dupliqué:** Sans une analyse approfondie et l'exécution d'outils de linting/analyse statique, il est difficile d'identifier précisément le code mort ou dupliqué. Cependant, la restructuration proposée en Clean Architecture devrait naturellement réduire la duplication en favorisant la réutilisation des composants et la centralisation de la logique métier.
*   **Anti-patterns:** Le couplage fort identifié entre la couche de présentation et la logique métier/accès aux données dans l'architecture actuelle est un anti-pattern majeur. La refonte visera à le corriger.
*   **Optimisations Algorithmiques et de Rendu:**
    *   **Backend (Rust):** L'utilisation extensive du caching (`redis`) dans les modules `tv.rs` et `tmdb.rs` est une bonne pratique pour améliorer les performances.
    *   **Frontend (SvelteKit):** Les optimisations comme le "lazy loading" des composants ou le "code splitting" sont gérées en partie par SvelteKit et Vite. Une analyse plus poussée du frontend serait nécessaire pour identifier des optimisations spécifiques au rendu Svelte.

### 2.2 Analyse SecOps (Vulnérabilités Identifiées et Recommandations)

#### A. Vulnérabilités Majeures (Criticité Élevée)

1.  **Absence d'Autorisation Granulaire pour les Routes Administratives (`src/api/security.rs`)**
    *   **Description:** Presque toutes les routes du module `security.rs` sont destinées aux administrateurs (ex: `get_audit_logs`, `add_blacklist`), mais aucune vérification d'autorisation basée sur le rôle de l'utilisateur n'est présente au niveau de l'API. Un utilisateur authentifié (non-admin) pourrait potentiellement accéder à ces fonctionnalités.
    *   **Impact:** Divulgation d'informations sensibles (logs d'audit), modification non autorisée de la blacklist/whitelist, compromettant l'intégrité du système de sécurité.
    *   **Recommandation:** Implémenter un middleware d'autorisation robuste qui vérifie le rôle de l'utilisateur (`Claims.role`) extrait du JWT pour toutes les routes administratives. Seuls les utilisateurs avec le rôle 'admin' devraient pouvoir y accéder.

2.  **Fallback `Uuid` Insecure dans `get_user_id` (`src/api/watchlist.rs`)**
    *   **Description:** La fonction `get_user_id` fournit un `Uuid` par défaut (`0000...001`) si l'extraction de l'ID utilisateur à partir du JWT échoue. Ceci est un contournement potentiel de l'authentification si cette ID correspond à un utilisateur existant avec des privilèges.
    *   **Impact:** Accès non autorisé aux données du "watchlist" d'un utilisateur par défaut, ou même à des fonctionnalités privilégiées si l'utilisateur par défaut a des droits spéciaux.
    *   **Recommandation:** La fonction `get_user_id` doit être refactorisée pour retourner un `Result<Uuid, ApiError::Unauthorized>` ou `Option<Uuid>`. Les handlers appelants devront explicitement gérer le cas où l'utilisateur n'est pas authentifié ou autorisé, en retournant une erreur `401 Unauthorized` ou `403 Forbidden` le cas échéant. Le concept d'utilisateur "anonyme" doit être explicite et sécurisé.

#### B. Vulnérabilités Mineures à Modérées (Criticité Moyenne)

1.  **Gestion du Secret JWT par Défaut (`src/api/auth.rs`)**
    *   **Description:** L'application vérifie si `CONFIG.jwt_secret` est la valeur par défaut ("sokoul_default_secret_change_me") mais permet de démarrer. Utiliser un secret par défaut, même avec un avertissement, est risqué.
    *   **Impact:** Si le secret par défaut est utilisé en production, les JWT peuvent être facilement falsifiés par un attaquant, permettant l'usurpation d'identité.
    *   **Recommandation:** L'application doit **refuser de démarrer en mode production** si `CONFIG.jwt_secret` est la valeur par défaut. Ce secret doit toujours être chargé via une variable d'environnement sécurisée et être unique et complexe.

2.  **Gestion de l'API Key (`src/api/auth.rs`)**
    *   **Description:** Le middleware d'authentification (`api_key_middleware`) accepte une clé API générique (`X-API-Key`) ou un token Bearer en plus du cookie JWT. Une clé API statique et sans gestion granulaire des permissions est un risque de sécurité élevé si elle est compromise.
    *   **Impact:** Une clé API compromise peut accorder un accès complet à l'API, contournant le système de rôles basé sur JWT.
    *   **Recommandation:** Si les clés API sont nécessaires, elles devraient être gérées via un système dédié avec des permissions granulaires, une rotation facile, et des limites de débit (`rate limiting`). Elles devraient être traitées séparément de l'authentification basée sur JWT. L'utilisation conjointe dans un même middleware est confuse et risque des vulnérabilités.

3.  **Validation Insuffisante des Paramètres String d'Entrée (XSS, DoS, Intégrité des Données)**
    *   **Description:** Plusieurs points d'entrée de l'API acceptent des chaînes de caractères via `Path` ou `Json` sans validation de leur longueur, format ou contenu, notamment :
        *   `src/api/watchlist.rs`: `AddWatchlistPayload` (champs `title`, `overview`, `poster_url`, `backdrop_url`, `media_type`).
        *   `src/api/tv.rs`: `SearchParams.q` (requêtes de recherche), `Path(code)` (identifiant de chaîne TV).
        *   `src/api/tmdb.rs`: `media_type`, `time_window`, `LangParam.lang`, `SearchQuery.query`, et divers champs string dans `DiscoverParams`.
    *   **Impact:**
        *   **XSS (Cross-Site Scripting):** Si des chaînes malveillantes (ex: scripts HTML) sont stockées et ensuite affichées sans échappement adéquat dans le frontend, cela peut entraîner l'exécution de code arbitraire dans le navigateur de l'utilisateur.
        *   **Déni de Service (DoS) / Consommation de Ressources:** Des chaînes excessivement longues peuvent saturer la base de données, la mémoire du serveur (via le cache Redis ou le traitement), ou causer des erreurs dans les appels aux APIs externes (TMDB).
        *   **Corruption de Données / Comportement Inattendu:** Des formats de données invalides peuvent perturber la logique métier ou causer des plantages.
    *   **Recommandation:**
        *   Pour les `Path` et `Query` paramètres: Mettre en place des validations strictes sur la longueur maximale, les caractères autorisés (regex), et les listes blanches (`allow-list`) pour les valeurs énumérées (ex: `media_type`, `time_window`).
        *   Pour les `Json` payloads: Utiliser des attributs de validation (si disponibles avec `serde` ou une crate de validation dédiée) sur les DTOs ou effectuer des vérifications manuelles dans les handlers pour la longueur, les URLs (avec une crate de validation d'URL), et les formats spécifiques.
        *   **Validation des données externes:** Pour les données ingérées de services externes (ex: `sync_channels_from_provider` dans `src/api/tv.rs`), valider et assainir toutes les chaînes avant de les stocker.

4.  **Faiblesse de la Politique de Mot de Passe (`src/api/auth.rs`)**
    *   **Description:** La validation du mot de passe à l'inscription ne vérifie que la longueur minimale (8 caractères).
    *   **Impact:** Mots de passe faibles plus susceptibles d'être craqués par des attaques par force brute ou dictionnaire.
    *   **Recommandation:** Imposer des exigences de complexité (majuscules, minuscules, chiffres, caractères spéciaux) en plus de la longueur minimale.

5.  **Granularité des Messages d'Erreur (`src/api/auth.rs`, `src/api/security.rs`, `src/api/watchlist.rs`)**
    *   **Description:** De nombreuses erreurs de base de données sont mappées à un `ApiError::InternalServerError` générique. Bien que cela empêche la divulgation de détails techniques, cela manque de granularité pour les clients.
    *   **Impact:** Expérience utilisateur dégradée (messages d'erreur peu clairs), difficulté de débogage pour les développeurs frontend.
    *   **Recommandation:** Créer des variantes plus spécifiques d'`ApiError` (ex: `ApiError::Conflict` pour les doublons, `ApiError::BadRequest` pour des données invalides spécifiques) et les utiliser lorsque c'est pertinent, sans révéler d'informations internes sensibles.

6.  **Exposition Publique du Statut de Sécurité (`src/api/security.rs`)**
    *   **Description:** La route `/status` dans `security.rs` est publique et expose l'état de l'intégration avec VirusTotal, Urlhaus, et l'activation de la whitelist/blacklist.
    *   **Impact:** Peut fournir à un attaquant des informations sur les défenses de sécurité actives du système, l'aidant à cibler ses attaques.
    *   **Recommandation:** Réévaluer la nécessité de rendre cette information publique. Si elle est destinée à la surveillance interne, elle devrait être protégée par une authentification ou un accès réseau restreint.

## PHASE 3 : REFONTE UI/UX ET ACCESSIBILITÉ

### 3.1 Audit et Refonte Visuelle

#### Critique du Design Actuel (Hypothétique, basé sur des patterns communs sans Design System)

*   **Hiérarchie Visuelle et Loi de Proximité:** Il est probable que l'application souffre d'une hiérarchie visuelle inconsistante. Sans une grille de design et des règles d'espacement claires, les éléments peuvent apparaître trop proches ou trop éloignés, rendant difficile la distinction entre les informations corrélées et non corrélées. Les titres et sous-titres pourraient ne pas utiliser une échelle typographique cohérente, ce qui nuit à la lisibilité et à la capacité de l'utilisateur à scanner rapidement le contenu.
*   **Clarté des Call-to-Action (CTA):** Les boutons et autres éléments interactifs peuvent manquer de distinction visuelle claire (couleur, taille, iconographie), rendant leur fonction moins évidente. Les états (hover, focus, active, disabled) peuvent être absents ou inconsistants, ce qui réduit le feedback visuel pour l'utilisateur.
*   **Cohérence Visuelle Générale:** L'absence d'un Design System unifié conduit souvent à des variations de style pour des composants similaires (ex: différentes apparences pour des formulaires, des cartes, des listes), créant une expérience utilisateur fragmentée et peu professionnelle.

#### Proposition d'un Design System Embryonnaire (Guidelines Générales)

L'objectif est de créer une base pour une identité visuelle cohérente et de faciliter le développement futur.

*   **Couleurs :**
    *   **Primaire (Ex: `#4F46E5` - Indigo):** Couleur principale de la marque, utilisée pour les CTA primaires, les éléments clés de navigation, et les accents importants.
    *   **Secondaire (Ex: `#6366F1` - Indigo clair):** Utilisée pour les CTA secondaires, les éléments de fond subtils ou les illustrations.
    *   **Accent (Ex: `#EC4899` - Rose):** Pour attirer l'attention sur des notifications, des badges ou des éléments interactifs spécifiques.
    *   **Neutres (Ex: `#1F2937` - Gris très foncé, `#D1D5DB` - Gris clair):** Pour le texte, les arrière-plans, les bordures et les séparateurs.
    *   **Feedback :**
        *   **Succès (Ex: `#10B981` - Vert):** Pour les messages de réussite, les validations positives.
        *   **Erreur (Ex: `#EF4444` - Rouge):** Pour les messages d'erreur, les alertes critiques.
        *   **Warning (Ex: `#F59E0B` - Jaune/Orange):** Pour les avertissements, les attentions.
*   **Typographie :**
    *   **Polices Recommandées:**
        *   **Primaire (Ex: `Inter` ou `Roboto`):** Police sans-serif moderne pour le corps du texte et les titres, assurant une bonne lisibilité sur tous les écrans.
        *   **Secondaire (Ex: `Space Mono` ou `Fira Code`):** Police monoespacée pour le code, les identifiants techniques ou les données spécifiques.
    *   **Échelle de Tailles Cohérente:**
        *   **Base:** 16px (1rem) pour le corps du texte.
        *   **Titres:** Utiliser une échelle modulaire (ex: 1.25rem, 1.5rem, 1.875rem, 2.25rem, 3rem) pour `h6` à `h1`.
        *   **Textes secondaires:** Tailles plus petites (ex: 0.875rem, 0.75rem) pour les légendes, les métadonnées.
        *   **Line-height:** Cohérent (ex: 1.5 pour le corps du texte).
*   **Composants (Règles pour les états interactifs) :**
    *   **Boutons (Primary, Secondary, Outline, Ghost):**
        *   **Default:** Styles définis (couleur de fond, texte, bordure).
        *   **Hover:** Légère variation de couleur de fond/texte, ou une ombre subtile.
        *   **Focus:** Bordure visible (outline) pour l'accessibilité au clavier.
        *   **Active:** Couleur de fond plus foncée, effet pressé.
        *   **Disabled:** Opacité réduite, curseur `not-allowed`.
        *   **Loading:** Indicateur de spinner intégré, bouton désactivé.
    *   **Champs de Formulaire (Input, Textarea, Select):**
        *   **Default:** Bordure claire, fond neutre.
        *   **Focus:** Bordure accentuée avec la couleur Primaire ou Accent.
        *   **Error:** Bordure et texte d'aide de couleur Erreur.
        *   **Disabled:** Fond grisé, non interactif.
    *   **Liens:**
        *   **Default:** Couleur Primaire, souligné.
        *   **Hover:** Soulignement supprimé ou changement de couleur subtil.
        *   **Visited:** Légère variation de couleur.

### 3.2 Accessibilité (a11y) et Navigation

#### Audit des Problèmes d'Accessibilité Potentiels (Hypothétique)

*   **Non-conformité WCAG (Contrastes):** Sans une palette de couleurs testée, il est fort probable que certains textes sur fonds colorés, ou icônes, ne respectent pas les ratios de contraste minimum définis par les WCAG 2.1 AA ou AAA.
*   **Absence/Mauvaise Utilisation des Attributs `aria-*`:** Les éléments interactifs complexes (modales, menus déroulants, onglets, indicateurs de progression) pourraient manquer d'attributs `aria-label`, `aria-describedby`, `aria-expanded`, `aria-controls`, `aria-live`, etc., rendant ces fonctionnalités incompréhensibles pour les utilisateurs de lecteurs d'écran.
*   **Navigation au Clavier Déficiente:** Les utilisateurs ne pouvant pas utiliser une souris (moteur, visuel) pourraient rencontrer des difficultés:
    *   Ordre de tabulation illogique.
    *   Absence d'indicateurs de focus visibles pour les éléments interactifs.
    *   Impossibilité d'accéder ou d'interagir avec certains composants (menus, modales) via le clavier.
*   **Sémantique HTML Manquante ou Incorrecte:** Utilisation généralisée de `div` et `span` au lieu d'éléments HTML sémantiques appropriés (`<header>`, `<nav>`, `<main>`, `<aside>`, `<footer>`, `<button>`, `<input>`, `<form>`, `<label>`, `<section>`, `<article>`). Cela prive les technologies d'assistance d'informations cruciales sur la structure et le rôle des contenus.
*   **Gestion des États Réseau/Chargement/Erreur:** Les messages d'erreur de formulaire, les notifications de succès ou d'échec d'une action, et les indicateurs de chargement (placeholders, spinners) pourraient ne pas être annoncés dynamiquement aux lecteurs d'écran, laissant les utilisateurs malvoyants dans l'incertitude quant au statut de leurs actions.

#### Recommandations pour l'Accessibilité et la Navigation

*   **Tests de Contraste:** Utiliser des outils pour vérifier et ajuster tous les textes et éléments graphiques essentiels pour qu'ils respectent au minimum les normes WCAG 2.1 AA.
*   **Implémentation d'ARIA WAI-ARIA:** Intégrer les attributs `aria-*` nécessaires pour décrire le rôle, l'état et la propriété des éléments interactifs et des widgets complexes.
*   **Amélioration de la Navigation au Clavier:**
    *   Assurer un ordre de tabulation logique (`tabindex`).
    *   Fournir des indicateurs de focus clairs et visibles (via `outline` CSS) pour tous les éléments interactifs.
    *   Rendre tous les composants interactifs entièrement opérables au clavier.
*   **Utilisation de HTML Sémantique:** Remplacer les `div` et `span` génériques par des balises HTML5 sémantiques appropriées pour structurer le contenu et améliorer la compréhension par les technologies d'assistance.
*   **Annonces Live Regions (ARIA Live Regions):** Utiliser `aria-live` pour annoncer dynamiquement les mises à jour importantes de l'interface (messages d'erreur, succès, progression du chargement) aux lecteurs d'écran, sans perturber le focus de l'utilisateur.
*   **Gestion des Erreurs et Focus:** En cas d'erreur de formulaire, diriger le focus de l'utilisateur vers le premier champ en erreur et fournir des messages d'erreur clairs et associés au champ concerné.
*   **Modélisation des Parcours Utilisateur Fluides:** Pour chaque interaction clé (ex: Inscription, Connexion, Ajout à la Watchlist, Lecture de Média), modéliser explicitement les états de chargement, de succès et d'erreur. S'assurer que chaque état est clair visuellement et accessible via les technologies d'assistance. Utiliser des squelettes de chargement (skeletons) ou des indicateurs de progression.

## PHASE 4 : INGÉNIERIE QUALITÉ (TESTS & DÉBUGGAGE)

### Stratégie de Tests Proposée

Une stratégie de test complète est essentielle pour garantir la qualité, la fiabilité et la maintenabilité de l'application "Sokoul". Elle sera adaptée aux spécificités du backend Rust et du frontend SvelteKit.

#### 1. Backend Rust (Tests)

*   **Tests Unitaires:**
    *   **Portée:** Fonctions pures, logique métier (modules `domain/services`), utilitaires (`shared/utils`), et petites unités de code isolées.
    *   **Objectif:** Vérifier le comportement correct des fonctions individuelles, en s'assurant qu'elles produisent les sorties attendues pour des entrées données, et gèrent les cas d'erreur.
    *   **Mise en œuvre:** Utilisation du framework de test intégré de Rust (`#[test]`) avec des assertions claires. Moqueries légères pour isoler les dépendances externes.

*   **Tests d'Intégration:**
    *   **Portée:** Interactions entre plusieurs modules, en particulier les cas d'utilisation de l'application (`application/handlers`), l'intégration avec la base de données (`infrastructure/persistence`), et les clients d'API externes (`infrastructure/api`).
    *   **Objectif:** S'assurer que les différentes couches et composants du backend fonctionnent ensemble comme prévu.
    *   **Mise en œuvre:**
        *   **Base de données:** Utiliser une base de données de test (ex: Docker Compose avec PostgreSQL temporaire) pour s'assurer que les requêtes et les mappings ORM fonctionnent correctement. Les transactions peuvent être utilisées pour nettoyer l'état après chaque test.
        *   **Clients externes:** Moquer ou stubber les appels aux APIs externes (TMDB, Jackett, etc.) en utilisant des crates comme `wiremock` ou des implémentations de traits moqués.
        *   **API:** Tester les routes HTTP et les réponses des contrôleurs (nouvelle couche `presentation/controllers`) en utilisant un client HTTP de test (ex: `reqwest` ou des outils spécifiques à `axum`).

*   **Tests E2E (Optionnel dans un premier temps):**
    *   **Portée:** Flux critiques du backend si l'API est consommée par d'autres services au-delà du frontend.
    *   **Objectif:** Valider l'expérience globale du backend du point d'entrée au point de sortie.

#### 2. Frontend SvelteKit (Tests)

*   **Tests Unitaires:**
    *   **Portée:** Composants Svelte isolés (en particulier les composants UI réutilisables dans `lib/components/ui`), stores Svelte, et fonctions utilitaires JavaScript/TypeScript.
    *   **Objectif:** Vérifier que les composants s'affichent correctement pour différentes props et états, que les stores gèrent correctement l'état, et que les utilitaires fonctionnent comme prévu.
    *   **Mise en œuvre:** Utilisation de `Vitest` comme test runner et `@testing-library/svelte` pour tester les composants d'une manière axée sur l'utilisateur, en simulant les interactions et en vérifiant les résultats dans le DOM.

*   **Tests d'Intégration:**
    *   **Portée:** Interactions entre plusieurs composants Svelte, entre les composants et les stores, et entre les services frontend et le client API (`lib/api`).
    *   **Objectif:** S'assurer que des portions plus larges de l'interface utilisateur fonctionnent ensemble.
    *   **Mise en œuvre:** `Vitest` et `@testing-library/svelte` peuvent également être utilisés pour des tests d'intégration, en mockant les appels API.

*   **Tests End-to-End (E2E):**
    *   **Portée:** Parcours utilisateur critiques de bout en bout, de la connexion à l'utilisation des fonctionnalités principales, couvrant à la fois le frontend et le backend.
    *   **Objectif:** Valider l'expérience utilisateur complète et s'assurer que les fonctionnalités clés fonctionnent dans un environnement proche de la production.
    *   **Mise en œuvre:** Utilisation d'outils comme `Playwright` ou `Cypress`. Ces tests devraient couvrir les scénarios définis dans la section "Simulation des Parcours Utilisateur".

### Simulation des Parcours Utilisateur et Points de Rupture (Edge Cases)

Pour garantir la robustesse de l'application, nous allons simuler un parcours utilisateur principal et identifier les "edge cases" à tester en priorité, notamment via des tests E2E.

**Parcours Utilisateur Principal : Inscription → Ajout à la Watchlist → Lecture d'un Média → Déconnexion**

1.  **Inscription (Register):**
    *   **Scénario Nominal:** Un nouvel utilisateur remplit le formulaire d'inscription avec des informations valides (username, email, mot de passe fort), soumet le formulaire, et est redirigé vers le tableau de bord avec un message de succès.
    *   **Points de Rupture (Edge Cases) à Tester:**
        *   **Champs manquants/invalides:** Email mal formaté, mot de passe trop court/faible (non-respect des règles de complexité).
        *   **Conflits:** Tentative d'inscription avec un email ou un nom d'utilisateur déjà enregistré.
        *   **Erreurs réseau/serveur:** Échec de la communication avec le backend pendant l'inscription.
        *   **Attaque par rejeu:** Soumission multiple du même formulaire d'inscription.

2.  **Ajout à la Watchlist (Add to Watchlist):**
    *   **Scénario Nominal:** L'utilisateur navigue vers la page d'un média, clique sur le bouton "Ajouter à la Watchlist", et voit le média apparaître dans sa liste personnelle.
    *   **Points de Rupture (Edge Cases) à Tester:**
        *   **Média déjà présent:** Tentative d'ajouter un média déjà dans la watchlist (le bouton devrait être désactivé ou changer d'état).
        *   **Utilisateur déconnecté:** Tentative d'ajout à la watchlist sans être connecté (redirection vers la page de connexion, ou message d'erreur).
        *   **Erreur d'API:** Le backend échoue à enregistrer le média dans la watchlist.
        *   **Média introuvable:** Tentative d'ajouter un média avec un ID invalide ou inexistant.
        *   **Limites:** Dépassement d'une limite hypothétique d'éléments dans la watchlist.

3.  **Lecture d'un Média (Play Media):**
    *   **Scénario Nominal:** L'utilisateur sélectionne un média, clique sur "Regarder", le lecteur vidéo s'initialise et le contenu est lu avec succès.
    *   **Points de Rupture (Edge Cases) à Tester:**
        *   **Média sans source:** Le média sélectionné n'a pas de source de streaming disponible.
        *   **Source invalide:** L'URL du streaming est cassée ou redirige vers un contenu non valide/malveillant.
        *   **Erreur de DRM/Droits:** Le média est protégé par DRM ou l'utilisateur n'a pas les droits d'accès.
        *   **Problèmes réseau:** Perte de connexion ou faible bande passante pendant la lecture.
        *   **Problèmes de lecteur:** Le lecteur vidéo ne s'initialise pas ou ne parvient pas à décoder le flux.
        *   **État de la lecture:** Reprise de la lecture depuis la dernière position connue.

4.  **Déconnexion (Logout):**
    *   **Scénario Nominal:** L'utilisateur clique sur "Déconnexion", est déconnecté du système et redirigé vers la page de connexion ou la page d'accueil publique.
    *   **Points de Rupture (Edge Cases) à Tester:**
        *   **Erreur réseau:** La requête de déconnexion échoue.
        *   **Accès post-déconnexion:** Tentative d'accéder à une page authentifiée immédiatement après la déconnexion (devrait échouer ou rediriger).
        *   **Multi-sessions:** Déconnexion d'une session n'affectant pas d'autres sessions actives (si géré).

Ces tests E2E, combinés aux tests unitaires et d'intégration à la fois frontend et backend, permettront de couvrir un large éventail de scénarios et d'assurer une haute qualité pour l'application Sokoul.

### Plan de Refactoring (Avant/Après)

#### Problème : Fallback `Uuid` Insecure dans `get_user_id` (`src/api/watchlist.rs`)

Le code actuel utilise un `Uuid` par défaut si l'extraction de l'ID utilisateur à partir du JWT échoue. Cela peut entraîner un accès non autorisé à des fonctionnalités ou des données si l'UUID par défaut correspond à un utilisateur avec des privilèges.

**Avant (Code Existant):**
```rust
// src/api/watchlist.rs

// ... other imports
use crate::api::auth::extract_user_id;
use uuid::Uuid;
use axum::http::HeaderMap; // Assurez-vous que HeaderMap est importé si utilisé dans d'autres parties du fichier

fn get_user_id(headers: &HeaderMap) -> Uuid {
    extract_user_id(headers)
        .unwrap_or_else(|| Uuid::parse_str("00000000-0000-0000-0000-000000000001").unwrap())
}

// Exemple d'utilisation dans un handler:
// pub async fn add_to_watchlist_handler(
//     State(state): State<Arc<AppState>>,
//     headers: HeaderMap,
//     Json(payload): Json<AddWatchlistPayload>,
// ) -> Result<StatusCode, ApiError> {
//     let user_id = get_user_id(&headers);
//     // ... logique métier
// }
```

**Après (Code Refactorisé):**

La fonction `get_user_id` est remplacée par `get_user_id_secure` qui retourne un `Result<Uuid, ApiError>`. Cela force les handlers à gérer explicitement l'état non authentifié, rendant le système plus sûr et plus robuste.

```rust
// src/api/watchlist.rs

// ... other imports
use crate::api::auth::extract_user_id; // extract_user_id devrait maintenant retourner Option<Uuid> ou Result<Uuid, ...>
use crate::api::error::ApiError; // Nécessaire pour retourner l'erreur Unauthorized
use uuid::Uuid;
use axum::http::HeaderMap; // Assurez-vous que HeaderMap est importé

// Fonction pour extraire l'ID utilisateur de manière sécurisée
// Retourne un Uuid si l'utilisateur est authentifié, sinon une erreur ApiError::Unauthorized
pub fn get_user_id_secure(headers: &HeaderMap) -> Result<Uuid, ApiError> {
    extract_user_id(headers) // Supposons que extract_user_id retourne Option<Uuid>
        .ok_or_else(|| ApiError::Unauthorized("Authentication required".to_string()))
}

// Exemple d'utilisation dans un handler (par exemple, add_to_watchlist_handler):
pub async fn add_to_watchlist_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Json(payload): Json<AddWatchlistPayload>,
) -> Result<StatusCode, ApiError> {
    // Utilisation de '?' pour propager l'erreur si l'utilisateur n'est pas authentifié
    let user_id = get_user_id_secure(&headers)?;
    
    let media_id = resolve_media_id(
        &state.db_pool,
        MediaReferenceInput {
            media_id: payload.media_id,
            tmdb_id: payload.tmdb_id,
            media_type: payload.media_type,
            title: payload.title,
            year: payload.year,
            release_date: payload.release_date,
            overview: payload.overview,
            poster_url: payload.poster_url,
            backdrop_url: payload.backdrop_url,
            genres: None,
            vote_average: payload.vote_average,
        },
    )
    .await?;

    db::watchlist::add_to_watchlist(
        &state.db_pool,
        user_id,
        media_id,
        payload.auto_download,
        &payload.quality_min,
    )
    .await
    .map_err(ApiError::Database)?;
    Ok(StatusCode::CREATED)
}
```

#### Justification du Refactoring :

*   **Sécurité Améliorée :** Supprime la possibilité d'un accès par défaut non autorisé, forçant une gestion explicite de l'authentification.
*   **Clarté du Code :** Rend explicite que l'absence d'un utilisateur authentifié est une condition d'erreur qui doit être gérée, plutôt qu'un état par défaut silencieux.
*   **Maintenabilité :** Facilite la compréhension des flux d'authentification et d'autorisation dans l'application.

### Checklist de Validation (API et Routes)

| Endpoint                           | Méthode | Fonctionnalité                         | Statut Attendu (Succès) | Sécurité (Authentification/Autorisation)        | Notes sur la validation des entrées                                                                   |
| :--------------------------------- | :------ | :------------------------------------- | :---------------------- | :---------------------------------------------- | :---------------------------------------------------------------------------------------------------- |
| `/auth/register`                   | `POST`  | Inscription utilisateur                | `200 OK` / `201 Created` | Public                                          | **Username:** min 3, max 32 caractères. **Email:** format valide, min 5 caractères. **Password:** min 8 caractères, complexité (à ajouter). |
| `/auth/login`                      | `POST`  | Connexion utilisateur                  | `200 OK`                | Public                                          | **Email/Password:** Présence vérifiée.                                                                |
| `/auth/logout`                     | `POST`  | Déconnexion utilisateur                | `200 OK`                | Authentifié (via cookie/JWT)                    | Aucun paramètre d'entrée.                                                                             |
| `/auth/me`                         | `GET`   | Obtenir profil utilisateur             | `200 OK`                | Authentifié (via JWT)                           | Aucun paramètre d'entrée.                                                                             |
| `/audit-logs`                      | `GET`   | Lister les logs d'audit                | `200 OK`                | **Admin Requis**                                | **Pagination:** `page` >= 1, `limit` (1-100).                                                         |
| `/audit-logs/:risk_level`          | `GET`   | Lister les logs par niveau de risque   | `200 OK`                | **Admin Requis**                                | **risk_level:** Liste blanche (ex: "high", "medium", "low"). **Pagination:** `page` >= 1, `limit` (1-100). |
| `/reputation/:domain`              | `GET`   | Obtenir la réputation d'un domaine     | `200 OK`                | **Admin Requis**                                | **domain:** Format de domaine valide (regex), max longueur.                                           |
| `/whitelist`                       | `GET`   | Lister la whitelist                    | `200 OK`                | **Admin Requis**                                | Aucun paramètre d'entrée.                                                                             |
| `/whitelist`                       | `POST`  | Ajouter à la whitelist                 | `200 OK`                | **Admin Requis**                                | **domain:** Format de domaine valide (regex), max longueur. **reason:** Max longueur.                 |
| `/whitelist/:domain`               | `DELETE`| Supprimer de la whitelist              | `200 OK`                | **Admin Requis**                                | **domain:** Format de domaine valide (regex), max longueur.                                           |
| `/blacklist`                       | `GET`   | Lister la blacklist                    | `200 OK`                | **Admin Requis**                                | Aucun paramètre d'entrée.                                                                             |
| `/blacklist`                       | `POST`  | Ajouter à la blacklist                 | `200 OK`                | **Admin Requis**                                | **domain:** Format de domaine valide (regex), max longueur. **risk_level:** Liste blanche. **threat_type:** Liste blanche (si applicable), max longueur. **reason:** Max longueur. |
| `/blacklist/:domain`               | `DELETE`| Supprimer de la blacklist              | `200 OK`                | **Admin Requis**                                | **domain:** Format de domaine valide (regex), max longueur.                                           |
| `/status`                          | `GET`   | Statut des services de sécurité        | `200 OK`                | Public (à réévaluer l'exposition)               | Aucun paramètre d'entrée.                                                                             |
| `/watchlist`                       | `POST`  | Ajouter un média à la watchlist        | `201 Created`           | Authentifié (via JWT)                           | **media_type:** Liste blanche (ex: "movie", "tv"). **title/overview:** Max longueur. **poster_url/backdrop_url:** Format URL valide, max longueur. |
| `/watchlist/:media_id`             | `DELETE`| Supprimer un média de la watchlist     | `204 No Content`        | Authentifié (via JWT)                           | **media_id:** Format UUID valide.                                                                     |
| `/watchlist/:tmdb_id/:media_type`  | `DELETE`| Supprimer un média (compat TMDB)       | `204 No Content`        | Authentifié (via JWT)                           | **tmdb_id:** Entier positif. **media_type:** Liste blanche (ex: "movie", "tv").                      |
| `/watchlist`                       | `GET`   | Lister la watchlist (paginée)          | `200 OK`                | Authentifié (via JWT)                           | **Pagination:** `page` >= 1, `per_page` (1-100).                                                      |
| `/channels`                        | `GET`   | Lister les chaînes TV                  | `200 OK`                | Public                                          | Aucun paramètre d'entrée.                                                                             |
| `/channels/:id`                    | `GET`   | Obtenir une chaîne TV                  | `200 OK`                | Public                                          | **id:** Format UUID valide.                                                                           |
| `/channels/:id/programs`           | `GET`   | Obtenir les programmes d'une chaîne    | `200 OK`                | Public                                          | **id:** Format UUID valide. **Pagination:** `page` >= 1, `limit` (1-50).                              |
| `/channels/:code/stream`           | `GET`   | Obtenir le flux d'une chaîne           | `200 OK`                | Public                                          | **code:** Format alphanumérique spécifique (à définir), max longueur.                                 |
| `/programs/now`                    | `GET`   | Obtenir les programmes en cours        | `200 OK`                | Public                                          | Aucun paramètre d'entrée.                                                                             |
| `/programs/search`                 | `GET`   | Rechercher des programmes              | `200 OK`                | Public                                          | **q:** Min longueur, max longueur (ex: 256 caractères).                                               |
| `/tmdb/trending/:media_type/:time_window` | `GET`   | Tendances TMDB                 | `200 OK`                | Public                                          | **media_type:** Liste blanche ("all", "movie", "tv", "person"). **time_window:** Liste blanche ("day", "week"). |
| `/tmdb/discover/:media_type`       | `GET`   | Découverte TMDB                    | `200 OK`                | Public                                          | **media_type:** Liste blanche ("movie", "tv"). **DiscoverParams:** Validation de tous les champs string (listes blanches, max longueurs, formats). |
| `/tmdb/movie/:id`                  | `GET`   | Détails film TMDB                  | `200 OK`                | Public                                          | **id:** Entier positif.                                                                               |
| `/tmdb/tv/:id`                     | `GET`   | Détails série TV TMDB              | `200 OK`                | Public                                          | **id:** Entier positif.                                                                               |
| `/tmdb/tv/:tv_id/season/:season_number` | `GET`   | Détails saison TMDB            | `200 OK`                | Public                                          | **tv_id/season_number:** Entiers positifs.                                                            |
| `/tmdb/:media_type/:id/credits`    | `GET`   | Crédits TMDB                       | `200 OK`                | Public                                          | **media_type:** Liste blanche. **id:** Entier positif.                                                |
| `/tmdb/:media_type/:id/videos`     | `GET`   | Vidéos TMDB                        | `200 OK`                | Public                                          | **media_type:** Liste blanche. **id:** Entier positif.                                                |
| `/tmdb/:media_type/:id/watch/providers` | `GET`   | Fournisseurs TMDB            | `200 OK`                | Public                                          | **media_type:** Liste blanche. **id:** Entier positif.                                                |
| `/tmdb/person/:id`                 | `GET`   | Détails personne TMDB              | `200 OK`                | Public                                          | **id:** Entier positif.                                                                               |
| `/tmdb/person/:id/credits`         | `GET`   | Crédits personne TMDB              | `200 OK`                | Public                                          | **id:** Entier positif.                                                                               |
| `/tmdb/:media_type/:id/similar`    | `GET`   | Contenu similaire TMDB             | `200 OK`                | Public                                          | **media_type:** Liste blanche. **id:** Entier positif.                                                |
| `/tmdb/search`                     | `GET`   | Recherche TMDB                     | `200 OK`                | Public                                          | **query:** Min longueur, max longueur (ex: 256 caractères).                                           |
| `/tmdb/collection/:id`             | `GET`   | Collection TMDB                    | `200 OK`                | Public                                          | **id:** Entier positif.                                                                               |
| `/tmdb/movie/:id/certification`    | `GET`   | Certification film TMDB            | `200 OK`                | Public                                          | **id:** Entier positif.                                                                               |
| `/tmdb/tv/:id/certification`       | `GET`   | Certification série TV TMDB        | `200 OK`                | Public                                          | **id:** Entier positif.                                                                               |