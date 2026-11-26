package com.sokoul.pc.data

import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.i18n.Strings

/** A text in the base language, English, with its French version. */
data class L(val en: String, val fr: String) {
    fun text(): String = Strings.pick(en, fr)
}

/** What a row asks TMDB for. Keywords are English names, resolved to ids at request time. */
data class TitleQuery(
    val genres: Set<Int> = emptySet(),
    val noGenres: Set<Int> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val minRating: Double? = null,
    val minVotes: Int? = null,
    val maxVotes: Int? = null,
    val minRuntime: Int? = null,
    val maxRuntime: Int? = null,
    val language: String? = null,
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val miniSeries: Boolean = false,
)

enum class Kicker(val label: L) {
    Trending(L("TRENDING", "TENDANCE")),
    ForYou(L("FOR YOU", "POUR VOUS")),
    New(L("NEW", "NOUVEAUTÉS")),
    Sagas(L("SAGAS", "SAGAS")),
    Platforms(L("PLATFORMS", "PLATEFORMES")),
    Era(L("ERA", "ÉPOQUE")),
    Essentials(L("ESSENTIALS", "INCONTOURNABLES")),
    Moods(L("MOODS", "AMBIANCES")),
    Catalog(L("CATALOG", "CATALOGUE")),
    Theme(L("THEME", "THÈME")),
    Genre(L("GENRE", "GENRE")),
    World(L("WORLD CINEMA", "CINÉMA DU MONDE")),
    Selection(L("SELECTION", "SÉLECTION")),
}

data class RowSpec(val id: String, val kind: String, val title: L, val tagline: L, val kicker: Kicker, val query: TitleQuery)

enum class Mood(val title: L, val movieTitle: L, val seriesTitle: L, val tagline: L, private val movie: TitleQuery, private val series: TitleQuery) {
    Chills(
        L("Chills", "Frissons"), L("Guaranteed Chills", "Frissons garantis"), L("Guaranteed Chills", "Frissons garantis"),
        L("Watch with the lights on.", "À regarder les lumières allumées."),
        TitleQuery(genres = setOf(G.HORROR, G.THRILLER), minRating = 6.2, minVotes = 300),
        TitleQuery(genres = setOf(G.MYSTERY, G.TV_SCIFI), keywords = setOf("supernatural", "survival", "suspense"), minRating = 6.2),
    ),
    Light(
        L("Light Hearted", "Esprit léger"), L("To Unwind", "Pour se détendre"), L("To Unwind", "Pour se détendre"),
        L("Laughs, smiles and nothing else.", "Rire, sourire, et rien d'autre."),
        TitleQuery(genres = setOf(G.COMEDY, G.ROMANCE, G.ANIMATION), noGenres = setOf(G.DRAMA, G.WAR, G.HORROR), maxRuntime = 114, minVotes = 300),
        TitleQuery(genres = setOf(G.COMEDY, G.ANIMATION), noGenres = setOf(G.DRAMA, G.CRIME, G.TV_WAR), minVotes = 100),
    ),
    Family(
        L("With Family", "En famille"), L("For the Whole Family", "À voir en famille"), L("For the Whole Family", "À voir en famille"),
        L("From the youngest to the oldest, everyone follows.", "Des petits aux grands, tout le monde suit."),
        TitleQuery(genres = setOf(G.FAMILY), noGenres = setOf(G.HORROR, G.THRILLER, G.CRIME, G.WAR), minVotes = 300),
        TitleQuery(genres = setOf(G.FAMILY, G.TV_KIDS), noGenres = setOf(G.CRIME, G.TV_WAR, G.TV_ACTION), minVotes = 50),
    ),
    Puzzle(
        L("Mind Games", "Puzzle mental"), L("Films That Twist Your Mind", "Films qui retournent le cerveau"), L("Series That Twist Your Mind", "Séries qui retournent le cerveau"),
        L("Nothing is what it seems.", "Rien n'est ce qu'il paraît."),
        TitleQuery(genres = setOf(G.SCIFI, G.MYSTERY), keywords = setOf("twist ending", "plot twist", "nonlinear timeline", "dystopia", "mind game", "time loop"), minRating = 7.0),
        TitleQuery(genres = setOf(G.TV_SCIFI, G.MYSTERY), keywords = setOf("twist ending", "plot twist", "nonlinear timeline", "dystopia", "mind game", "time loop"), minRating = 7.0),
    ),
    Emotions(
        L("All the Feels", "À fleur de peau"), L("Big Emotions", "Grandes émotions"), L("Big Emotions", "Grandes émotions"),
        L("Keep the tissues close.", "Prévoyez les mouchoirs."),
        TitleQuery(genres = setOf(G.DRAMA, G.HISTORY, G.ROMANCE), minRating = 7.2, minVotes = 500),
        TitleQuery(genres = setOf(G.DRAMA), minRating = 7.2, minVotes = 200),
    ),
    Adrenaline(
        L("Adrenaline", "Adrénaline"), L("High Tension", "Haute tension"), L("High Tension", "Haute tension"),
        L("Your heart won't slow down.", "Le cœur ne redescend pas."),
        TitleQuery(genres = setOf(G.ACTION, G.CRIME, G.THRILLER), noGenres = setOf(G.ANIMATION, G.FAMILY), minRating = 6.3, minVotes = 500),
        TitleQuery(genres = setOf(G.TV_ACTION, G.CRIME), noGenres = setOf(G.ANIMATION, G.FAMILY, G.TV_KIDS), minVotes = 100),
    ),
    ;

