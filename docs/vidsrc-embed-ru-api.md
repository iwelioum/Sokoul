# VidSrc-Embed.ru API

## Embed film

**Endpoint**

```text
https://vidsrc-embed.ru/embed/movie
```

**Parametres**

- `imdb` ou `tmdb` (requis)
- `sub_url` (optionnel) : URL encodee `.srt` ou `.vtt` (CORS requis)
- `ds_lang` (optionnel) : langue de sous-titres par defaut (ISO639)
- `autoplay` (optionnel) : `1` ou `0` (active par defaut)

**Exemples**

```text
https://vidsrc-embed.ru/embed/movie/tt5433140
https://vidsrc-embed.ru/embed/movie?imdb=tt5433140
https://vidsrc-embed.ru/embed/movie?tmdb=385687
https://vidsrc-embed.ru/embed/movie?imdb=tt5433140&ds_lang=de
https://vidsrc-embed.ru/embed/movie?imdb=tt5433140&sub_url=https%3A%2F%2Fvidsrc.me%2Fsample.srt&autoplay=1
```

## Embed serie TV

**Endpoint**

```text
https://vidsrc-embed.ru/embed/tv
```

**Parametres**

- `imdb` ou `tmdb` (requis)
- `ds_lang` (optionnel)

**Exemples**

```text
https://vidsrc-embed.ru/embed/tv/tt0944947
https://vidsrc-embed.ru/embed/tv?imdb=tt0944947
https://vidsrc-embed.ru/embed/tv?tmdb=1399
https://vidsrc-embed.ru/embed/tv?tmdb=1399&ds_lang=de
```

## Embed episode TV

**Endpoint**

```text
https://vidsrc-embed.ru/embed/tv
```

**Parametres**

- `imdb` ou `tmdb` (requis)
- `season` (requis)
- `episode` (requis)
- `sub_url` (optionnel)
- `ds_lang` (optionnel)
- `autoplay` (optionnel)
- `autonext` (optionnel)

**Exemples**

```text
https://vidsrc-embed.ru/embed/tv/tt0944947/1-1
https://vidsrc-embed.ru/embed/tv?imdb=tt0944947&season=1&episode=1
https://vidsrc-embed.ru/embed/tv?imdb=tt0944947&season=1&episode=1&ds_lang=de
https://vidsrc-embed.ru/embed/tv?tmdb=1399&season=1&episode=1&autoplay=1&autonext=1
```

## Flux JSON (latest)

`PAGE_NUMBER` est requis.

```text
https://vidsrc-embed.ru/movies/latest/page-1.json
https://vidsrc-embed.ru/tvshows/latest/page-1.json
https://vidsrc-embed.ru/episodes/latest/page-1.json
```
