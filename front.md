C'est noté ! Voici le document de spécifications complet et finalisé. Il intègre la nouvelle direction artistique "Nuits d'Orient" (Bleu Nuit & Or), la stratégie de données multi-sources, et les interactions immersives.

Vous pouvez copier ce contenu dans un fichier nommé `specs_sokoul_v2.md`. C'est votre "bible" pour le développement.

---

# 📘 Sokoul V2 — Spécifications Design & Techniques

**Version :** 2.0 (Direction "Midnight Blue & Gold")
**Concept :** Immersion Cinématographique & Élégance Orientale

---

## 1. 🎨 Identité Visuelle (Design System)

L'objectif est de créer une ambiance "Premium" et apaisante, rappelant la profondeur d'un ciel nocturne, contrastée par la chaleur de l'or/terracotta (rappel du logo).

### A. Palette de Couleurs (Theme: Night Mode)

| Variable | Couleur | Code Hex | Usage |
| --- | --- | --- | --- |
| `--bg-main` | **Bleu Abysse** | `#0F172A` | Fond principal de la page (remplace le noir pur). |
| `--bg-surface` | **Bleu Indigo** | `#1E293B` | Fond des cartes, modales et menus déroulants. |
| `--text-primary` | **Blanc Lunaire** | `#F8FAFC` | Titres et textes principaux (jamais de blanc pur #FFF). |
| `--text-secondary` | **Gris Cendré** | `#94A3B8` | Métadonnées (durée, année, genre). |
| `--accent-color` | **Or Berbère** | `#D97706` | Boutons d'action (CTA), logos, états actifs. |

### B. Typographie & Formes

* **Titres (Headings) :** Police avec empattement (Serif) élégante (ex: *Playfair Display* ou *Merriweather*) pour les titres de films/séries dans le Hero.
* **Corps (Body) :** Police sans empattement (Sans-Serif) moderne et lisible (ex: *Inter* ou *Montserrat*).
* **Arrondis (Radius) :** `12px` sur les affiches, `50px` (Pill shape) sur les boutons.

---

## 2. 🏗️ Interface Utilisateur (UI/UX)

### A. Navigation (Header Dynamique)

* **État Initial (Haut de page) :** Totalement transparent. Le texte flotte sur l'image.
* **État Scrolled (> 50px) :** Devient solide avec effet de flou (*Glassmorphism*).
* `background: rgba(15, 23, 42, 0.85);`
* `backdrop-filter: blur(12px);`


* **Transition :** Fluide (`ease-in-out 0.3s`).

### B. Section Hero (Le Carousel "Incroyable")

* **Contenu :** Sélection de **5 titres majeurs** uniquement.
* **Images de fond :** Images Fanart (sans texte) haute résolution.
* **Logo du film :** Utiliser le `ClearLogo` (PNG transparent) centré ou aligné à gauche, au lieu de texte brut.
* **Transition :** Cross-fade (fondu enchaîné) lent de `0.8s`.
* **Effet Ken Burns :** Léger zoom avant lent sur l'image de fond active pour donner vie à la scène.
* **Le "Vignettage" (Masque) :**
* Indispensable pour fondre l'image dans le bleu du site.
* `background: linear-gradient(to bottom, transparent 0%, #0F172A 100%);`



### C. Cartes & Miniatures (Thumbnails)

Pour éviter l'effet "image collée" sur le fond bleu :

* **Ombre portée :** Douce et colorée (basée sur l'accent ou sombre).
* `box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.5);`


* **Hover Effect :**
* Scale up (`transform: scale(1.05)`).
* Lumière de bordure (`border: 1px solid rgba(255,255,255,0.2)`).



---

## 3. 🗄️ Architecture des Données (Data Strategy)

Pour garantir des infos complètes et un design riche.

### A. Sources (La "Cascade")

Le script doit interroger les API dans cet ordre précis :

1. **TMDB (The Movie Database) :** Base principale pour l'ID, le casting, et la date.
2. **TheTVDB :**
* *Prioritaire pour les Séries TV.*
* Utilisé pour récupérer la structure des saisons et les **résumés d'épisodes** (souvent plus complets).


3. **OMDb API (IMDb) :**
* Utilisé pour récupérer les **Notes Critiques** (Rotten Tomatoes / Metascore).
* Utilisé si le résumé TMDB est trop court (chercher le champ `plot=full`).


4. **Fanart.tv :**
* *Obligatoire pour le Hero.*
* Récupération des `HD Movie Logo` (Titre transparent) et `Movie Background` (Sans texte).



### B. Logique de "Richesse"

* **Règle du Résumé :** Comparer la longueur du texte (`string.length`) entre TMDB et OMDb/TVDB. Afficher toujours le plus long.
* **Catégories Intelligentes :** Ne pas se limiter à "Action/Comédie". Utiliser les **codes cachés Netflix** pour créer des rails spécifiques :
* *Séries à binger en un week-end* (Code: 3182735)
* *Films Cultes* (Code: 7627)
* *Action & Aventure Spy* (Code: 10702)



---

## 4. ⚡ Transitions & Performance

* **Lazy Loading :** Les images des rails inférieurs ne chargent que lorsque l'utilisateur scrolle.
* **Placeholders (Squelettes) :**
* Pendant le chargement, afficher des rectangles gris bleutés (`#1E293B`) avec une animation de pulsation (*pulse*), jamais de noir vide.


* **Navigation Fluide (SPA) :** Pas de rechargement de page complet entre "Accueil" et "Détails". Utiliser des transitions de page (ex: l'affiche du film s'agrandit pour devenir le fond de la page suivante).