    fun spec(kind: String): RowSpec = RowSpec(
        "$kind-mood-${name.lowercase()}", kind, if (kind == "tv") seriesTitle else movieTitle, tagline, Kicker.Moods, if (kind == "tv") series else movie,
    )
}

/**
 * Streaming platforms and what they produce themselves: series by TMDB network, films by TMDB company, when one exists
 * (checked against TMDB's names). The logo comes from TMDB's provider list.
 */
enum class Platform(val providerId: Int, val label: String, val color: Long, val accent: Long, val networks: String, val companies: String? = null) {
    Netflix(8, "Netflix", 0xFF831010, 0xFFE50914, "213", "178464"),
    Prime(119, "Prime Video", 0xFF0B3A5C, 0xFF00A8E1, "1024", "20580"),
    Disney(337, "Disney+", 0xFF0A1C5C, 0xFF0E47A1, "2739"),
    Max(1899, "HBO Max", 0xFF1E0B5C, 0xFF5822F5, "49|3186", "7429"),
    Hulu(15, "Hulu", 0xFF0B3A1E, 0xFF1CE783, "453"),
    Paramount(531, "Paramount+", 0xFF0A2A6B, 0xFF0064FF, "4330"),
    Canal(381, "Canal+", 0xFF111111, 0xFF4A4A4A, "285"),
}

/**
 * The studios, as Disney+ shows its brands (Disney, Pixar, Marvel, Star Wars…): a tile with the studio's logo opens
 * what it made, films and series. TMDB company ids.
 */
enum class Studio(val companyId: Int, val label: String) {
    Disney(2, "Disney"),
    Pixar(3, "Pixar"),
    Marvel(420, "Marvel Studios"),
    Lucasfilm(1, "Lucasfilm"),
    DreamWorks(521, "DreamWorks Animation"),
    Ghibli(10342, "Studio Ghibli"),
    Illumination(6704, "Illumination"),
    Warner(174, "Warner Bros."),
    Universal(33, "Universal"),
    A24(41077, "A24"),
}

/** The sections of a studio's page, in order: what it is known for, what is new, the best, its series, its classics. */
enum class StudioSection(val title: L, val tagline: L, val style: RowStyle) {
    Popular(L("Most Watched", "Les plus regardés"), L("What everyone is watching now.", "Ce que tout le monde regarde en ce moment."), RowStyle.Landscape),
    Latest(L("Latest Releases", "Dernières sorties"), L("Out most recently, newest first.", "Sortis le plus récemment, du plus récent au plus ancien."), RowStyle.Poster),
    Acclaimed(L("Highest Rated", "Les mieux notés"), L("The best rated by viewers.", "Les mieux notés par les spectateurs."), RowStyle.Poster),
    Series(L("Series", "Séries"), L("Its series and shows.", "Ses séries et programmes."), RowStyle.Landscape),
    Classics(L("Classics", "Les classiques"), L("Before 2000, the most seen first.", "Avant 2000, les plus vus d'abord."), RowStyle.Poster),
}

object G {
    const val ACTION = 28
    const val ADVENTURE = 12
    const val ANIMATION = 16
    const val COMEDY = 35
    const val CRIME = 80
    const val DOCUMENTARY = 99
    const val DRAMA = 18
    const val FAMILY = 10751
    const val FANTASY = 14
    const val HISTORY = 36
    const val HORROR = 27
    const val MUSIC = 10402
    const val MYSTERY = 9648
    const val ROMANCE = 10749
    const val SCIFI = 878
    const val THRILLER = 53
    const val WAR = 10752
    const val WESTERN = 37
    const val TV_ACTION = 10759
    const val TV_KIDS = 10762
    const val TV_REALITY = 10764
    const val TV_SCIFI = 10765
    const val TV_WAR = 10768
}

/** The editorial rows, carried over from the existing Sokoul app, in English first. */
object RowBook {
    private const val M = "movie"
    private const val S = "tv"

    private fun row(id: String, kind: String, kicker: Kicker, query: TitleQuery, en: Pair<String, String>, fr: Pair<String, String>) =
        RowSpec(id, kind, L(en.first, fr.first), L(en.second, fr.second), kicker, query)

    private fun theme(id: String, kind: String, keywords: Set<String>, minRating: Double, en: Pair<String, String>, fr: Pair<String, String>) =
        row(id, kind, Kicker.Theme, TitleQuery(keywords = keywords, minRating = minRating, minVotes = 100), en, fr)

    private fun genre(id: String, kind: String, genres: Set<Int>, minRating: Double, en: Pair<String, String>, fr: Pair<String, String>, no: Set<Int> = emptySet(), votes: Int = 300) =
        row(id, kind, Kicker.Genre, TitleQuery(genres = genres, noGenres = no, minRating = minRating, minVotes = votes), en, fr)

