package com.sokoul.pc.setup

import com.sokoul.pc.data.L

/**
 * One service a user can connect, as the "API et sources" page (and later the web client) explains it: what it adds,
 * what it costs, how to get the key in a few steps, and what stays private. [link] is the official page where the key
 * is found; it is also shown as a QR so the user can open it on a phone.
 *
 * Words shown on a service's own site (a button, a menu) are quoted «…» in French and “…” in English.
 */
data class ServiceGuide(
    val id: String,
    val name: String,
    val adds: L,
    val cost: L,
    val steps: List<L>,
    /** The official page where the account or key is made, shown as a QR on the TV; null when there is none. */
    val link: String?,
    val linkLabel: L?,
    val privacy: L,
    val optional: Boolean = true,
    /** Only where the format is certain: catches a paste of the wrong value before asking the service. */
    val keyFormat: Regex? = null,
    /** The day the facts below were checked against the service's own pages. */
    val verified: String,
)

object ServiceGuides {
    private const val CHECKED = "2026-11-25"

    // The Sokoul project page: the only official page for what runs on the user's own machines.

    private val keyPrivacy = L(
        "Your key stays on this device, or in your own vault on your Sokoul server. It is never shown in full: only its last 4 characters.",
        "Votre clé reste sur cet appareil, ou dans votre propre coffre sur votre serveur Sokoul. Elle n'est jamais affichée en entier : seulement ses 4 derniers caractères.",
    )

