// UI texts in French (default) and English, picked from the browser's preferred languages.

const FR = {
  appName: 'Sokoul',
  // Sign-in
  signinTitle: 'Connexion',
  signinIntro: 'Entrez le nom et la phrase secrète de votre compte Sokoul.',
  name: 'Nom',
  passphrase: 'Phrase secrète',
  signinButton: 'Se connecter',
  signingIn: 'Connexion…',
  wrongCredentials: 'Nom ou phrase secrète incorrects.',
  tooMany: 'Trop d’essais. Réessayez dans quelques minutes.',
  noAnswer: 'Le serveur ne répond pas. Vérifiez la connexion, puis réessayez.',
  missingFields: 'Entrez votre nom et votre phrase secrète.',
  linkTitle: 'Connecter une TV',
  linkIntro: 'Le code {code} attend une confirmation.',
  linkButton: 'Confirmer sur la page dédiée',
  deviceName: 'Navigateur',
  // Profiles
  whoIsWatching: 'Qui regarde ?',
  kids: 'Enfants',
  profileCode: 'Code de {name}',
  profileCodeHint: 'Ce profil est protégé.',
  wrongCode: 'Ce n’est pas le bon code.',
  open: 'Ouvrir',
  cancel: 'Annuler',
  // Navigation
  home: 'Accueil',
  search: 'Recherche',
  sources: 'Sources et clés',
  account: 'Compte',
  back: 'Retour',
  previous: 'Précédent',
  next: 'Suivant',
  switchProfile: 'Changer de profil',
  // Home
  continueWatching: 'Continuer à regarder',
  movie: 'Film',
  series: 'Série',
  play: 'Lecture',
  resume: 'Reprendre',
  fromStart: 'Depuis le début',
  moreInfo: 'Plus d’infos',
  homeEmpty: 'Rien à afficher pour l’instant.',
  episodeShort: 'S{season} · É{episode}',
  left: 'Reste {time}',
  // Title
  seasons: 'Saisons',
  season: 'Saison {n}',
  episodes: 'Épisodes',
  episodesCount: '{n} épisodes',
  episodeTitle: '{n}. {name}',
  cast: 'Distribution',
  noEpisodes: 'Aucun épisode connu pour cette saison.',
  playEpisode: 'Lire S{season} É{episode}',
  resumeEpisode: 'Reprendre S{season} É{episode}',
  // Search
  searchPlaceholder: 'Films, séries…',
  searchHint: 'Tapez le titre d’un film ou d’une série.',
  searchNone: 'Aucun résultat pour « {q} ».',
  // Player
  versions: 'Versions',
  subtitles: 'Sous-titres',
  subtitlesOff: 'Désactivés',
  close: 'Fermer',
  loadingVersions: 'Recherche des versions…',
  noVersions: 'Aucune version trouvée pour ce titre.',
  playFailed: 'Cette version ne se lance pas. Essayez-en une autre.',
  remuxed: 'Convertie pour ce navigateur',
  // Sources
  sourcesIntro: 'Les clés de votre compte, telles que le serveur les connaît. Pour l’instant, elles se saisissent depuis l’app Sokoul sur PC ou sur TV ; elles apparaissent ensuite ici.',
  notSet: 'Non renseignée',
  verdictAccepted: 'Vérifiée le {date}',
  verdictRefused: 'Refusée par le service',
  verdictUnreachable: 'Service injoignable le {date}',
  sourcesEmpty: 'Aucune clé n’est enregistrée dans votre compte.',
  needsKey: 'Il manque la clé {service}. Ajoutez-la depuis Sources et clés.',
  needsKeyLink: 'Ouvrir Sources et clés',
  // Account
  signedInAs: 'Connecté en tant que {name}',
  owner: 'Propriétaire',
  member: 'Membre',
  devices: 'Appareils',
  thisDevice: 'Cet appareil',
  lastUsed: 'Utilisé {when}',
  revoke: 'Retirer',
  signOut: 'Se déconnecter',
  linkTv: 'Connecter une TV',
  // Common
  retry: 'Réessayer',
  loadFailed: 'Le chargement a échoué.',
  justNow: 'à l’instant',
  minutesAgo: 'il y a {n} min',
  hoursAgo: 'il y a {n} h',
  daysAgo: 'il y a {n} j',
  hours: '{h} h {m}',
  hoursOnly: '{h} h',
  minutes: '{m} min',
};