    private fun era(id: String, from: Int, to: Int, en: Pair<String, String>, fr: Pair<String, String>) =
        row(id, M, Kicker.Era, TitleQuery(minYear = from, maxYear = to, minRating = 6.5, minVotes = 500), en, fr)

    private fun world(id: String, kind: String, language: String, minRating: Double, en: Pair<String, String>, fr: Pair<String, String>, genres: Set<Int> = emptySet()) =
        row(id, kind, Kicker.World, TitleQuery(genres = genres, language = language, minRating = minRating, minVotes = 100), en, fr)

    val movies: List<RowSpec> = listOf(
        genre("m-action", M, setOf(G.ACTION), 6.0, "Action at Full Throttle" to "Chases, fights and stunts.", "Action à haute dose" to "Poursuites, combats et cascades."),
        genre("m-thriller", M, setOf(G.THRILLER), 6.2, "Edge-of-Your-Seat Suspense" to "Impossible to look away.", "Suspense à couper le souffle" to "Impossible de décrocher."),
        genre("m-comedy", M, setOf(G.COMEDY), 6.0, "Comedies Not to Miss" to "Guaranteed laughs.", "Comédies à ne pas manquer" to "Fous rires garantis.", no = setOf(G.HORROR)),
        theme("m-heist", M, setOf("heist", "bank robbery", "robbery"), 6.0, "Heists and Robberies" to "The perfect plan… almost.", "Casses et braquages" to "Le plan parfait… ou presque."),
        genre("m-scifi", M, setOf(G.SCIFI), 6.2, "Sci-Fi That Bends Your Mind" to "The future starts here.", "La science-fiction qui retourne le cerveau" to "Le futur commence ici."),
        genre("m-crime", M, setOf(G.CRIME), 6.3, "Crime and Investigations" to "Cops, crooks and grey areas.", "Crimes et enquêtes" to "Flics, truands et zones grises."),
        genre("m-adventure", M, setOf(G.ADVENTURE), 6.2, "Great Adventures" to "Treasure maps, unknown lands and fearless heroes.", "Grandes aventures" to "Cartes au trésor, terres inconnues et héros intrépides.", no = setOf(G.ANIMATION)),
        theme("m-superhero", M, setOf("superhero", "based on comic", "marvel cinematic universe (mcu)", "dc extended universe (dceu)"), 6.0, "Superheroes" to "Capes, masks and extraordinary powers.", "Super-héros" to "Capes, masques et pouvoirs extraordinaires."),
        genre("m-drama", M, setOf(G.DRAMA), 7.0, "Unforgettable Dramas" to "Stories that stay with you.", "Drames inoubliables" to "Des histoires qui restent longtemps."),
        genre("m-animation", M, setOf(G.ANIMATION), 6.5, "The Best of Animation" to "For kids… and especially for grown-ups.", "Le meilleur de l'animation" to "Pour les petits… et surtout les grands."),
        theme("m-space", M, setOf("space travel", "outer space", "astronaut", "spacecraft"), 6.0, "Space Odysseys" to "Welcome to space.", "Odyssées spatiales" to "Bienvenue dans l'espace."),
        genre("m-horror", M, setOf(G.HORROR), 5.8, "Watch With the Lights On" to "Not for the faint of heart.", "À regarder lumière allumée" to "Âmes sensibles, s'abstenir."),
        theme("m-true", M, setOf("based on true story", "biography", "true crime"), 6.5, "Based on True Stories" to "Stronger than fiction.", "Tirés d'histoires vraies" to "Plus fort que la fiction."),
        genre("m-fantasy", M, setOf(G.FANTASY), 6.2, "Fantasy Worlds" to "Magic, dragons and forgotten kingdoms.", "Mondes fantastiques" to "Magie, dragons et royaumes oubliés.", no = setOf(G.ANIMATION)),
        theme("m-survival", M, setOf("survival"), 6.0, "Survival at All Costs" to "Alone against everyone… and nature.", "Survivre à tout prix" to "Seuls contre tous… et contre la nature."),
        genre("m-romance", M, setOf(G.ROMANCE), 6.2, "Love Stories" to "Love at first sight and broken hearts.", "Histoires d'amour" to "Coups de foudre et cœurs brisés.", no = setOf(G.HORROR)),
        theme("m-spy", M, setOf("spy", "espionage", "secret agent", "cia"), 6.0, "Spies and Secret Agents" to "Impossible missions and double games.", "Espions et agents secrets" to "Missions impossibles et doubles jeux."),
        genre("m-mystery", M, setOf(G.MYSTERY), 6.3, "Mysteries and Puzzles" to "Can you solve it before the end?", "Mystères et énigmes" to "Saurez-vous trouver avant la fin ?"),
        theme("m-revenge", M, setOf("revenge", "vigilante"), 6.2, "Revenge" to "A dish best served cold.", "Vengeance" to "Un plat qui se mange froid."),
        genre("m-family", M, setOf(G.FAMILY), 6.0, "Family Movie Night" to "Everyone on the couch.", "Soirée en famille" to "Tout le monde sur le canapé.", no = setOf(G.HORROR, G.THRILLER, G.CRIME, G.WAR)),
        theme("m-dystopia", M, setOf("dystopia", "post-apocalyptic future", "post-apocalyptic"), 6.0, "End of the World and Dystopias" to "Tomorrow won't be rosy.", "Fins du monde et dystopies" to "Demain ne sera pas rose."),
        theme("m-ai", M, setOf("artificial intelligence", "robot", "android", "cyborg"), 6.0, "Robots and Artificial Intelligence" to "When machines start to think.", "Robots et intelligences artificielles" to "Quand la machine se met à penser."),
        genre("m-war", M, setOf(G.WAR), 6.5, "On the Front Line" to "Courage at the heart of chaos.", "En première ligne" to "Le courage au cœur du chaos."),
        theme("m-ww2", M, setOf("world war ii", "nazi", "d-day"), 6.5, "World War II" to "Resistance, the front and unsung heroes.", "La Seconde Guerre mondiale" to "Résistance, front et héros anonymes."),
        genre("m-history", M, setOf(G.HISTORY), 6.8, "History on a Grand Scale" to "Kings, battles and extraordinary destinies.", "L'Histoire en grand" to "Rois, batailles et destins hors du commun."),
        theme("m-martial", M, setOf("martial arts", "kung fu", "samurai"), 6.0, "Martial Arts" to "Kung fu, swords and fight choreography.", "Arts martiaux" to "Kung-fu, sabres et chorégraphies de combat."),
        theme("m-monsters", M, setOf("monster", "kaiju", "giant monster", "dinosaur", "creature"), 5.6, "Monsters and Creatures" to "The bigger, the better.", "Monstres et créatures" to "Plus c'est gros, plus c'est bon."),
        theme("m-zombie", M, setOf("zombie", "zombie apocalypse"), 5.5, "Zombie Outbreak" to "They're slow. Well, not all of them.", "Invasion de zombies" to "Ils sont lents. Enfin, pas tous."),
        theme("m-vampire", M, setOf("vampire"), 5.5, "Creatures of the Night" to "Fangs, capes and endless nights.", "Nuits de vampires" to "Des crocs, des capes et des nuits sans fin."),
        theme("m-romcom", M, setOf("romantic comedy"), 5.8, "Romantic Comedies" to "Love, with a smile.", "Comédies romantiques" to "L'amour, avec le sourire."),
        theme("m-darkcomedy", M, setOf("dark comedy", "black comedy", "satire"), 6.5, "Dark Humor" to "We shouldn't laugh… and yet.", "Humour noir" to "On ne devrait pas rire… et pourtant."),
        theme("m-court", M, setOf("courtroom", "lawyer", "trial", "legal drama"), 6.3, "In the Courtroom" to "Objection, your honor!", "Au tribunal" to "Objection, votre honneur !"),
        theme("m-coming", M, setOf("coming of age", "high school", "teenager"), 6.3, "Coming of Age" to "Adolescence, its first times and its dramas.", "Le temps de grandir" to "L'adolescence, ses premières fois et ses drames."),
        theme("m-road", M, setOf("road trip", "road movie"), 6.0, "Road Movies" to "The road as the only destination.", "Road movies" to "La route comme seule destination."),
        theme("m-sport", M, setOf("sports", "boxing", "football (soccer)", "basketball", "baseball", "sport"), 6.0, "The Will to Win" to "Sweat, grit and victories.", "La rage de gagner" to "Sueur, dépassement et victoires."),
        genre("m-western", M, setOf(G.WESTERN), 6.3, "Legends of the West" to "Duels in the sun and wide open spaces.", "Légendes du Far West" to "Duels au soleil et grands espaces."),
        genre("m-music", M, setOf(G.MUSIC), 6.5, "Music, Maestro" to "Films with rhythm.", "Musique, maestro" to "Des films qui ont du rythme."),
        genre("m-documentary", M, setOf(G.DOCUMENTARY), 6.8, "Captivating Documentaries" to "Reality often beats fiction.", "Documentaires captivants" to "La réalité dépasse souvent la fiction.", votes = 100),
        era("m-20s", 2020, 2029, "The 2020s" to "The decade so far.", "Les années 2020" to "La décennie en cours."),
        era("m-10s", 2010, 2019, "The Best of the 2010s" to "The decade of the great sagas.", "Le meilleur des années 2010" to "La décennie des grandes sagas."),
        era("m-00s", 2000, 2009, "Back to the 2000s" to "The cinema of the new millennium.", "Retour aux années 2000" to "Le cinéma du nouveau millénaire."),
        era("m-90s", 1990, 1999, "90s Nostalgia" to "VHS, blockbusters and cult films.", "Nostalgie des années 90" to "VHS, blockbusters et films cultes."),
        era("m-80s", 1980, 1989, "Pure 80s" to "Synths, neon and great classics.", "Les années 80 en version originale" to "Synthés, néons et grands classiques."),
        row("m-classics", M, Kicker.Era, TitleQuery(maxYear = 1979, minRating = 7.0, minVotes = 300), "Great Classics" to "The cinema that invented everything.", "Grands classiques" to "Le cinéma qui a tout inventé."),
        world("m-lang-fr", M, "fr", 6.0, "French Cinema" to "The best from France.", "Cinéma français" to "Le meilleur de chez nous."),
        world("m-lang-ko", M, "ko", 6.5, "Korean Cinema" to "Shocking thrillers and strong emotions.", "Cinéma coréen" to "Thrillers chocs et émotions fortes."),
        world("m-lang-ja", M, "ja", 6.8, "Japanese Cinema" to "Animation, samurai and poetry.", "Cinéma japonais" to "Animation, samouraïs et poésie."),
        world("m-lang-es", M, "es", 6.3, "Spanish-Language Cinema" to "Passion, suspense and sunshine.", "Cinéma hispanique" to "Passion, suspense et soleil."),
        world("m-lang-hi", M, "hi", 6.3, "Bollywood and Indian Cinema" to "Colors, music and big feelings.", "Bollywood et cinéma indien" to "Couleurs, musique et grands sentiments."),
        row("m-psy", M, Kicker.Selection, TitleQuery(keywords = setOf("psychological thriller"), minRating = 6.0, minVotes = 100), "Psychological Thrillers" to "The danger is in the mind.", "Thrillers psychologiques" to "Le danger est dans la tête."),
        row("m-gems", M, Kicker.Selection, TitleQuery(minRating = 7.2, minVotes = 150, maxVotes = 2_500), "Hidden Gems" to "Highly rated, still little seen.", "Pépites méconnues" to "Très bien notées, encore peu vues."),
        row("m-time", M, Kicker.Selection, TitleQuery(keywords = setOf("time travel", "time loop", "time paradox"), minVotes = 100), "Time Paradoxes" to "Yesterday, tomorrow, and it all starts again.", "Paradoxes temporels" to "Hier, demain, et tout recommence."),
        row("m-closed", M, Kicker.Selection, TitleQuery(keywords = setOf("single location"), minVotes = 100), "Locked Rooms" to "One place, one night, no way out.", "Huis clos" to "Un lieu, une nuit, aucune issue."),
        row("m-noir", M, Kicker.Selection, TitleQuery(genres = setOf(G.CRIME), keywords = setOf("neo-noir", "serial killer"), minVotes = 100), "Dark Crime Stories" to "Rain, neon and troubled hearts.", "Polars sombres" to "Pluie, néons et cœurs troubles."),
        row("m-short", M, Kicker.Selection, TitleQuery(minRuntime = 60, maxRuntime = 89, minRating = 6.3, minVotes = 300), "Under 90 Minutes" to "For an evening that doesn't end too late.", "Moins de 1 h 30" to "Pour une soirée qui ne finit pas trop tard."),
        row("m-acclaimed", M, Kicker.Essentials, TitleQuery(minRating = 8.0, minVotes = 1_000), "Acclaimed by All" to "Audiences and critics agree.", "Acclamés par tous" to "Le public et la critique sont d'accord."),
    )

