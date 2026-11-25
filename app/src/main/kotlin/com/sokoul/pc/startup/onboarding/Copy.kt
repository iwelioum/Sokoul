package com.sokoul.pc.startup.onboarding

import com.sokoul.pc.data.L
import java.text.DateFormat
import java.util.Date

/**
 * Every word of the first launch, in plain language: no "API", no "Xtream" on its own, no HTTP code. Errors say what
 * happened and what to do.
 */
object Copy {
    // Welcome
    val welcomeTitle get() = L("Welcome to Sokoul", "Bienvenue sur Sokoul").text()
    val welcomeLead get() = L(
        "Your channels, films and series in one place, with the posters and details of a real streaming service.",
        "Vos chaînes, films et séries au même endroit, avec les affiches et les fiches d'un vrai service de streaming.",
    ).text()
    val welcomeTime get() = L("About 2 minutes · everything is optional", "Environ 2 minutes · tout est facultatif").text()
    val start get() = L("Get started", "Commencer").text()
    val later get() = L("Later", "Plus tard").text()
    val back get() = L("Back", "Retour").text()
    val next get() = L("Continue", "Continuer").text()
    val privacy get() = L(
        "Your sign-ins stay on this device. They are never shown in full.",
        "Vos identifiants restent sur cet appareil. Ils ne sont jamais affichés en entier.",
    ).text()

    // Steps (rail)
    val stepWelcome get() = L("Welcome", "Bienvenue").text()
    val stepSources get() = L("Your sources", "Vos sources").text()
    val stepPreferences get() = L("Viewing", "Visionnage").text()
    val stepReady get() = L("Ready", "Prêt").text()
    fun stepOf(n: Int, total: Int) = L("Step $n of $total", "Étape $n sur $total").text()

    // Sources
    val sourcesTitle get() = L("Add what you already have", "Ajoutez ce que vous avez déjà").text()
    val sourcesLead get() = L(
        "Each source brings something different. Add one, several or none: you can finish later in Settings.",
        "Chaque source apporte quelque chose de différent. Ajoutez-en une, plusieurs ou aucune : vous pourrez compléter plus tard dans Réglages.",
    ).text()
    val iptvTitle get() = L("TV channels", "Chaînes TV").text()
    val iptvBenefit get() = L("Live channels, guide and your provider's films (IPTV)", "Les chaînes en direct, le guide et les films de votre fournisseur (IPTV)").text()
    val debridTitle get() = "Real-Debrid"
    val debridBenefit get() = L("Films and series in high quality", "Des films et séries en haute qualité").text()
    val subtitlesTitle get() = L("Subtitles", "Sous-titres").text()
    val subtitlesBenefit get() = L("French and English subtitles for everything you watch", "Des sous-titres en français et en anglais pour tout ce que vous regardez").text()
    val serverTitle get() = L("Sokoul server", "Serveur Sokoul").text()
    val serverBenefit get() = L("More web and anime sources, from the PC that runs it", "Plus de sources web et d'anime, depuis le PC qui le fait tourner").text()
    val advancedTitle get() = L("Expert keys", "Clés expertes").text()
    val advancedBenefit get() = L("Your own keys for ratings, artwork and recommendations", "Vos propres clés pour les notes, les visuels et les recommandations").text()

    val statusReady get() = L("Ready", "Prêt").text()
    val statusChecking get() = L("Checking…", "Vérification…").text()
    val statusEmpty get() = L("Add", "Ajouter").text()
    val statusProblem get() = L("To fix", "À corriger").text()

    // IPTV sheet
    val iptvSheetLead get() = L(
        "Enter what your IPTV provider sent you, by e-mail or in your customer area.",
        "Saisissez ce que votre fournisseur IPTV vous a envoyé, par e-mail ou dans votre espace client.",
    ).text()
    val iptvTabAccount get() = L("Server and sign-in", "Serveur et identifiants").text()
    val iptvTabLink get() = L("Playlist link (M3U)", "Lien de playlist (M3U)").text()
    val fieldServer get() = L("Server address", "Adresse du serveur").text()
    val fieldServerHint get() = "http://exemple.tv:8080"
    val fieldUser get() = L("Username", "Identifiant").text()
    val fieldPassword get() = L("Password", "Mot de passe").text()
    val fieldLink get() = L("Playlist link", "Lien de la playlist").text()
    val fieldLinkHint get() = "http://…/get.php?…"
    val checkAndSave get() = L("Check and save", "Vérifier et enregistrer").text()
    val checking get() = L("Checking with your provider…", "Vérification auprès de votre fournisseur…").text()
    val show get() = L("Show", "Afficher").text()
    val hide get() = L("Hide", "Masquer").text()
    val done get() = L("Done", "Terminé").text()