const EN = {
  appName: 'Sokoul',
  signinTitle: 'Sign in',
  signinIntro: 'Enter your Sokoul account’s name and passphrase.',
  name: 'Name',
  passphrase: 'Passphrase',
  signinButton: 'Sign in',
  signingIn: 'Signing in…',
  wrongCredentials: 'Wrong name or passphrase.',
  tooMany: 'Too many attempts. Try again in a few minutes.',
  noAnswer: 'The server did not answer. Check the connection, then try again.',
  missingFields: 'Enter your name and passphrase.',
  linkTitle: 'Link a TV',
  linkIntro: 'Code {code} is waiting for a confirmation.',
  linkButton: 'Confirm on the link page',
  deviceName: 'Browser',
  whoIsWatching: 'Who’s watching?',
  kids: 'Kids',
  profileCode: '{name}’s code',
  profileCodeHint: 'This profile is protected.',
  wrongCode: 'That is not the right code.',
  open: 'Open',
  cancel: 'Cancel',
  home: 'Home',
  search: 'Search',
  sources: 'Sources and keys',
  account: 'Account',
  back: 'Back',
  previous: 'Previous',
  next: 'Next',
  switchProfile: 'Switch profile',
  continueWatching: 'Continue watching',
  movie: 'Film',
  series: 'Series',
  play: 'Play',
  resume: 'Resume',
  fromStart: 'From the start',
  moreInfo: 'More info',
  homeEmpty: 'Nothing to show yet.',
  episodeShort: 'S{season} · E{episode}',
  left: '{time} left',
  seasons: 'Seasons',
  season: 'Season {n}',
  episodes: 'Episodes',
  episodesCount: '{n} episodes',
  episodeTitle: '{n}. {name}',
  cast: 'Cast',
  noEpisodes: 'No known episodes for this season.',
  playEpisode: 'Play S{season} E{episode}',
  resumeEpisode: 'Resume S{season} E{episode}',
  searchPlaceholder: 'Films, series…',
  searchHint: 'Type the title of a film or a series.',
  searchNone: 'No results for “{q}”.',
  versions: 'Versions',
  subtitles: 'Subtitles',
  subtitlesOff: 'Off',
  close: 'Close',
  loadingVersions: 'Looking for versions…',
  noVersions: 'No version found for this title.',
  playFailed: 'This version does not start. Try another one.',
  remuxed: 'Converted for this browser',
  sourcesIntro: 'Your account’s keys, as the server knows them. For now they are entered from the Sokoul app on the PC or the TV; they then show up here.',
  notSet: 'Not set',
  verdictAccepted: 'Checked on {date}',
  verdictRefused: 'Refused by the service',
  verdictUnreachable: 'Service unreachable on {date}',
  sourcesEmpty: 'No key is saved in your account.',
  needsKey: 'The {service} key is missing. Add it from Sources and keys.',
  needsKeyLink: 'Open Sources and keys',
  signedInAs: 'Signed in as {name}',
  owner: 'Owner',
  member: 'Member',
  devices: 'Devices',
  thisDevice: 'This device',
  lastUsed: 'Used {when}',
  revoke: 'Remove',
  signOut: 'Sign out',
  linkTv: 'Link a TV',
  retry: 'Try again',
  loadFailed: 'Loading failed.',
  justNow: 'just now',
  minutesAgo: '{n} min ago',
  hoursAgo: '{n} h ago',
  daysAgo: '{n} d ago',
  hours: '{h} h {m}',
  hoursOnly: '{h} h',
  minutes: '{m} min',
};

/** French unless the browser ranks English above French (or lists English and no French). */
function pickLanguage() {
  const langs = (navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || 'fr'])
    .map((l) => String(l).toLowerCase());
  for (const l of langs) {
    if (l.startsWith('fr')) return 'fr';
    if (l.startsWith('en')) return 'en';
  }
  return 'fr';
}

export const lang = pickLanguage();
const table = lang === 'en' ? EN : FR;

/** The text for [key] with `{name}` placeholders filled from [args]. */
export function t(key, args = {}) {
  const raw = table[key] ?? FR[key] ?? key;
  return raw.replace(/\{(\w+)\}/g, (_, k) => (args[k] ?? ''));
}

/** "2 h 46", "48 min" from minutes. */
export function runtime(totalMinutes) {
  if (!totalMinutes || totalMinutes <= 0) return '';
  const h = Math.floor(totalMinutes / 60);
  const m = Math.round(totalMinutes % 60);
  if (h === 0) return t('minutes', { m });
  if (m === 0) return t('hoursOnly', { h });
  return t('hours', { h, m: String(m).padStart(2, '0') });
}

/** "24 nov." / "Nov 24" from epoch milliseconds. */
export function shortDate(ms) {
  if (!ms) return '';
  return new Intl.DateTimeFormat(lang === 'en' ? 'en' : 'fr-FR', { day: 'numeric', month: 'short' }).format(new Date(ms));
}

/** "il y a 3 h" from epoch milliseconds. */
export function ago(ms) {
  const s = Math.max(0, (Date.now() - ms) / 1000);
  if (s < 60) return t('justNow');
  if (s < 3600) return t('minutesAgo', { n: Math.floor(s / 60) });
  if (s < 86400) return t('hoursAgo', { n: Math.floor(s / 3600) });
  if (s < 86400 * 30) return t('daysAgo', { n: Math.floor(s / 86400) });
  return shortDate(ms);
}
