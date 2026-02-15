use redis::{AsyncCommands, Client, RedisResult};
use serde::{de::DeserializeOwned, Serialize};

pub async fn get_from_cache<T: DeserializeOwned>(client: &Client, key: &str) -> RedisResult<Option<T>> {
    let mut con = client.get_multiplexed_async_connection().await?;
    let data: Option<String> = con.get(key).await?;
    match data {
        Some(json) => {
            match serde_json::from_str(&json) {
                Ok(val) => Ok(Some(val)),
                Err(_) => Ok(None),
            }
        },
        None => Ok(None),
    }
}

pub async fn set_to_cache<T: Serialize>(client: &Client, key: &str, value: &T) -> RedisResult<()> {
    let mut con = client.get_multiplexed_async_connection().await?;
    let json = serde_json::to_string(value).unwrap();
    con.set_ex(key, json, 3600).await
}

pub async fn set_to_cache_with_ttl<T: Serialize>(client: &Client, key: &str, value: &T, ttl_secs: u64) -> RedisResult<()> {
    let mut con = client.get_multiplexed_async_connection().await?;
    let json = serde_json::to_string(value).unwrap();
    con.set_ex(key, json, ttl_secs).await
}

pub async fn delete_from_cache(client: &Client, key: &str) -> RedisResult<()> {
    let mut con = client.get_multiplexed_async_connection().await?;
    con.del(key).await
}