    val serverFormat get() = L("This doesn't look like an address. Example: http://exemple.tv:8080", "Cette adresse n'a pas le bon format. Exemple : http://exemple.tv:8080").text()
    val iptvIncomplete get() = L("Enter both the username and the password.", "Saisissez l'identifiant et le mot de passe.").text()
    val iptvRefused get() = L(
        "Your provider refused this username or password, or the subscription has ended.",
        "Votre fournisseur refuse cet identifiant ou ce mot de passe, ou l'abonnement est terminé.",
    ).text()
    val serverUnreachable get() = L(
        "The server doesn't answer. Check the address and the port, then try again.",
        "Le serveur ne répond pas. Vérifiez l'adresse et le port, puis réessayez.",
    ).text()
    val m3uFormat get() = L("Paste a link that starts with http:// or https://", "Collez un lien qui commence par http:// ou https://").text()
    val m3uRefused get() = L("This link doesn't lead to a playlist.", "Ce lien ne mène pas à une playlist.").text()
    val linkUnreachable get() = L("This link doesn't answer. Check it and try again.", "Ce lien ne répond pas. Vérifiez-le et réessayez.").text()
    val m3uReady get() = L("Playlist added", "Playlist ajoutée").text()
    val iptvSaved get() = L("Subscription saved", "Abonnement enregistré").text()

    fun iptvSummary(expiresAt: Long?, screens: Int?, trial: Boolean): String {
        val parts = buildList {
            add(if (trial) L("Trial active", "Essai actif").text() else L("Subscription active", "Abonnement actif").text())
            expiresAt?.let { add(L("until ", "jusqu'au ").text() + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) }
            screens?.takeIf { it > 0 }?.let { add(if (it == 1) L("1 screen", "1 écran").text() else L("$it screens", "$it écrans").text()) }
        }
        return parts.joinToString(" · ")
    }

    // Real-Debrid sheet
    val debridSheetLead get() = L(
        "Sign in from your phone: no key to type.",
        "Connectez-vous depuis votre téléphone : aucune clé à saisir.",
    ).text()
    val debridConnect get() = L("Sign in with Real-Debrid", "Se connecter avec Real-Debrid").text()
    val debridScan get() = L("Scan with your phone", "Scannez avec votre téléphone").text()
    fun debridOrVisit(url: String) = L("or go to $url and enter this code", "ou allez sur $url et saisissez ce code").text()
    val debridWaiting get() = L("Waiting for your confirmation…", "En attente de votre confirmation…").text()
    val debridExpired get() = L("The code has expired.", "Le code a expiré.").text()
    val debridNewCode get() = L("New code", "Nouveau code").text()
    val debridUseToken get() = L("Use a private token instead", "Utiliser plutôt un jeton privé").text()
    val debridTokenField get() = L("Private token (real-debrid.com/apitoken)", "Jeton privé (real-debrid.com/apitoken)").text()
    val debridRefused get() = L("Real-Debrid refused this sign-in. Try again.", "Real-Debrid refuse cette connexion. Réessayez.").text()
    val debridUnreachable get() = L("Real-Debrid doesn't answer. Check the connection and try again.", "Real-Debrid ne répond pas. Vérifiez la connexion et réessayez.").text()
    val debridSaved get() = L("Account connected", "Compte connecté").text()
    fun debridSummary(user: String?, days: Int?): String = listOfNotNull(
        user?.takeIf { it.isNotBlank() },
        when {
            days == null -> null
            days <= 0 -> L("no Premium", "sans Premium").text()
            else -> L("Premium: $days days left", "Premium : encore $days jours").text()
        },
    ).joinToString(" · ").ifEmpty { debridSaved }

