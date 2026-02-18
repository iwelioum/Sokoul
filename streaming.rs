use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct StreamResponse {
    pub sources: Vec<StreamSource>,
    pub recommended: usize, // Index de la source recommandée
    pub subtitles: Vec<SubtitleTrack>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct StreamSource {
    pub url: String,
    pub quality: String,  // "1080p", "720p", "auto"
    pub provider: String, // "flixhq", "vidsrc", etc.
    pub has_vf: bool,
    pub is_alive: bool,
    pub audio_tracks: Vec<AudioTrack>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SubtitleTrack {
    pub lang: String,
    pub label: String,
    pub url: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct AudioTrack {
    pub id: u32,
    pub lang: String,
    pub label: String,
}

// Structure interne pour parser la réponse de Consumet
#[derive(Debug, Deserialize)]
pub struct ConsumetSource {
    pub url: String,
    pub quality: Option<String>,
    pub isM3U8: Option<bool>,
}