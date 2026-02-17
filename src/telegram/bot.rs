use crate::{
    db,
    events::{self, DownloadRequestedPayload, SearchRequestedPayload},
    AppState,
};
use std::sync::Arc;
use teloxide::{
    prelude::*,
    types::{InlineKeyboardButton, InlineKeyboardMarkup, ParseMode},
    utils::command::BotCommands,
};

#[derive(BotCommands, Clone)]
#[command(rename_rule = "lowercase", description = "Available commands:")]
pub enum Command {
    #[command(description = "Show help")]
    Help,
    #[command(description = "Search for a movie/series — /search Inception")]
    Search(String),
    #[command(description = "View current downloads")]
    Downloads,
    #[command(description = "View media library")]
    Library,
    #[command(description = "System status")]
    Status,
}

pub async fn run_bot(state: Arc<AppState>) {
    tracing::info!("Sokoul Telegram bot starting...");

    let bot = Bot::from_env();

    let handler = dptree::entry()
        .branch(
            Update::filter_message()
                .filter_command::<Command>()
                .endpoint(handle_command),
        )
        .branch(Update::filter_callback_query().endpoint(handle_callback));

    Dispatcher::builder(bot, handler)
        .dependencies(dptree::deps![state])
        .enable_ctrlc_handler()
        .build()
        .dispatch()
        .await;
}

async fn handle_command(
    bot: Bot,
    msg: Message,
    cmd: Command,
    state: Arc<AppState>,
) -> ResponseResult<()> {
    match cmd {
        Command::Help => {
            bot.send_message(msg.chat.id, Command::descriptions().to_string())
                .await?;
        }
        Command::Search(query) => {
            handle_search(bot, msg, query, state).await?;
        }
        Command::Downloads => {
            handle_downloads(bot, msg, state).await?;
        }
        Command::Library => {
            handle_library(bot, msg, state).await?;
        }
        Command::Status => {
            handle_status(bot, msg, state).await?;
        }
    }
    Ok(())
}

async fn handle_search(
    bot: Bot,
    msg: Message,
    query: String,
    state: Arc<AppState>,
) -> ResponseResult<()> {
    if query.is_empty() {
        bot.send_message(msg.chat.id, "Usage: /search <movie or series title>")
            .await?;
        return Ok(());
    }

    bot.send_message(msg.chat.id, format!("🔍 Searching for \"{}\"...", query))
        .await?;

    // Publish search event to NATS
    let payload = SearchRequestedPayload {
        query: query.clone(),
    };
    let event_data = serde_json::to_vec(&payload).unwrap();

    if let Err(e) = state
        .jetstream_context
        .publish(events::SEARCH_REQUESTED_SUBJECT, event_data.into())
        .await
    {
        bot.send_message(msg.chat.id, format!("❌ Error: {}", e))
            .await?;
        return Ok(());
    }

    // Wait a bit for Scout to process, then show results
    tokio::time::sleep(tokio::time::Duration::from_secs(4)).await;

    let media_list = db::media::search_media(&state.db_pool, &query, 10)
        .await
        .unwrap_or_default();

    if media_list.is_empty() {
        bot.send_message(msg.chat.id, "No results found.").await?;
        return Ok(());
    }

    let mut text = format!("📋 *Results for \"{}\":*\n\n", escape_md(&query));
    let mut buttons: Vec<Vec<InlineKeyboardButton>> = Vec::new();

    for (i, media) in media_list.iter().take(8).enumerate() {
        let year_str = media.year.map(|y| format!(" ({})", y)).unwrap_or_default();
        let type_emoji = match media.media_type.as_str() {
            "movie" => "🎬",
            "tv" => "📺",
            _ => "📁",
        };

        text.push_str(&format!(
            "{} *{}*{} — {}\n",
            type_emoji,
            escape_md(&media.title),
            escape_md(&year_str),
            escape_md(&media.media_type),
        ));

        buttons.push(vec![InlineKeyboardButton::callback(
            format!(
                "{}. {} {}",
                i + 1,
                media.title.chars().take(30).collect::<String>(),
                year_str
            ),
            format!("results:{}", media.id),
        )]);
    }

    let keyboard = InlineKeyboardMarkup::new(buttons);

    bot.send_message(msg.chat.id, text)
        .parse_mode(ParseMode::MarkdownV2)
        .reply_markup(keyboard)
        .await?;

    Ok(())
}