    // Subtitles / keys
    val subtitlesReady get() = L("On", "Activés").text()
    val subtitlesLead get() = L(
        "Subtitles work with the keys built into Sokoul. Add your own only if you have one.",
        "Les sous-titres fonctionnent avec les clés intégrées à Sokoul. Ajoutez la vôtre seulement si vous en avez une.",
    ).text()
    val subtitlesNeedKey get() = L(
        "Add a free OpenSubtitles or Subdl key to turn subtitles on. One of the two is enough.",
        "Ajoutez une clé gratuite OpenSubtitles ou Subdl pour activer les sous-titres. L'une des deux suffit.",
    ).text()
    val keyMissing get() = L("Paste the key first.", "Collez d'abord la clé.").text()
    val keyRefused get() = L("The service refused this key. Check that it is complete.", "Le service refuse cette clé. Vérifiez qu'elle est complète.").text()
    val keyUnreachable get() = L("The service doesn't answer right now. Try again in a moment.", "Le service ne répond pas pour l'instant. Réessayez dans un moment.").text()
    val keySaved get() = L("Saved", "Enregistrée").text()
    val keyField get() = L("Key", "Clé").text()
    val save get() = L("Save", "Enregistrer").text()
    val checkAnyway get() = L("Check anyway", "Vérifier quand même").text()
    fun advancedSummary(n: Int, total: Int) = L("$n of $total keys", "$n clés sur $total").text()
    val advancedLead get() = L(
        "Sokoul already works without these. Each key adds something precise.",
        "Sokoul fonctionne déjà sans elles. Chaque clé ajoute quelque chose de précis.",
    ).text()
    val benefitCatalogue = L("Catalogue, posters and details", "Catalogue, affiches et fiches")
    val benefitRatings = L("Critics' ratings", "Notes des critiques")
    val benefitArtwork = L("Logos and wide artwork", "Logos et grands visuels")
    val benefitEpisodes = L("Episode lists and air dates", "Listes d'épisodes et dates de diffusion")
    val benefitWhere = L("Where else a title can be watched", "Où regarder un titre ailleurs")
    val benefitSimilar = L("Recommendations from what you like", "Recommandations selon vos goûts")

    // Server sheet
    val serverLead get() = L(
        "On the PC that runs the Sokoul server, its address is shown in its window. Example: 192.168.1.20",
        "Sur le PC qui fait tourner le serveur Sokoul, son adresse est affichée dans sa fenêtre. Exemple : 192.168.1.20",
    ).text()
    val serverField get() = L("PC address", "Adresse du PC").text()
    val serverKeyField get() = L("Access key (if asked)", "Clé d'accès (si demandée)").text()
    val sokoulUnreachable get() = L(
        "No Sokoul server answers at this address. Check that the PC is on and on the same Wi-Fi.",
        "Aucun serveur Sokoul ne répond à cette adresse. Vérifiez que le PC est allumé et sur le même Wi-Fi.",
    ).text()
    fun serverSummary(url: String) = L("Connected to ", "Connecté à ").text() + url.substringAfter("://").substringBefore('/')

    // Preferences
    val prefsTitle get() = L("How do you like to watch?", "Comment aimez-vous regarder ?").text()
    val prefsLead get() = L("Sokoul picks the right version for you. You can change it at any time.", "Sokoul choisit la bonne version pour vous. Vous pourrez changer à tout moment.").text()
    val audioTitle get() = L("Language", "Langue").text()
    val audioVf get() = L("French (VF)", "Français (VF)").text()
    val audioVostfr get() = L("Original with French subtitles (VOSTFR)", "Version originale sous-titrée (VOSTFR)").text()
    val audioVo get() = L("Original version (VO)", "Version originale (VO)").text()
    val audioAuto get() = L("Like my device", "Comme mon appareil").text()
    val trailersTitle get() = L("Trailers", "Bandes-annonces").text()
    val trailersOn get() = L("Play on their own", "Lecture automatique").text()
    val trailersOff get() = L("Only when I ask", "Seulement quand je le demande").text()

    // Ready
    val readyTitle get() = L("All set", "Tout est prêt").text()
    fun readyLead(n: Int) = if (n == 0) L(
        "You can explore the catalogue and trailers now, and add a source later in Settings.",
        "Vous pouvez explorer le catalogue et les bandes-annonces dès maintenant, et ajouter une source plus tard dans Réglages.",
    ).text() else L("Sokoul is loading your content. It appears as it arrives.", "Sokoul charge votre contenu. Il apparaît au fur et à mesure.").text()
    val notAdded get() = L("Not added · you can add it later in Settings", "Non ajouté · à ajouter plus tard dans Réglages").text()
    val enter get() = L("Enter Sokoul", "Entrer dans Sokoul").text()
}