    val series: List<RowSpec> = listOf(
        genre("s-crime", S, setOf(G.CRIME), 7.0, "Crime Series" to "Investigations, gangs and cops on the edge.", "Séries criminelles" to "Enquêtes, gangs et flics au bord du gouffre.", votes = 100),
        genre("s-scifi", S, setOf(G.TV_SCIFI), 7.0, "Sci-Fi and Fantasy" to "Other worlds, season after season.", "Science-fiction et fantastique" to "D'autres mondes, saison après saison.", votes = 100),
        genre("s-comedy", S, setOf(G.COMEDY), 6.8, "Series to Laugh Out Loud" to "Short episodes, big laughs.", "Des séries pour rire aux éclats" to "Des épisodes courts pour rire beaucoup.", votes = 100),
        row("s-mini", S, Kicker.Selection, TitleQuery(miniSeries = true, minRating = 7.2, minVotes = 150), "Miniseries" to "One season, one complete story.", "Mini-séries" to "Une saison, une histoire complète."),
        genre("s-drama", S, setOf(G.DRAMA), 7.5, "Great Drama Series" to "Characters you follow for years.", "Grandes séries dramatiques" to "Des personnages qu'on suit pendant des années."),
        genre("s-action", S, setOf(G.TV_ACTION), 6.5, "Non-Stop Action Series" to "Episodes that fly by.", "Séries sous haute tension" to "Des épisodes qui filent à toute allure.", votes = 100),
        genre("s-mystery", S, setOf(G.MYSTERY), 7.0, "Mystery Series" to "Every answer brings a new question.", "Séries à mystère" to "Chaque réponse amène une nouvelle question.", votes = 100),
        world("s-lang-ko", S, "ko", 7.0, "K-Dramas" to "Korean romances, thrillers and cliffhangers.", "K-dramas" to "Romances, thrillers et cliffhangers coréens."),
        world("s-lang-ja", S, "ja", 7.2, "Japanese Anime" to "The great sagas of Japanese animation.", "Animés japonais" to "Les grandes sagas de l'animation japonaise.", genres = setOf(G.ANIMATION)),
        theme("s-true", S, setOf("based on true story", "true crime", "biography"), 6.8, "True Stories in Series" to "Real events and famous cases.", "Histoires vraies en série" to "Faits réels et affaires célèbres."),
        theme("s-superhero", S, setOf("superhero", "based on comic"), 6.5, "Superhero Series" to "Powers, and episodes galore.", "Super-héros en série" to "Des pouvoirs, et des épisodes à la pelle."),
        genre("s-animation", S, setOf(G.ANIMATION), 7.0, "Animated Series" to "Animation, the long version.", "Séries animées" to "L'animation, version longue.", no = setOf(G.TV_KIDS), votes = 100),
        theme("s-zombie", S, setOf("zombie", "post-apocalyptic future", "dystopia"), 6.5, "The End of the World, in Series" to "Survive, season after season.", "Fin du monde en série" to "Survivre, saison après saison."),
        theme("s-heist", S, setOf("heist", "robbery", "con artist"), 6.8, "Cons and Heists" to "Brilliant plans and dirty tricks.", "Arnaques et braquages" to "Plans géniaux et coups tordus."),
        world("s-lang-fr", S, "fr", 6.5, "French Series" to "The best of French creation.", "Séries françaises" to "Le meilleur de la création française."),
        world("s-lang-es", S, "es", 6.8, "Spanish and Latin Series" to "Heists, passions and telenovelas.", "Séries espagnoles et latines" to "Braquages, passions et telenovelas."),
        theme("s-medical", S, setOf("hospital", "doctor", "medical drama"), 6.8, "Scrubs and Emergencies" to "Emergencies, scalpels and desperate cases.", "Blouses blanches et urgences" to "Urgences, bistouris et cas désespérés."),
        theme("s-teen", S, setOf("high school", "teenager", "coming of age"), 6.8, "High School Years" to "High school, love and secrets.", "Les années lycée" to "Lycée, amours et secrets."),
        theme("s-nordic", S, setOf("scandinavia", "sweden", "denmark", "norway", "iceland", "nordic noir"), 6.8, "Nordic Noir" to "Cold, night and chilling crimes.", "Polars nordiques" to "Le froid, la nuit et des crimes glaçants."),
        genre("s-war", S, setOf(G.TV_WAR), 7.0, "War and Power" to "Plots, battles and the corridors of power.", "Guerre et pouvoir" to "Complots, batailles et coulisses du pouvoir.", votes = 100),
        genre("s-family", S, setOf(G.FAMILY, G.TV_KIDS), 6.5, "Series for the Whole Family" to "Episodes to share.", "Séries pour toute la famille" to "Des épisodes à partager.", no = setOf(G.CRIME, G.TV_ACTION), votes = 50),
        genre("s-western", S, setOf(G.WESTERN), 6.8, "Western Series" to "The wild West, season after season.", "Séries western" to "L'Ouest sauvage, saison après saison.", votes = 50),
        genre("s-documentary", S, setOf(G.DOCUMENTARY), 7.0, "Docuseries" to "Reality, episode by episode.", "Docu-séries" to "La réalité, épisode par épisode.", votes = 50),
        genre("s-reality", S, setOf(G.TV_REALITY), 5.0, "Reality and Entertainment" to "To unwind.", "Téléréalité et divertissement" to "Pour décompresser.", votes = 20),
        row("s-classics", S, Kicker.Era, TitleQuery(maxYear = 2009, minRating = 7.8, minVotes = 300), "Cult Series" to "The monuments of television.", "Séries cultes" to "Les monuments de la télé."),
        row("s-acclaimed", S, Kicker.Essentials, TitleQuery(minRating = 8.2, minVotes = 1_000), "The Highest Rated Series" to "Rated 8 and above by viewers.", "Les séries les mieux notées" to "Notées 8 et plus par les spectateurs."),
    )