async fn handle_downloads(bot: Bot, msg: Message, state: Arc<AppState>) -> ResponseResult<()> {
    let tasks = sqlx::query_as::<_, crate::models::Task>(
        "SELECT * FROM tasks WHERE task_type = 'download' ORDER BY created_at DESC LIMIT 10",
    )
    .fetch_all(&state.db_pool)
    .await
    .unwrap_or_default();

    if tasks.is_empty() {
        bot.send_message(msg.chat.id, "📭 No downloads in progress.")
            .await?;
        return Ok(());
    }

    let mut text = "📥 *Downloads:*\n\n".to_string();
    for task in &tasks {
        let status_emoji = match task.status.as_str() {
            "running" => "⏳",
            "completed" => "✅",
            "failed" => "❌",
            "pending" => "⏸️",
            _ => "❓",
        };
        let title = task
            .payload
            .as_ref()
            .and_then(|p| p.get("title"))
            .and_then(|t| t.as_str())
            .unwrap_or("Unknown");

        text.push_str(&format!(
            "{} *{}* — {}\n",
            status_emoji,
            escape_md(title),
            escape_md(&task.status),
        ));
    }

    bot.send_message(msg.chat.id, text)
        .parse_mode(ParseMode::MarkdownV2)
        .await?;

    Ok(())
}

async fn handle_library(bot: Bot, msg: Message, state: Arc<AppState>) -> ResponseResult<()> {
    let media_list = db::media::list_media(&state.db_pool, 20, 0)
        .await
        .unwrap_or_default();

    if media_list.is_empty() {
        bot.send_message(msg.chat.id, "📚 Library is empty.")
            .await?;
        return Ok(());
    }

    let mut text = format!("📚 *Library \\({} media\\):*\n\n", media_list.len());
    for media in media_list.iter().take(15) {
        let year_str = media.year.map(|y| format!(" ({})", y)).unwrap_or_default();
        let type_emoji = match media.media_type.as_str() {
            "movie" => "🎬",
            "tv" => "📺",
            _ => "📁",
        };
        text.push_str(&format!(
            "{} *{}*{}\n",
            type_emoji,
            escape_md(&media.title),
            escape_md(&year_str),
        ));
    }

    bot.send_message(msg.chat.id, text)
        .parse_mode(ParseMode::MarkdownV2)
        .await?;

    Ok(())
}

async fn handle_status(bot: Bot, msg: Message, state: Arc<AppState>) -> ResponseResult<()> {
    let db_ok = sqlx::query("SELECT 1")
        .execute(&state.db_pool)
        .await
        .is_ok();
    let redis_ok = state.redis_client.get_async_connection().await.is_ok();

    let media_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM media")
        .fetch_one(&state.db_pool)
        .await
        .unwrap_or(0);

    let download_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM tasks WHERE task_type = 'download' AND status = 'running'",
    )
    .fetch_one(&state.db_pool)
    .await
    .unwrap_or(0);

    let text = format!(
        "🖥️ *SOKOUL Status:*\n\n\
         {} Database\n\
         {} Redis\n\
         ✅ NATS\n\n\
         📊 {} media in database\n\
         📥 {} active downloads",
        if db_ok { "✅" } else { "❌" },
        if redis_ok { "✅" } else { "❌" },
        media_count,
        download_count,
    );

    bot.send_message(msg.chat.id, text)
        .parse_mode(ParseMode::MarkdownV2)
        .await?;

    Ok(())
}

