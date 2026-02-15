use strsim::jaro_winkler;

/// Nettoie un titre de torrent en retirant les specs techniques, l'année, les groupes de release.
pub fn normalize_torrent_title(title: &str) -> String {
    let mut result = title.to_lowercase();

    // Remplacer les séparateurs courants par des espaces
    result = result.replace('.', " ").replace('_', " ").replace('-', " ");

    // Retirer les indicateurs de qualité
    let quality_patterns = [
        "bluray",
        "blu ray",
        "bdrip",
        "brrip",
        "webrip",
        "web dl",
        "webdl",
        "hdtv",
        "dvdrip",
        "hdrip",
        "remux",
        "proper",
        "repack",
        "2160p",
        "1080p",
        "720p",
        "480p",
        "360p",
        "x264",
        "x265",
        "h264",
        "h265",
        "hevc",
        "avc",
        "hdr",
        "hdr10",
        "hdr10plus",
        "dolby vision",
        "dv",
        "aac",
        "ac3",
        "dts",
        "truehd",
        "atmos",
        "flac",
        "10bit",
        "8bit",
    ];
    for pattern in quality_patterns {
        result = result.replace(pattern, " ");
    }

    // Retirer les groupes de release courants
    let groups = [
        "yify", "yts", "rarbg", "eztv", "ettv", "sparks", "geckos", "fgt", "mkvcage", "evo",
        "tigole", "qxr",
    ];
    for group in groups {
        result = result.replace(group, " ");
    }

    // Retirer les années (1900-2099)
    let mut chars: Vec<char> = result.chars().collect();
    let result_str: String = chars.iter().collect();
    let year_positions: Vec<(usize, usize)> = result_str
        .match_indices(|c: char| c.is_ascii_digit())
        .collect::<Vec<_>>()
        .windows(4)
        .filter_map(|w| {
            if w.len() == 4 && w[1].0 == w[0].0 + 1 && w[2].0 == w[1].0 + 1 && w[3].0 == w[2].0 + 1
            {
                let year_str: String = w.iter().map(|(_, s)| s.to_string()).collect::<String>();
                if let Ok(year) = year_str.parse::<u32>() {
                    if (1900..=2099).contains(&year) {
                        return Some((w[0].0, w[3].0 + 1));
                    }
                }
            }
            None
        })
        .collect();

    for (start, end) in year_positions.iter().rev() {
        for i in *start..*end {
            if i < chars.len() {
                chars[i] = ' ';
            }
        }
    }
    result = chars.into_iter().collect();

    // Compresser les espaces multiples
    result
        .split_whitespace()
        .collect::<Vec<&str>>()
        .join(" ")
        .trim()
        .to_string()
}

/// Compare un titre de torrent au titre du média attendu.
/// Retourne un score entre 0.0 et 1.0.
pub fn title_similarity(torrent_title: &str, media_title: &str) -> f64 {
    let torrent_clean = normalize_torrent_title(torrent_title);
    let media_clean = media_title.to_lowercase().trim().to_string();

    // Vérification exacte
    if torrent_clean == media_clean {
        return 1.0;
    }

    // Vérifier si le titre du média apparaît en entier dans le titre torrent
    if torrent_clean.contains(&media_clean) {
        return 0.95;
    }

    // Vérifier la correspondance mot-à-mot consécutive
    let media_words: Vec<&str> = media_clean.split_whitespace().collect();
    let torrent_words: Vec<&str> = torrent_clean.split_whitespace().collect();

    if !media_words.is_empty() && torrent_words.len() >= media_words.len() {
        for i in 0..=(torrent_words.len() - media_words.len()) {
            if torrent_words[i..i + media_words.len()] == media_words[..] {
                return 0.90;
            }
        }
    }

    // Fallback: Jaro-Winkler sur les titres nettoyés
    jaro_winkler(&torrent_clean, &media_clean)
}

/// Vérifie si un titre de torrent correspond au média avec un seuil donné.
pub fn is_title_match(torrent_title: &str, media_title: &str, threshold: f64) -> bool {
    title_similarity(torrent_title, media_title) >= threshold
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exact_match() {
        assert!(is_title_match("Inception", "Inception", 0.65));
    }

    #[test]
    fn test_torrent_name_match() {
        assert!(is_title_match(
            "Inception.2010.1080p.BluRay.x264-SPARKS",
            "Inception",
            0.65
        ));
    }

    #[test]
    fn test_no_match() {
        assert!(!is_title_match(
            "The.Matrix.1999.720p.BluRay",
            "Inception",
            0.65
        ));
    }

    #[test]
    fn test_substring_match() {
        let score = title_similarity("Perriers.Bounty.2009.720p", "Bounty");
        // "bounty" appears as a word in the cleaned title, substring match gives 0.95
        assert!(score >= 0.90);
    }

    #[test]
    fn test_completely_different() {
        let score = title_similarity("The.Matrix.1999.720p.BluRay", "Frozen");
        assert!(score < 0.7);
    }
}