    val all: List<ServiceGuide> = listOf(
        // No official page: each IPTV provider sends its own details. Formats from CredentialValidator / OnboardingModel.
        ServiceGuide(
            id = "iptv",
            name = "IPTV",
            adds = L(
                "Your provider's live channels, TV guide, films and series, in Sokoul.",
                "Les chaînes en direct, le guide TV, les films et les séries de votre fournisseur, dans Sokoul.",
            ),
            cost = L(
                "Paid to your IPTV provider, not to Sokoul. Sokoul shows the end date and the number of screens when the provider gives them.",
                "Payant auprès de votre fournisseur IPTV, pas de Sokoul. Sokoul affiche la date de fin et le nombre d'écrans quand le fournisseur les donne.",
            ),
            steps = listOf(
                L(
                    "Find the e-mail or customer area of your IPTV provider.",
                    "Retrouvez l'e-mail ou l'espace client de votre fournisseur IPTV.",
                ),
                L(
                    "If it gives a server address, a username and a password (the “Xtream Codes” format), choose “Server and sign-in”.",
                    "S'il donne une adresse de serveur, un identifiant et un mot de passe (le format « Xtream Codes »), choisissez « Serveur et identifiants ».",
                ),
                L(
                    "Type the server address with its port, for example http://exemple.tv:8080, then the username and password.",
                    "Saisissez l'adresse du serveur avec son port, par exemple http://exemple.tv:8080, puis l'identifiant et le mot de passe.",
                ),
                L(
                    "If it gives a single link instead (it often contains get.php), choose “Playlist link (M3U)” and paste it.",
                    "S'il donne plutôt un seul lien (il contient souvent get.php), choisissez « Lien de playlist (M3U) » et collez-le.",
                ),
                L(
                    "Press “Check and save”: Sokoul asks your provider before keeping anything.",
                    "Appuyez sur « Vérifier et enregistrer » : Sokoul interroge votre fournisseur avant de garder quoi que ce soit.",
                ),
            ),
            link = null,
            linkLabel = null,
            privacy = L(
                "Your password stays on this device, or in your own vault on your Sokoul server. It is never shown again once saved.",
                "Votre mot de passe reste sur cet appareil, ou dans votre propre coffre sur votre serveur Sokoul. Il n'est plus jamais affiché une fois enregistré.",
            ),
            verified = CHECKED,
        ),
        // https://api.real-debrid.com/ — device sign-in at https://real-debrid.com/device; private token at
        // https://real-debrid.com/apitoken ("never use your private API token for public applications").
        // Premium prices: not readable on the official site (403 to robots) — kept generic. // unverified (prices)
        ServiceGuide(
            id = "realdebrid",
            name = "Real-Debrid",
            adds = L(
                "Films and series in high quality, played through your Real-Debrid account.",
                "Des films et des séries en haute qualité, lus grâce à votre compte Real-Debrid.",
            ),
            cost = L(
                "Paid: a Premium subscription is needed. Prices are shown on real-debrid.com.",
                "Payant : il faut un abonnement Premium. Les prix sont affichés sur real-debrid.com.",
            ),
            steps = listOf(
                L(
                    "Create an account on real-debrid.com and take a Premium subscription.",
                    "Créez un compte sur real-debrid.com et prenez un abonnement Premium.",
                ),
                L(
                    "In Sokoul, choose “Sign in with Real-Debrid”: a code and a QR appear.",
                    "Dans Sokoul, choisissez « Se connecter avec Real-Debrid » : un code et un QR s'affichent.",
                ),
                L(
                    "Scan the QR with your phone, or go to real-debrid.com/device and type the code.",
                    "Scannez le QR avec votre téléphone, ou allez sur real-debrid.com/device et saisissez le code.",
                ),
                L(
                    "Confirm on your phone: Sokoul connects by itself, no key to type.",
                    "Confirmez sur votre téléphone : Sokoul se connecte tout seul, aucune clé à saisir.",
                ),
                L(
                    "Other way: copy your private token from real-debrid.com/apitoken and paste it in “Use a private token instead”.",
                    "Autre moyen : copiez votre jeton privé sur real-debrid.com/apitoken et collez-le dans « Utiliser plutôt un jeton privé ».",
                ),
            ),
            link = "https://real-debrid.com/device",
            linkLabel = L("Real-Debrid sign-in page", "Page de connexion Real-Debrid"),
            privacy = L(
                "Your sign-in stays on this device, or in your own vault on your Sokoul server, and is never shown in full. The private token opens your whole account: never share it.",
                "Votre connexion reste sur cet appareil, ou dans votre propre coffre sur votre serveur Sokoul, et n'est jamais affichée en entier. Le jeton privé ouvre tout votre compte : ne le partagez jamais.",
            ),
            verified = CHECKED,
        ),
        // https://developer.themoviedb.org/docs/getting-started — key at https://www.themoviedb.org/settings/api,
        // "use a desktop computer"; https://developer.themoviedb.org/docs/faq — free for non-commercial use, credit TMDB.
        // Sokoul sends it as `api_key=` (v3), so the short "API Key" is needed, not the long "Read Access Token".
        ServiceGuide(
            id = "tmdb",
            name = "TMDB",
            adds = L(
                "The Movie Database: the catalogue, posters, summaries, cast and trailers Sokoul shows everywhere.",
                "The Movie Database : le catalogue, les affiches, les résumés, la distribution et les bandes-annonces que Sokoul affiche partout.",
            ),
            cost = L(
                "Free for personal, non-commercial use.",
                "Gratuit pour un usage personnel, non commercial.",
            ),
            steps = listOf(
                L("Create a free account on themoviedb.org and confirm your e-mail.", "Créez un compte gratuit sur themoviedb.org et confirmez votre e-mail."),
                L(
                    "On a computer, open “Settings”, then “API” (themoviedb.org/settings/api).",
                    "Sur un ordinateur, ouvrez « Paramètres », puis « API » (themoviedb.org/settings/api).",
                ),
                L(
                    "Ask for a key, accept the terms and fill in the short form for personal use.",
                    "Demandez une clé, acceptez les conditions et remplissez le court formulaire pour un usage personnel.",
                ),
                L(
                    "Copy the short “API Key” (32 characters), not the long “Read Access Token”.",
                    "Copiez la « Clé d'API » courte (32 caractères), pas le long « Jeton d'accès en lecture ».",
                ),
                L("Paste it in Sokoul and press “Check and save”.", "Collez-la dans Sokoul et appuyez sur « Vérifier et enregistrer »."),
            ),
            link = "https://www.themoviedb.org/settings/api",
            linkLabel = L("TMDB key page", "Page de la clé TMDB"),
            privacy = keyPrivacy,
            keyFormat = Regex("^[0-9a-f]{32}$"),
            verified = CHECKED,
        ),
        // https://opensubtitles.tawk.help/article/getting-started — sign up at https://www.opensubtitles.com/en/users/sign_up,
        // keys at https://www.opensubtitles.com/en/consumers; 5 downloads / 24 h per IP without sign-in (Sokoul does not sign in).
        ServiceGuide(
            id = "opensubtitles",
            name = "OpenSubtitles",
            adds = L(
                "Subtitles in French, English and other languages for the film or episode you are watching.",
                "Des sous-titres en français, en anglais et dans d'autres langues pour le film ou l'épisode que vous regardez.",
            ),
            cost = L(
                "Free account. Downloads are limited per day: the limit is shown on opensubtitles.com.",
                "Compte gratuit. Les téléchargements sont limités par jour : la limite est indiquée sur opensubtitles.com.",
            ),
            steps = listOf(
                L("Create a free account on opensubtitles.com and confirm your e-mail.", "Créez un compte gratuit sur opensubtitles.com et confirmez votre e-mail."),
                L(
                    "Open your profile menu, then “API consumers” (opensubtitles.com/en/consumers).",
                    "Ouvrez le menu de votre profil, puis « API consumers » (opensubtitles.com/en/consumers).",
                ),
                L("Create a new consumer: any name works, for example Sokoul.", "Créez un nouveau « consumer » : n'importe quel nom convient, par exemple Sokoul."),
                L("Copy its key and paste it in Sokoul.", "Copiez sa clé et collez-la dans Sokoul."),
            ),
            link = "https://www.opensubtitles.com/en/consumers",
            linkLabel = L("OpenSubtitles key page", "Page de la clé OpenSubtitles"),
            privacy = keyPrivacy,
            verified = CHECKED,
        ),
        // subdl.com is behind a browser check (403 to robots). Steps from the SubDL Stremio add-on guide
        // https://guides.viren070.me/stremio/addons/subdl-subtitles : register at https://subdl.com/panel/register,
        // key at https://subdl.com/panel/api ("Copy API Code"). // unverified (official page not readable; limits unknown)
        ServiceGuide(
            id = "subdl",
            name = "Subdl",
            adds = L(
                "More subtitles, looked up when OpenSubtitles doesn't have enough.",
                "Plus de sous-titres, cherchés quand OpenSubtitles n'en a pas assez.",
            ),
            cost = L(
                "Free account. Any limit is shown on subdl.com.",
                "Compte gratuit. Une éventuelle limite est indiquée sur subdl.com.",
            ),
            steps = listOf(
                L("Create a free account on subdl.com.", "Créez un compte gratuit sur subdl.com."),
                L("Open the key page of your account (subdl.com/panel/api).", "Ouvrez la page de clé de votre compte (subdl.com/panel/api)."),
                L("Press “Copy API Code”.", "Appuyez sur « Copy API Code »."),
                L("Paste it in Sokoul.", "Collez-la dans Sokoul."),
            ),
            link = "https://subdl.com/panel/api",
            linkLabel = L("Subdl key page", "Page de la clé Subdl"),
            privacy = keyPrivacy,
            verified = CHECKED,
        ),
        // https://www.omdbapi.com/apikey.aspx — "FREE! (1,000 daily limit)" or Patreon; key sent by e-mail.
        ServiceGuide(
            id = "omdb",
            name = "OMDb",
            adds = L(
                "IMDb and Rotten Tomatoes ratings, and awards, on title pages.",
                "Les notes IMDb et Rotten Tomatoes, et les récompenses, sur les fiches.",
            ),
            cost = L("Free: 1,000 requests a day. Patreon supporters get more.", "Gratuit : 1 000 requêtes par jour. Les soutiens Patreon en ont davantage."),
            steps = listOf(
                L("Open omdbapi.com/apikey.aspx.", "Ouvrez omdbapi.com/apikey.aspx."),
                L("Choose “FREE!” and type your e-mail address.", "Choisissez « FREE! » et saisissez votre adresse e-mail."),
                L(
                    "Open the e-mail OMDb sends and follow its instructions. It can take a while with Outlook or Yahoo addresses.",
                    "Ouvrez l'e-mail envoyé par OMDb et suivez ses instructions. Il peut tarder avec les adresses Outlook ou Yahoo.",
                ),
                L("Copy the key from the e-mail and paste it in Sokoul.", "Copiez la clé de l'e-mail et collez-la dans Sokoul."),
            ),
            link = "https://www.omdbapi.com/apikey.aspx",
            linkLabel = L("OMDb key page", "Page de la clé OMDb"),
            privacy = keyPrivacy,
            verified = CHECKED,
        ),
        // fanart.tv is behind a browser check (403 to robots). Key page https://fanart.tv/get-an-api-key/ cited by
        // several guides; personal keys show newer images sooner (fanart.tv blog on Medium). // unverified (official page not readable)
        ServiceGuide(
            id = "fanart",
            name = "fanart.tv",
            adds = L(
                "Title logos and wide, high-quality artwork for posters and backgrounds.",
                "Les logos des titres et de grands visuels de qualité pour les affiches et les fonds.",
            ),
            cost = L("Free account.", "Compte gratuit."),
            steps = listOf(
                L("Create a free account on fanart.tv.", "Créez un compte gratuit sur fanart.tv."),
                L("Signed in, open fanart.tv/get-an-api-key.", "Une fois connecté, ouvrez fanart.tv/get-an-api-key."),
                L("Copy your personal key.", "Copiez votre clé personnelle."),
                L("Paste it in Sokoul.", "Collez-la dans Sokoul."),
            ),
            link = "https://fanart.tv/get-an-api-key/",
            linkLabel = L("fanart.tv key page", "Page de la clé fanart.tv"),
            privacy = keyPrivacy,
            verified = CHECKED,
        ),
        // https://thetvdb.com/api-information/signup — free under $50k/year revenue, attribution required;
        // https://github.com/thetvdb/v4-api — keys at https://www.thetvdb.com/dashboard/account/apikey.
        ServiceGuide(
            id = "tvdb",
            name = "TheTVDB",
            adds = L(
                "Complete episode lists, with titles in French, when TMDB is missing some.",
                "Des listes d'épisodes complètes, avec les titres en français, quand il en manque chez TMDB.",
            ),
            cost = L(
                "Free for personal projects (no income), with credit to TheTVDB.",
                "Gratuit pour les projets personnels (sans revenus), en citant TheTVDB.",
            ),
            steps = listOf(
                L("Create a free account on thetvdb.com.", "Créez un compte gratuit sur thetvdb.com."),
                L(
                    "Open the keys page of your dashboard (thetvdb.com/dashboard/account/apikey).",
                    "Ouvrez la page des clés de votre tableau de bord (thetvdb.com/dashboard/account/apikey).",
                ),
                L("Ask for a key and describe your use as personal, without income.", "Demandez une clé et décrivez votre usage comme personnel, sans revenus."),
                L("Copy the key and paste it in Sokoul.", "Copiez la clé et collez-la dans Sokoul."),
            ),
            link = "https://www.thetvdb.com/dashboard/account/apikey",
            linkLabel = L("TheTVDB key page", "Page de la clé TheTVDB"),
            privacy = keyPrivacy,
            verified = CHECKED,
        ),
        // https://www.tvmaze.com/api — public, no key, "at least 20 calls every 10 seconds" per IP, CC BY-SA 4.0.
        // Sokoul calls it without a key (ProviderProbe, TvmazeClient).
        ServiceGuide(
            id = "tvmaze",
            name = "TVmaze",
            adds = L(
                "The date of a series' next episode.",
                "La date du prochain épisode d'une série.",
            ),
            cost = L("Free, without an account.", "Gratuit, sans compte."),
            steps = listOf(
                L(
                    "Nothing to do: TVmaze needs no key, Sokoul uses it on its own.",
                    "Rien à faire : TVmaze ne demande aucune clé, Sokoul l'utilise tout seul.",
                ),
            ),
            link = "https://www.tvmaze.com/api",
            linkLabel = L("About TVmaze", "À propos de TVmaze"),
            privacy = L(
                "No key and no account: nothing personal is sent.",
                "Aucune clé ni compte : rien de personnel n'est envoyé.",
            ),
            verified = CHECKED,
        ),
        // https://api.watchmode.com/ — free plan: 2,500 monthly credits, non-commercial, no credit card, up to 3 countries
        // (https://api.watchmode.com/requestApiKey/). Where the key is shown after sign-up: not stated. // unverified (step 3)
        ServiceGuide(
            id = "watchmode",
            name = "Watchmode",
            adds = L(
                "Where else a title can be watched in your country, when Movie of the Night doesn't know.",
                "Où regarder un titre ailleurs dans votre pays, quand Movie of the Night ne le sait pas.",
            ),
            cost = L(
                "Free plan for personal use, no bank card, with a monthly limit shown on the site.",
                "Offre gratuite pour un usage personnel, sans carte bancaire, avec une limite mensuelle indiquée sur le site.",
            ),
            steps = listOf(
                L("Open api.watchmode.com/requestApiKey.", "Ouvrez api.watchmode.com/requestApiKey."),
                L("Create a free account with the free plan.", "Créez un compte gratuit avec l'offre gratuite."),
                L("Copy the key Watchmode gives you.", "Copiez la clé que Watchmode vous donne."),
                L("Paste it in Sokoul.", "Collez-la dans Sokoul."),
            ),
            link = "https://api.watchmode.com/requestApiKey/",
            linkLabel = L("Watchmode key page", "Page de la clé Watchmode"),
            privacy = keyPrivacy,
            verified = CHECKED,
        ),
        // TasteDive's own page (https://tastedive.com/read/api) refuses robots (403). Key request page
        // https://tastedive.com/account/api_access/ per https://tastedive-api-documentation.readthedocs.io/ (not official).
        // // unverified (cost and limit kept generic)
        ServiceGuide(
            id = "tastedive",
            name = "TasteDive",
            adds = L(
                "Recommendations of films similar to the ones you like.",
                "Des recommandations de films proches de ceux que vous aimez.",
            ),
            cost = L(
                "Free account, with a usage limit shown on the site.",
                "Compte gratuit, avec une limite d'utilisation indiquée sur le site.",
            ),
            steps = listOf(
                L("Create a free account on tastedive.com.", "Créez un compte gratuit sur tastedive.com."),
                L("Signed in, open tastedive.com/account/api_access.", "Une fois connecté, ouvrez tastedive.com/account/api_access."),
                L("Ask for an access key and copy it.", "Demandez une clé d'accès et copiez-la."),
                L("Paste it in Sokoul.", "Collez-la dans Sokoul."),
            ),
            link = "https://tastedive.com/account/api_access",
            linkLabel = L("TasteDive key page", "Page de la clé TasteDive"),
            privacy = keyPrivacy,
            verified = CHECKED,
        ),
        // https://www.movieofthenight.com/about/api — "Get started" → https://developers.movieofthenight.com/;
        // https://www.movieofthenight.com/about/api/pricing — Free plan: 1,000 requests per month.
        // Sokoul calls api.movieofthenight.com directly with X-API-Key. Dashboard steps not readable. // unverified (steps 2–3)
        ServiceGuide(
            id = "motn",
            name = "Movie of the Night",
            adds = L(
                "Which streaming services offer a title in your country (Netflix, Prime Video…).",
                "Sur quels services de streaming un titre est disponible dans votre pays (Netflix, Prime Video…).",
            ),
            cost = L(
                "Free plan: 1,000 requests a month. Paid plans offer more.",
                "Offre gratuite : 1 000 requêtes par mois. Les offres payantes en donnent davantage.",
            ),
            steps = listOf(
                L("Open developers.movieofthenight.com.", "Ouvrez developers.movieofthenight.com."),
                L("Create an account and choose the free plan.", "Créez un compte et choisissez l'offre gratuite."),
                L("Copy the key shown in your account.", "Copiez la clé affichée dans votre compte."),
                L("Paste it in Sokoul.", "Collez-la dans Sokoul."),
            ),
            link = "https://developers.movieofthenight.com/",
            linkLabel = L("Movie of the Night for developers", "Movie of the Night pour les développeurs"),
            privacy = keyPrivacy,
            verified = CHECKED,
        ),
        // The Sokoul server runs on the owner's PC (default http://127.0.0.1:25565, ProviderConfig.MAINAPI_DEFAULT_URL);
        // its access key is left in %LOCALAPPDATA%\SokoulApi\access.key (LocalServerKey). From the code, not the web.
        ServiceGuide(
            id = "mainapi",
            name = "Serveur Sokoul",
            adds = L(
                "More web and anime sources, from the PC at home that runs the Sokoul server.",
                "Plus de sources web et d'anime, depuis le PC de la maison qui fait tourner le serveur Sokoul.",
            ),
            cost = L("Free: it runs on your own PC.", "Gratuit : il tourne sur votre propre PC."),
            steps = listOf(
                L(
                    "On the PC that runs it, start the Sokoul server. On that PC, nothing else to do.",
                    "Sur le PC qui le fait tourner, lancez le serveur Sokoul. Sur ce PC, rien d'autre à faire.",
                ),
                L(
                    "Note the PC's address shown in the server's window, for example 192.168.1.20.",
                    "Notez l'adresse du PC affichée dans la fenêtre du serveur, par exemple 192.168.1.20.",
                ),
                L(
                    "On the TV, type this address in “PC address”. The TV and the PC must be on the same Wi-Fi.",
                    "Sur la TV, saisissez cette adresse dans « Adresse du PC ». La TV et le PC doivent être sur le même Wi-Fi.",
                ),
                L(
                    "If an access key is asked, it is on the PC, in the SokoulApi folder (file access.key).",
                    "Si une clé d'accès est demandée, elle se trouve sur le PC, dans le dossier SokoulApi (fichier access.key).",
                ),
            ),
            link = null,
            linkLabel = null,
            privacy = L(
                "The address and access key stay on this device and only lead to your own PC. The key is never shown in full.",
                "L'adresse et la clé d'accès restent sur cet appareil et ne mènent qu'à votre propre PC. La clé n'est jamais affichée en entier.",
            ),
            verified = CHECKED,
        ),
        // An AniProject-compatible server (Aniwatch and Consumet routes, AniProjectSources.kt) that the user runs
        // themselves; ProviderConfig.animeBackendUrl. No official page found. // unverified (link and cost)
        ServiceGuide(
            id = "animeBackend",
            name = "AniProject",
            adds = L(
                "Extra anime episodes, from an anime server you run yourself.",
                "Des épisodes d'anime en plus, depuis un serveur d'anime que vous faites tourner vous-même.",
            ),
            cost = L(
                "Nothing to pay to Sokoul: the server is yours.",
                "Rien à payer à Sokoul : le serveur est le vôtre.",
            ),
            steps = listOf(
                L(
                    "Run an AniProject-compatible anime server on a PC at home.",
                    "Faites tourner un serveur d'anime compatible AniProject sur un PC de la maison.",
                ),
                L("Note its address, for example http://192.168.1.20:3000.", "Notez son adresse, par exemple http://192.168.1.20:3000."),
                L("Type this address in Sokoul's Settings.", "Saisissez cette adresse dans les Réglages de Sokoul."),
                L(
                    "Sokoul uses it only once an address is entered; clear it to stop.",
                    "Sokoul ne l'utilise qu'une fois l'adresse saisie ; effacez-la pour arrêter.",
                ),
            ),
            link = null,
            linkLabel = null,
            privacy = L(
                "The address stays on this device. No account or key is sent.",
                "L'adresse reste sur cet appareil. Aucun compte ni clé n'est envoyé.",
            ),
            verified = CHECKED,
        ),
    )

    /** Ids stored under another name: the server address belongs to the "Serveur Sokoul" guide. */
    private val aliases = mapOf("mainapi_url" to "mainapi", "anime_backend_url" to "animeBackend")

    fun of(id: String): ServiceGuide? = (aliases[id] ?: id).let { key -> all.firstOrNull { it.id == key } }
}