async fn handle_callback(bot: Bot, q: CallbackQuery, state: Arc<AppState>) -> ResponseResult<()> {
    let data = match q.data {
        Some(ref d) => d.clone(),
        None => return Ok(()),
    };

    bot.answer_callback_query(&q.id).await?;

    let chat_id = match q.message {
        Some(ref msg) => msg.chat.id,
        None => return Ok(()),
    };

    if let Some(media_id_str) = data.strip_prefix("results:") {
        // Show search results (torrents) for this media
        let media_id: uuid::Uuid = match media_id_str.parse() {
            Ok(id) => id,
            Err(_) => {
                bot.send_message(chat_id, "❌ Invalid ID").await?;
                return Ok(());
            }
        };

        let results = db::search_results::get_results_by_media_id(&state.db_pool, media_id)
            .await
            .unwrap_or_default();

        if results.is_empty() {
            bot.send_message(chat_id, "No torrent sources found for this media\\.")
                .parse_mode(ParseMode::MarkdownV2)
                .await?;
            return Ok(());
        }

        let mut text = "🔗 *Available sources:*\n\n".to_string();
        let mut buttons: Vec<Vec<InlineKeyboardButton>> = Vec::new();

        for (i, result) in results.iter().take(6).enumerate() {
            let size_mb = result.size_bytes / (1024 * 1024);
            text.push_str(&format!(
                "{}\\. {} — {}MB — 🟢{} seeds\n",
                i + 1,
                escape_md(&result.title.chars().take(40).collect::<String>()),
                size_mb,
                result.seeders,
            ));

            if result.magnet_link.is_some() || result.url.is_some() {
                buttons.push(vec![InlineKeyboardButton::callback(
                    format!(
                        "⬇️ {}. {} ({}MB)",
                        i + 1,
                        result.title.chars().take(25).collect::<String>(),
                        size_mb
                    ),
                    format!("dl:{}:{}", media_id, result.id),
                )]);
            }
        }

        let keyboard = InlineKeyboardMarkup::new(buttons);

        bot.send_message(chat_id, text)
            .parse_mode(ParseMode::MarkdownV2)
            .reply_markup(keyboard)
            .await?;
    } else if let Some(dl_data) = data.strip_prefix("dl:") {
        // Trigger download
        let parts: Vec<&str> = dl_data.splitn(2, ':').collect();
        if parts.len() != 2 {
            return Ok(());
        }

        let media_id: uuid::Uuid = match parts[0].parse() {
            Ok(id) => id,
            Err(_) => return Ok(()),
        };
        let search_result_id: i32 = match parts[1].parse() {
            Ok(id) => id,
            Err(_) => return Ok(()),
        };

        let results = db::search_results::get_results_by_media_id(&state.db_pool, media_id)
            .await
            .unwrap_or_default();

        let result = match results.iter().find(|r| r.id == search_result_id) {
            Some(r) => r,
            None => {
                bot.send_message(chat_id, "❌ Source not found").await?;
                return Ok(());
            }
        };

        let magnet_or_url = match result.magnet_link.clone().or_else(|| result.url.clone()) {
            Some(u) => u,
            None => {
                bot.send_message(chat_id, "❌ No download link available")
                    .await?;
                return Ok(());
            }
        };

        let download_event = DownloadRequestedPayload {
            media_id,
            search_result_id,
            magnet_or_url,
            title: result.title.clone(),
        };

        let event_data = serde_json::to_vec(&download_event).unwrap();

        match state
            .jetstream_context
            .publish(events::DOWNLOAD_REQUESTED_SUBJECT, event_data.into())
            .await
        {
            Ok(_) => {
                bot.send_message(chat_id, format!("✅ Download started: {}", result.title))
                    .await?;
            }
            Err(e) => {
                bot.send_message(chat_id, format!("❌ Error: {}", e))
                    .await?;
            }
        }
    }

    Ok(())
}

/// Escape special characters for MarkdownV2
fn escape_md(text: &str) -> String {
    let special = [
        '_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!',
    ];
    let mut result = String::with_capacity(text.len());
    for ch in text.chars() {
        if special.contains(&ch) {
            result.push('\\');
        }
        result.push(ch);
    }
    result
}