    fun byId(id: String): RowSpec = (movies + series).first { it.id == id }
}

/** Translates a [TitleQuery] into TMDB discover parameters. Pure and tested. */
object RowQueries {
    fun params(query: TitleQuery, kind: String, keywordIds: List<Int>): Map<String, String> = buildMap {
        put("sort_by", "popularity.desc")
        put("include_adult", "false")
        if (query.genres.isNotEmpty()) put("with_genres", query.genres.joinToString("|"))
        if (query.noGenres.isNotEmpty()) put("without_genres", query.noGenres.joinToString(","))
        if (keywordIds.isNotEmpty()) put("with_keywords", keywordIds.joinToString("|"))
        query.minRating?.let { put("vote_average.gte", it.toString()) }
        query.minVotes?.let { put("vote_count.gte", it.toString()) }
        query.maxVotes?.let { put("vote_count.lte", it.toString()) }
        query.minRuntime?.let { put("with_runtime.gte", it.toString()) }
        query.maxRuntime?.let { put("with_runtime.lte", it.toString()) }
        query.language?.let { put("with_original_language", it) }
        val date = if (kind == "tv") "first_air_date" else "primary_release_date"
        query.minYear?.let { put("$date.gte", "$it-01-01") }
        query.maxYear?.let { put("$date.lte", "$it-12-31") }
        if (query.miniSeries) put("with_type", "2")
    }
}

