# VidLink — Documentation Complète de l'API

> **Biggest and Fastest Streaming API**
> 100K+ Films · 70K+ Séries · 5K+ Anime

---

## Table des matières

1. [Présentation](#présentation)
2. [Embed Movies](#embed-movies)
3. [Embed Shows (Séries)](#embed-shows)
4. [Embed Anime](#embed-anime)
5. [Paramètres de personnalisation](#paramètres-de-personnalisation)
6. [Personnalisation du lecteur (UI)](#personnalisation-du-lecteur-ui)
7. [Watch Progress (Continue Watching)](#watch-progress)
8. [Player Events](#player-events)

---

## Présentation

VidLink est une API de streaming embarqué qui permet d'intégrer facilement des films, séries et anime dans n'importe quel site web via des `<iframe>`.

### Points forts

| Fonctionnalité | Description |
|---|---|
| **Easy to use** | Intuitif et simple d'utilisation. Il suffit de copier le lien et de l'intégrer dans votre site |
| **Huge Library** | Films et séries scrapés depuis 13+ sources |
| **Customizable** | Personnalisation complète via des query parameters |
| **Auto Update** | Contenu ajouté chaque jour, mis à jour automatiquement |
| **Highest Quality** | Dernière qualité disponible, la plus rapide |

---

## Embed Movies

Le `TmdbId` est requis. Il provient de [The Movie Database API](https://www.themoviedb.org/).

### URL

```
https://vidlink.pro/movie/{tmdbId}
```

### Exemple de code

```html
<iframe
  src="https://vidlink.pro/movie/786892"
  frameborder="0"
  allowfullscreen>
</iframe>
```

---

## Embed Shows

### URL

```
https://vidlink.pro/tv/{tmdbId}/{saison}/{episode}
```

### Exemple de code

```html
<iframe
  src="https://vidlink.pro/tv/94605/2/1"
  frameborder="0"
  allowfullscreen>
</iframe>
```

---

## Embed Anime

> ⚠️ Fonctionnalité en **BETA**

Utilise l'ID MAL (MyAnimeList) pour retrouver et afficher l'anime.

### URL

```
https://vidlink.pro/anime/{malId}/{episode}
```

### Exemple de code

```html
<iframe
  src="https://vidlink.pro/anime/5/1"
  frameborder="0"
  allowfullscreen>
</iframe>
```

> **Note :** Si MalDB ne trouve pas l'anime, le fallback est utilisé automatiquement pour l'identifier grâce au titre.

---

## Paramètres de personnalisation

Chaque paramètre commence par `?` et les paramètres suivants sont séparés par `&`.
Les couleurs utilisent des **codes Hex sans le `#`**.

### Exemple complet

```
https://vidlink.pro/tv/94605/2/1?primaryColor=63b8bc&secondaryColor=a2a2a2&iconColor=eefdec&icons=default&player=default&title=true&poster=true&autoplay=false&nextbutton=false
```

---

### Tableau des paramètres

| Paramètre | Type | Valeur par défaut | Description |
|---|---|---|---|
| `primaryColor` | `hex` | — | Couleur principale du lecteur (sliders, contrôles autoplay) |
| `secondaryColor` | `hex` | — | Couleur de la barre de progression derrière les sliders |
| `icons` | `string` | `default` | Change le design des icônes dans le lecteur (`default` ou `vid`) |
| `backdropColor` | `hex` | — | Modifie la couleur du fond affiché derrière le lecteur |
| `title` | `boolean` | `true` | Affiche ou masque le titre du contenu |
| `poster` | `boolean` | `true` | Affiche ou masque le poster lors du chargement |
| `autoplay` | `boolean` | `false` | Active ou désactive la lecture automatique |
| `nextEpisodeButton` | `boolean` | `true` | Affiche ou masque le bouton "Épisode suivant" (séries TV) |
| `startEp` | `number` | — | Numéro de l'épisode de départ pour la lecture automatique |
| `player` | `string` | `default` | Change le template du lecteur (`default` ou `vid`) |
| `ad_fre` | `boolean` | `true` | Active ou désactive les publicités dans le lecteur |
| `vid_fre` | `boolean` | `true` | Active ou désactive le logo du site dans le player. **Nécessite une licence** |
| `referrer_url` | `string` | — | Redirect URL lorsque le stream échoue à se charger |
| `start` | `number` (secondes) | — | Démarre la vidéo au temps spécifié (en secondes). Ne remplace pas la progression sauvegardée |
| `sub_file` | `url` | — | Lien direct vers un fichier de sous-titre `.vtt` externe |
| `sub_label` | `string` | `External Subtitle` | Label pour la piste de sous-titres externe |
| `iconColor` | `hex` | — | Couleur des icônes dans le lecteur |

---

## Personnalisation du lecteur (UI)

VidLink propose une interface de personnalisation visuelle interactive pour prévisualiser le rendu avant d'intégrer le lecteur.

### Options disponibles

| Catégorie | Options |
|---|---|
| **Colors** | `#Inline`, `#HEIC`, `custom` |
| **Player** | `VidLik Player` / `JV Player` |
| **Options** | Autoplay ON/OFF, Next Button ON/OFF |

> Les options **VidLik Player** et **JV Player** sont des variantes d'interface avec des styles différents.

---

## Watch Progress

VidLink permet de **suivre la progression de visionnage** de vos utilisateurs à travers les films et séries TV. Cela active automatiquement une fonctionnalité **"Continue Watching"** sur votre site.

### Fonctionnement

Ajoutez ce script là où se trouve votre `<iframe>` :

```javascript
window.addEventListener('message', (event) => {
  if (event.origin !== 'https://vidlink.pro') return;

  if (event.data?.type === 'PLAYER_EVENT') {
    const { event: eventType, currentTime, duration } = event.data.data;
    console.log(`Player ${eventType} at ${currentTime}s of ${duration}s`);
  }
});
```

### Structure des données retournées (Event Data)

```json
{
  "76479": {
    "id": 76479,
    "type": "tv",
    "title": "The Boys",
    "poster_path": "/2zmTngn1tYC1AvfnrFLhxeD82hz.jpg",
    "progress": {
      "watched": 31.435372,
      "duration": 3609.867
    },
    "last_season_watched": "1",
    "last_episode_watched": "1",
    "show_progress": {
      "s1e1": {
        "season": "1",
        "episode": "1",
        "progress": {
          "watched": 31.435372,
          "duration": 3609.867
        }
      }
    }
  },
  "786892": {
    "id": 786892,
    "type": "movie",
    "title": "Furiosa: A Mad Max Saga",
    "poster_path": "/iADOJ8Zymht2JPMoy3R7xceZprc.jpg",
    "backdrop_path": "/wNAhuOZ3Zf84jCIlrcI6JhgmY5q.jpg",
    "progress": {
      "watched": 8726.904767,
      "duration": 8891.763
    },
    "last_updated": 1725723972695
  }
}
```

---

## Player Events

> ⚠️ Fonctionnalité en **BETA**

VidLink permet d'écouter les événements du lecteur pour suivre les interactions des utilisateurs et les états de lecture. Les événements sont envoyés via `postMessage` à la fenêtre parente.

### Événements disponibles

| Événement | Description |
|---|---|
| `PLAY` | Déclenché quand l'utilisateur lance la lecture |
| `PAUSE` | Déclenché quand l'utilisateur met en pause |
| `TIMEUPDATE` | Déclenché quand l'utilisateur change de source vidéo |
| `COMPLETE` | Déclenché quand la vidéo se termine |

### Implémentation

```javascript
window.addEventListener('message', (event) => {
  if (event.origin !== 'https://vidlink.pro') return;

  if (event.data?.type === 'PLAYER_EVENT') {
    const { event: eventType, currentTime, duration } = event.data.data;

    // Gérer l'événement
    console.log(`Player ${eventType} at ${currentTime}s of ${duration}s`);
  }
});
```

### Structure de l'événement (Event Data Structure)

```json
{
  "type": "PLAYER_EVENT",
  "data": {
    "event": "play | pause | timeupdate | complete",
    "currentTime": 42.5,
    "duration": 7200.0
  }
}
```

---

## Ressources utiles

- 🌐 Site officiel : [https://vidlink.pro](https://vidlink.pro)
- 🧪 Test Player : [https://vidlink.pro](https://vidlink.pro) → *Test the Player*
- 🗃️ The Movie Database API : [https://www.themoviedb.org](https://www.themoviedb.org)
- 📋 Changelog : disponible sur le site officiel

---

*Documentation reconstituée d'après la capture officielle de vidlink.pro*
