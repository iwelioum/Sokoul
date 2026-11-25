# Embed hosts — protocol sheets

Facts about how each embed host serves its media, written in Sokoul's own words. The Kotlin extractors in this package
are implemented from these sheets only. "UA" means an ordinary desktop Chrome User-Agent. "Playback headers" are the
headers the player must send with the media URL and its segments.

Common shapes:

- **Packed script**: many players hide their setup inside the classic JavaScript packer,
  `eval(function(p,a,c,k,e,d){…}('<template>', <radix>, <count>, '<w0|w1|…>'.split('|'), …))`. Every word of the
  template is a number written in base `radix` (digits `0-9`, then `a-z` for 10–35, then `A-Z` for 36–61); word number
  `n` stands for dictionary entry `n`, or for itself when that entry is empty. Unpacking is a pure text substitution.
- **Script hop**: some hosts first answer a small page that sends the browser elsewhere, by
  `window.location.href = '…'`, by `<meta http-equiv="refresh" content="0;url=…">`, or by printing the
  `https://<mirror>/e/<id>` address of the real player. Following at most three such hops (Referer = the previous page)
  reaches the player.

## Voe

- Domains: `voe.sx` and any `voe.<tld>`; the "unblock" family (`voeunblock.<tld>`, `voe-unblock3.<tld>`,
  `un-block-voe.<tld>`…); and a long, monthly-rotating list of throw-away names (see `Voe.ALIASES`).
- Fetch the embed page with UA and `Referer: https://voe.sx/`, following script hops until the page carries a
  `<script type="application/json">`.
- Encrypted pages: `<script type="application/json">["<payload>"]</script>` is immediately followed by
  `<script src="<bundle>">`. The bundle (fetched with the page as Referer) contains, as its first array of two-character
  non-word strings in single quotes, the list of *markers* of the day. Older players used the markers
  `@$ ^^ ~@ %? *~ !! #&`.
- Decoding the payload: apply ROT13 to letters, delete every marker occurrence, Base64-decode, subtract 3 from every
  character code, reverse the string, Base64-decode again: the result is a JSON object. The stream is in `source`,
  `file` or `direct_access_url` (prefer the one that is an `.m3u8`).
- Plain pages instead carry `"hls": "<url>"` or `"mp4": "<url>"` (slashes may be escaped).
- Playback headers: `Referer: https://voe.sx/`, `Origin: https://voe.sx`.

## Vidmoly (and `ansembed.net`)

- Domains: `vidmoly.<tld>` (`.to`, `.org`, `.net`…); `ansembed.net` serves the same player under another name.
- Fetch the embed page with `Referer: https://voirdrama.to/` (the host wants a partner site as Referer), following
  script hops.
- Media: `sources: [{file: "<url>"}]`, else a `file: "<…m3u8>"`, else any `.m3u8` address in the page. An `.mpd` (DASH)
  answer is not playable by Sokoul's players.
- Playback headers: `Referer: https://voirdrama.to/`, `Origin: https://voirdrama.to`.

## Sibnet

- Domains: `sibnet.ru` and its sub-domains (`video.sibnet.ru`, CDN hosts `dvNN.sibnet.ru`).
- Links come as `/shell.php?videoid=<n>` or as permalinks `/video<n>-<title>.html`; only the first serves the player,
  so always ask `https://video.sibnet.ru/shell.php?videoid=<n>`.
- The player script holds `player.src([{src: "<path>"…}])`; the path is relative to `https://video.sibnet.ru` and is
  an `.mp4` today (do not insist on the extension).
- That address answers a redirect to a numbered CDN host. Resolve it with a `Range: bytes=0-0` request (Referer
  `https://video.sibnet.ru/`) and keep the final address, without downloading the video. If that fails, the first
  address still plays.
- Playback headers: `Referer: https://video.sibnet.ru/`, `Origin: https://video.sibnet.ru`.

## Uqload

- Domains: the registrable name is `uqload.<tld>`, where the TLD rotates (`.to`, `.cx`, `.is`, `.bz`, `.vc`…).
  Sub-domains (CDN) belong to it.
- Normalise any link to `<origin>/embed-<id>.html` (`id` = last path segment without `embed-` and `.html`,
  letters/digits/`_`/`-`). If that page fails, `<origin>/<id>.html` is the same video.