/** Where a row's titles come from. */
sealed interface RowSource {
    data class Trending(val kind: String) : RowSource
    data class TmdbList(val kind: String, val name: String) : RowSource
    data class Spec(val spec: RowSpec) : RowSource
    data class OnPlatform(val kind: String, val platform: Platform) : RowSource
    data class Fixed(val items: List<TmdbItem>) : RowSource
    data class Anime(val query: com.sokoul.pc.data.anime.AniQuery) : RowSource
    data object Ghibli : RowSource
    data object AnimeToday : RowSource
    /** Recommendations from the profile's history. */
    data object ForYou : RowSource
}

enum class RowStyle { Poster, Landscape, Square, Top10 }

enum class PageKind(val kind: String) { Home("movie"), Movies("movie"), Series("tv"), Anime("tv") }

/** One line of a page, in display order. */
sealed interface PageRow {
    val id: String

    /** A row of titles under ONE heading: a word or a short phrase, never a kicker above it or a line under it. */
    data class Titles(
        override val id: String,
        val title: L,
        val style: RowStyle,
        val source: RowSource,
        val minItems: Int = MIN_ITEMS,
    ) : PageRow

    data class Moods(val kind: String) : PageRow { override val id get() = "moods" }
    data class Categories(val kind: String) : PageRow { override val id get() = "categories" }
    data class Platforms(val kind: String?) : PageRow { override val id get() = "platforms" }
    /** The studios' tiles (Disney+'s brands). */
    data object Studios : PageRow { override val id get() = "studios" }
    data object Sagas : PageRow { override val id get() = "sagas" }
    /** Calendar, every category, a file of one's own. */
    data object AnimeTools : PageRow { override val id get() = "anime-tools" }
    /** The genres, to sort by. */
    data object AnimeGenres : PageRow { override val id get() = "anime-genres" }
    /** Books being read. */
    data object Reading : PageRow { override val id get() = "anime-reading" }
    /** Books, which only exist here: covers rather than titles of the catalog. */
    data class Books(override val id: String, val title: L, val query: com.sokoul.pc.data.anime.AniQuery) : PageRow
    data class Because(val kind: String?) : PageRow { override val id get() = "because" }
    /** Release calendar, roulette and the other discovery tools. */
    data object Discover : PageRow { override val id get() = "discover" }

