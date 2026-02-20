# AutoEmbed API (player.autoembed.cc)

## Identifiants

- `{id}` (requis) : ID IMDb ou TMDB.
- Les IDs IMDb doivent commencer par `tt`.

## Embed film

**Endpoint**

```text
https://player.autoembed.cc/embed/movie/{id}
```

**Exemples**

```text
https://player.autoembed.cc/embed/movie/tt3359350
https://player.autoembed.cc/embed/movie/359410
```

## Embed episode TV

**Endpoint**

```text
https://player.autoembed.cc/embed/tv/{id}/{season}/{episode}
```

**Parametres**

- `{season}` (requis) : numero de saison
- `{episode}` (requis) : numero d'episode

**Exemples**

```text
https://player.autoembed.cc/embed/tv/tt0903747/1/1
https://player.autoembed.cc/embed/tv/1396/1/1
```

## Selection du serveur

Ajoute `?server=<numero>` a l'URL d'embed.

```text
https://player.autoembed.cc/embed/tv/1396/1/1?server=2
https://player.autoembed.cc/embed/movie/tt3359350?server=2
```