- Headers: UA, `Referer: https://uqload.<tld>/`, `Origin: https://uqload.<tld>` (the registrable domain, not the
  mirror's sub-domain).
- "File was deleted" in the page: the video is gone.
- Media: among the `https://` addresses of the page and of its unpacked script that are on an `uqload.<tld>` host,
  prefer `…/master.m3u8`, then any `.m3u8`, then `…/v.mp4`.
- Subtitles: the jwplayer setup has `tracks: [{file: "…", label: "…", kind: "captions", default: true}, …]`. Keep
  `captions`/`subtitles` (the default kind) that end in `.vtt`/`.srt` on an Uqload host; ignore thumbnails and the
  placeholder `empty.srt`/`empty.vtt` ("Upload captions"). The language is guessed from the label or from a
  `_fre`/`_eng` style file suffix. Uqload sends no CORS headers, so a browser player must fetch them with the Referer.
- Playback headers: the same `Referer`/`Origin` as the page.

## DoodStream

- Domains: a large family of names spelled `d`, then `o`/`0` repeated, then `d`, optionally `s`, `stream`, `ster`,
  `cdn` (`dood.to`, `d0000d.com`, `doodstream.com`, `dooood.com`…), plus `ds2play.com`, `ds2video.com`, `dsvplay.com`,
  `vidply.com`, `myvidplay.com`, `vide0.net`, `vvide0.com`, `all3do.com`, `do7go.com`, `doply.net`, `d-s.io`,
  `playmogo.com`. `doods.to` is **not** DoodStream (see Veev).
- Fetch the embed with UA and `Referer: <embed origin>/` (the host checks the Referer against the mirror it served).
  A `/d/<id>` link is a shell whose `<iframe src>` (or the same address with `/e/`) is the player.
- The player page mentions a path `/pass_md5/<a>/<b>`; the token is the `token=` value printed in the page, or else
  `<b>`. No such path: the file was deleted.
- `GET <player origin>/pass_md5/<a>/<b>` with `Referer: <player page>` answers, as plain text, the start of the media
  address. If it is on Cloudflare R2 (`cloudflarestorage.`) it is complete and signed. Otherwise the player appends ten
  random letters/digits and `?token=<token>&expiry=<now in ms>`.
- Playback headers: `Referer: <player origin>/`, `Origin: <player origin>`.

## LuluStream

- Domains: `lulustream.com`, `luluvdo.com`, `luluvdoo.com`, `luluvid.com`, `lulu.st` and spellings of
  `lulu(stream|vdo|vido…)` on `.com/.sbs/.site/.st`, plus `streamhihi.com`, `cdn1.site`, `d00ds.site`,
  `732eg54de642sa.sbs`. Mirrors redirect to each other (301).
- Fetch `<origin>/e/<id>` with `Referer: <origin>/`. The player is usually packed: look in the unpacked script first,
  then the raw page, for `sources: [{file: "<url>"}]` or any `.m3u8` address.
- The CDN binds its token to the `Accept-Language` of the request that obtained it: pin
  `fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7` on both the page request and playback.
- **Live-check risk**: the host is known to hand non-browser HTTP stacks a token its CDN refuses (403). Extraction may
  only work where the request leaves from a browser engine.
- Playback headers: `Referer: https://lulustream.com/`, `Origin: https://lulustream.com`, the pinned Accept-Language.

## Veev

- Domains: `veev`, `poophq` and `doods` on `.to`, `.com`, `.pro`.
- Fetch `<origin>/e/<id>` (Referer = that address, pinned Accept-Language as LuluStream). A redirect may change the
  file code: use the one of the final address.
- The page has no media address. It holds one or more *challenge* strings, assigned as `fc: '<s>'` or
  `_vvto[…] = '<s>'`; try the last one first. Each is LZW-compressed (character codes below 256 are literals, codes from
  256 up name dictionary entries built as usual); a string that decompresses to itself is not a challenge.
- The decompressed challenge is a string of digits describing groups of operations: a digit `n`, then `n` digits (the
  group, read in reverse order), then the next count, and so on; a non-digit counts as 0 and ends the list. Only the
  first group is used.
- `GET <origin>/dl?op=player_api&cmd=gi&file_code=<id>&ch=<challenge>&ie=1` (same headers) answers JSON
  `{"file": {"file_status": "OK", "dv": [{"s": "<encoded>"}]}}`. Anything else than `OK`: unavailable.
- Decoding `s`: LZW-decompress it, then for each operation of the group: if the operation is `1`, reverse the string;
  then read it as hexadecimal UTF-8 bytes and delete every `dXRmOA==`. The result must start with `http`.
- Playback headers: `Referer: https://veev.to/`, `Origin: https://veev.to`, the pinned Accept-Language.

## Vidara

- Domains: `vidara.to`, `vidara.so`.
- `POST <origin>/api/stream`, JSON body `{"filecode": "<id>", "device": "web"}`, `Referer: <origin>/e/<id>`. The answer
  `{"streaming_url": "<master.m3u8>"}` is the HLS master; no `streaming_url` means the video is unavailable.
- The token is bound to the caller's IP and short-lived: extraction and playback must leave from the same address.
- Playback headers: `Referer: https://vidara.to/`, `Origin: https://vidara.to`.

## SeekStreaming

- Domains: white-label domains that rotate; the embed is recognised by its shape, `https://<host>/#<id>`: root path,
  no query, the video id (letters, digits, `_`, `-`, at most 128) as the fragment. Links may arrive URL-encoded once or
  twice.
- `GET <origin>/api/v1/video?id=<id>&w=1920&h=1080&r=` with UA, `Accept: */*`, `Accept-Language: en-US,en;q=0.5`,
  `Origin: <origin>`, `Referer: <origin>/`.
- The answer is hexadecimal text (sometimes inside quotes) of AES-128-CBC with PKCS#7 padding; key = the ASCII bytes of
  `kiemtienmua911ca`, IV = the ASCII bytes of `1234567890oiuytr`. The plaintext is JSON; the stream is `cfNative` if
  present, else `source`, `master` or `masterUrl`.
- Playback headers: `Origin: <origin>`, `Referer: <origin>/`.

## Fsvid and Vidzy

- Domains: `fsvid.<tld>` (`fsvid.lol`, CDN `s1.fsvid.lol`…) and `vidzy.<tld>` (`vidzy.org`, `vidzy.cc`, `vN.vidzy.*`).
  Embed paths contain `/embed-` or `/embed/`.
- Fsvid refuses its own Referer ("Veuillez utiliser une URL valide"): ask with a French-Stream mirror as Referer
  (`https://fsmirror46.lol/`), French Accept-Language. Vidzy wants `Referer: https://vidzy.org/`.
- **Static part**: older pages (and the packed script) carry `sources: [{file: "<…m3u8>"}]`, `src:`/`file:` strings or
  a plain `.m3u8` string. Addresses containing `troll` are decoys. Only `https` masters are accepted.
- **Dynamic part (not ported)**: the current player builds the master address at run time: a Base64 string XOR-ed with a
  rolling key whose seed comes from browser measurements (the width of a hidden element sized in CSS units, the sum of
  the host name's character codes). Without a JavaScript engine and a layout model this cannot be read statically, so
  these pages answer `Unsupported`. (The derived runtime handled them with QuickJS; Sokoul's own code does not.)
- **Gate key**: without a `gk` parameter the CDN serves only the first 9–19 segments. The key comes from a "gate"
  (`https://v6.vidzy.cc/gate.php` for Vidzy, whose page may name another `…/gate.php` on a Vidzy/Fsvid host;
  `https://fsvid.lol/gk` for Fsvid; one key works for both) asked with a browser fingerprint query
  `v=p1&cw=159.56&cw2=170.34&wg=1&dpr=1&sw=1920&hw=8&ft=TOP&rf=`; it answers `{"gk": "<key>"}` (16–512 characters of
  `A-Za-z0-9_.-`; the key ends with `.<issued>.<lifetime in s>`). Append `gk=<key>` to the master. Without a key the
  master still plays, but stops early.
- Playback headers: Fsvid CDN `Referer: https://fsvid.lol/`; Vidzy `Referer: https://vidzy.org/`; both with `Origin`
  and the session cookie `vz_pe=<t>; fsv_pe=<t>`, where `<t>` is the master's `t` query value (16–128 characters).