    companion object {
        /** A shorter row looks broken: it is dropped. */
        const val MIN_ITEMS = 6
    }
}

/** The order of each page, following the existing Sokoul app: trends first, then a mix that keeps changing. */
object PagePlans {
    private fun spec(s: RowSpec, style: RowStyle = RowStyle.Poster) = PageRow.Titles("spec-${s.id}", s.title, style, RowSource.Spec(s))

    private fun plat(kind: String, p: Platform) =
        PageRow.Titles("plat-$kind-${p.name}", L("${p.label} Originals", "Originaux ${p.label}"), RowStyle.Poster, RowSource.OnPlatform(kind, p))

    private val SagaRowKind = PageRow.Sagas

    private fun top10(kind: String) = PageRow.Titles(
        "top-$kind",
        if (kind == "tv") L("Top 10 Series", "Top 10 séries") else L("Top 10 Films", "Top 10 films"),
        RowStyle.Top10, RowSource.Trending(kind),
    )

    private fun popular(kind: String) = PageRow.Titles(
        "popular-$kind",
        if (kind == "tv") L("Popular Series", "Séries populaires") else L("Popular Films", "Films populaires"),
        RowStyle.Landscape, RowSource.TmdbList(kind, "popular"),
    )

    private fun newest(kind: String) = PageRow.Titles(
        "new-$kind",
        if (kind == "tv") L("New Episodes", "Nouveaux épisodes") else L("Just Released", "Tout juste sortis"),
        RowStyle.Poster, RowSource.TmdbList(kind, if (kind == "tv") "on_the_air" else "now_playing"),
    )

    private fun topRated(kind: String) = PageRow.Titles(
        "toprated-$kind",
        L("Essentials", "Incontournables"),
        RowStyle.Poster, RowSource.TmdbList(kind, "top_rated"),
    )

    /** Editorial rows, with a platform row slipped in every few rows. */
    private fun mix(specs: List<RowSpec>, kind: String, platforms: List<Platform>): List<PageRow> {
        val out = ArrayList<PageRow>()
        val rest = platforms.toMutableList()
        specs.forEachIndexed { i, s ->
            out += spec(s)
            if (i % 4 == 3 && rest.isNotEmpty()) out += plat(kind, rest.removeAt(0))
        }
        rest.forEach { out += plat(kind, it) }
        return out
    }

    val movies: List<PageRow> by lazy {
        val specs = RowBook.movies
        val head = specs.take(4)
        val tail = specs.drop(4)
        listOf(PageRow.Platforms("movie"), top10("movie"), PageRow.Because("movie"), newest("movie"), PageRow.Studios, PageRow.Moods("movie")) +
            spec(head[0]) + popular("movie") + spec(head[1]) + plat("movie", Platform.Netflix) + spec(head[2]) + SagaRowKind + spec(head[3]) + topRated("movie") +
            mix(tail, "movie", Platform.entries.filter { it.companies != null }.drop(1)) + PageRow.Categories("movie")
    }

