# VidSrc.to API (Embed + VAPI)

## Identifiants

- `{id}` (requis) : ID IMDb ou TMDB.
- Les IDs IMDb doivent commencer par `tt`.

## Embed film

```text
https://vidsrc.to/embed/movie/{id}
```

**Exemples**

```text
https://vidsrc.to/embed/movie/tt17048514
https://vidsrc.to/embed/movie/927085
```

## Embed series TV

### Serie complete

```text
https://vidsrc.to/embed/tv/{id}
```

### Saison complete

```text
https://vidsrc.to/embed/tv/{id}/{season}
```

### Episode specifique

```text
https://vidsrc.to/embed/tv/{id}/{season}/{episode}
```

## Endpoints VAPI (nouveautes)

Le parametre `page` est optionnel.

```text
https://vidsrc.to/vapi/movie/new
https://vidsrc.to/vapi/movie/add
https://vidsrc.to/vapi/tv/new
https://vidsrc.to/vapi/tv/add
https://vidsrc.to/vapi/episode/latest
```

## Sous-titres personnalises

### Fichier unique

- `sub_file` : URL encodee vers un fichier `.vtt`
- `sub_label` (optionnel) : label de la piste

### Fichiers multiples

- `sub.info` : URL vers un JSON contenant `file`, `label`, `kind`

### Contrainte CORS

Les URLs de sous-titres doivent repondre avec :

```text
Access-Control-Allow-Origin: *
```
