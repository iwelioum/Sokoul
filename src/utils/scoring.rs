use crate::providers::TorrentResult;

pub fn compute_score(result: &TorrentResult) -> i32 {
    let mut score: f64 = 0.0;
    let title_lower = result.title.to_lowercase();

    // Seeders score (0-40 points)
    let seeders = result.seeders.unwrap_or(0) as f64;
    score += (seeders.ln().max(0.0) * 6.0).min(40.0);

    // Size score (0-15 points) — prefer 1-8GB for movies
    let size_gb = result.size_bytes as f64 / (1024.0 * 1024.0 * 1024.0);
    if (1.0..=8.0).contains(&size_gb) {
        score += 15.0;
    } else if (0.5..=15.0).contains(&size_gb) {
        score += 10.0;
    } else if size_gb > 0.0 {
        score += 5.0;
    }

    // Quality score (0-25 points)
    if title_lower.contains("2160p") || title_lower.contains("4k") {
        score += 25.0;
    } else if title_lower.contains("1080p") {
        score += 20.0;
    } else if title_lower.contains("720p") {
        score += 10.0;
    } else if title_lower.contains("480p") {
        score += 3.0;
    }

    // Codec bonus (0-10 points)
    if title_lower.contains("x265") || title_lower.contains("hevc") || title_lower.contains("h265")
    {
        score += 10.0;
    } else if title_lower.contains("x264") || title_lower.contains("h264") {
        score += 5.0;
    }

    // HDR bonus (0-5 points)
    if title_lower.contains("hdr")
        || title_lower.contains("dolby vision")
        || title_lower.contains("dv")
    {
        score += 5.0;
    }

    // Audio bonus (0-5 points)
    if title_lower.contains("atmos")
        || title_lower.contains("truehd")
        || title_lower.contains("dts-hd")
    {
        score += 5.0;
    } else if title_lower.contains("aac") || title_lower.contains("ac3") {
        score += 2.0;
    }

    // Source quality bonus
    if title_lower.contains("bluray")
        || title_lower.contains("blu-ray")
        || title_lower.contains("remux")
    {
        score += 5.0;
    } else if title_lower.contains("web-dl") || title_lower.contains("webdl") {
        score += 3.0;
    } else if title_lower.contains("webrip") {
        score += 2.0;
    }

    // Penalty: CAM/TS/screener
    if title_lower.contains("cam")
        || title_lower.contains("hdts")
        || title_lower.contains("screener")
        || title_lower.contains("telecine")
    {
        score -= 30.0;
    }

    score.clamp(0.0, 100.0) as i32
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_result(title: &str, seeders: i32, size_gb: f64) -> TorrentResult {
        TorrentResult {
            title: title.to_string(),
            guid: "test".to_string(),
            size_bytes: (size_gb * 1024.0 * 1024.0 * 1024.0) as i64,
            indexer: "test".to_string(),
            info_url: None,
            download_url: None,
            magnet_url: None,
            info_hash: None,
            seeders: Some(seeders),
            leechers: None,
            protocol: None,
            provider_name: "test".to_string(),
        }
    }

    #[test]
    fn high_quality_scores_higher() {
        let hq = make_result("Movie.2024.2160p.BluRay.x265.HDR.DTS-HD", 100, 4.0);
        let lq = make_result("Movie.2024.720p.WEBRip.x264", 20, 1.0);
        assert!(compute_score(&hq) > compute_score(&lq));
    }

    #[test]
    fn cam_penalized() {
        let cam = make_result("Movie.2024.1080p.CAM.x264", 500, 2.0);
        let web = make_result("Movie.2024.1080p.WEB-DL.x264", 50, 2.0);
        assert!(compute_score(&cam) < compute_score(&web));
    }
}