    val series: List<PageRow> by lazy {
        val specs = RowBook.series
        val head = specs.take(3)
        val tail = specs.drop(3)
        listOf(PageRow.Platforms("tv"), top10("tv"), PageRow.Because("tv"), newest("tv"), PageRow.Moods("tv")) +
            spec(head[0]) + popular("tv") + spec(head[1]) + plat("tv", Platform.Netflix) + spec(head[2]) + topRated("tv") +
            mix(tail, "tv", Platform.entries.drop(1)) + PageRow.Categories("tv")
    }

    private val forYou = PageRow.Titles(
        "for-you", L("For You", "Pour vous"),
        RowStyle.Square, RowSource.ForYou,
    )

    val home: List<PageRow> by lazy {
        fun m(id: String) = spec(RowBook.byId(id))
        listOf(
            top10("movie"), PageRow.Because(null), forYou, newest("movie"), top10("tv"), PageRow.Moods("movie"), popular("tv"), PageRow.Platforms(null), PageRow.Studios, PageRow.Discover,
            plat("tv", Platform.Netflix), m("m-action"), SagaRowKind, m("s-crime"), plat("movie", Platform.Prime), topRated("movie"), m("m-gems"), m("s-mini"),
            plat("movie", Platform.Disney), m("m-animation"), m("s-lang-ko"), m("m-thriller"), plat("tv", Platform.Max), m("m-90s"), m("s-acclaimed"), m("m-comedy"),
            m("s-scifi"), m("m-true"), PageRow.Categories("movie"),
        )
    }

    private fun animeRow(id: String, en: String, fr: String, query: com.sokoul.pc.data.anime.AniQuery, style: RowStyle = RowStyle.Poster) =
        PageRow.Titles("anime-$id", L(en, fr), style, RowSource.Anime(query), minItems = 4)

    private val anime: List<PageRow> by lazy {
        val k = com.sokoul.pc.data.anime.MediaKind.ANIME
        val b = com.sokoul.pc.data.anime.MediaKind.MANGA
        val q = { sort: String -> com.sokoul.pc.data.anime.AniQuery(k, sort = sort, perPage = 24) }
        val (season, year) = com.sokoul.pc.data.anime.AnimeSeasons.of(java.time.LocalDate.now())
        val (nextSeason, nextYear) = com.sokoul.pc.data.anime.AnimeSeasons.next(java.time.LocalDate.now())
        val theme = { id: String, tag: String, en: String, fr: String -> animeRow(id, en, fr, q("POPULARITY_DESC").copy(tag = tag)) }
        // Anime and manga on one page (the Manga tab is gone): the books one reads come back here, and books have their
        // own rows among the anime ones. A book opens its own page, which leads to its anime.
        val book = { sort: String -> com.sokoul.pc.data.anime.AniQuery(b, sort = sort) }
        listOf(
            animeRow("top", "Top 10 Anime", "Top 10 des animés", q("TRENDING_DESC").copy(perPage = 10), RowStyle.Top10),
            PageRow.Reading,
            PageRow.AnimeTools,
            PageRow.Titles("anime-today", L("Airing today", "Diffusés aujourd'hui"), RowStyle.Poster, RowSource.AnimeToday, minItems = 4),
            PageRow.Books("books-trending", L("Manga of the moment", "Les mangas du moment"), book("TRENDING_DESC")),
            animeRow("season", "This season", "Cette saison", q("POPULARITY_DESC").copy(season = season, year = year)),
            PageRow.AnimeGenres,
            PageRow.Books("books-ongoing", L("Manga still coming out", "Mangas en cours de parution"), book("POPULARITY_DESC").copy(status = "RELEASING")),
            animeRow("rated", "Highest rated", "Les mieux notés", q("SCORE_DESC"), RowStyle.Landscape),
            PageRow.Books("books-rated", L("Highest rated manga", "Les mangas les mieux notés"), book("SCORE_DESC")),
            theme("isekai", "Isekai", "Another world", "Un autre monde"),
            animeRow("movies", "Anime films", "Films d'animation", q("POPULARITY_DESC").copy(format = "MOVIE")),
            PageRow.Titles("anime-ghibli", L("The worlds of Studio Ghibli", "Les mondes du Studio Ghibli"), RowStyle.Landscape, RowSource.Ghibli),
            theme("iyashi", "Iyashikei", "Soft and soothing", "Doux et apaisant"),
            animeRow("next", "Next season", "La saison prochaine", q("POPULARITY_DESC").copy(season = nextSeason, year = nextYear)),
            theme("family", "Found Family", "Found family", "Une famille choisie"),
            theme("antihero", "Anti-Hero", "Anti-heroes", "Anti-héros"),
            theme("death", "Death Game", "Death games", "Jeux mortels"),
            animeRow("donghua", "Chinese animation", "Animation chinoise", q("POPULARITY_DESC").copy(country = "CN")),
            theme("rebirth", "Reincarnation", "A second life", "Une seconde vie"),
            theme("food", "Food", "Food on screen", "Gastronomie"),
            theme("power", "Super Power", "Superpowers", "Super-pouvoirs"),
        )
    }

    fun of(kind: PageKind) = when (kind) {
        PageKind.Anime -> anime
        PageKind.Home -> home
        PageKind.Movies -> movies
        PageKind.Series -> series
    }
}
