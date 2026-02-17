# 📚 Sokoul v2 - API Documentation

**Version:** 2.0  
**Base URL:** `https://api.sokoul.local/api/v1`  
**Authentication:** JWT Bearer Token  
**Content-Type:** `application/json`

---

## 🔑 Authentication

### Overview

Sokoul uses JWT (JSON Web Tokens) for authentication. All requests must include a valid token in the `Authorization` header.

### Token Format

```bash
Authorization: Bearer <your_jwt_token_here>
```

### Token Structure

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // User ID
  "exp": 1708612800,                              // Expiration time
  "iat": 1708609200,                              // Issued at
  "role": "user"                                  // Role: user|moderator|admin
}
```

### Token Expiry

- **Access Token:** 1 hour
- **Refresh Token:** 7 days
- **Tokens are signed** with RS256 (RSA Signature with SHA-256)

### Getting a Token

#### Register a New User

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "secure_password_123"
}
```

**Response:** `201 Created`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "email": "john@example.com",
  "access_token": "eyJhbGc...",
  "refresh_token": "eyJhbGc...",
  "expires_in": 3600
}
```

#### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "john_doe",
  "password": "secure_password_123"
}
```

**Response:** `200 OK`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "access_token": "eyJhbGc...",
  "refresh_token": "eyJhbGc...",
  "expires_in": 3600
}
```

#### Refresh Access Token

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refresh_token": "eyJhbGc..."
}
```

**Response:** `200 OK`
```json
{
  "access_token": "eyJhbGc...",
  "refresh_token": "eyJhbGc...",
  "expires_in": 3600
}
```

#### Logout

```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

**Response:** `204 No Content`

---

## ⚙️ Common Parameters

### Pagination

```http
GET /api/v1/media?page=1&limit=20&sort=title&order=asc
```

**Parameters:**
- `page` (integer, default: 1) - Page number
- `limit` (integer, default: 20, max: 100) - Items per page
- `sort` (string, default: created_at) - Sort field
- `order` (string, default: desc) - Sort order (asc|desc)

**Response Format:**
```json
{
  "data": [...],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 150,
    "total_pages": 8
  }
}
```

### Rate Limiting

All endpoints are rate-limited:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1708612800
```

**Limits:**
- Per-user: 100 requests/min (authenticated)
- Per-IP: 1000 requests/min (all)
- Search endpoint: 30 requests/min (per-user)
- Download endpoint: 5 concurrent per-user

---

## 🔍 Search Endpoints

### Start Search

Initiate a multi-provider search for media.

```http
POST /api/v1/search
Authorization: Bearer <token>
Content-Type: application/json

{
  "query": "Inception",
  "media_type": "movie",  // "movie" | "tv" | "all"
  "year": 2010,           // Optional
  "providers": ["tmdb", "prowlarr"],  // Optional, all by default
  "timeout": 30           // Optional, seconds
}
```

**Response:** `202 Accepted`
```json
{
  "search_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "query": "Inception",
  "status": "searching",
  "created_at": "2026-02-15T15:28:37Z",
  "estimated_completion": "2026-02-15T15:28:47Z"
}
```

### Get Search Results

```http
GET /api/v1/search/{search_id}
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "search_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "query": "Inception",
  "status": "completed",
  "results": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Inception",
      "year": 2010,
      "tmdb_id": 27205,
      "media_type": "movie",
      "poster_url": "https://image.tmdb.org/...",
      "rating": 8.8,
      "overview": "A skilled thief who steals...",
      "sources": [
        {
          "provider": "prowlarr",
          "title": "Inception 2010 1080p BluRay",
          "quality": "1080p",
          "seeders": 150,
          "leechers": 20,
          "size_bytes": 2147483648,
          "score": 95,
          "magnet_link": "magnet:?xt=urn:btih:..."
        }
      ]
    }
  ],
  "total_results": 1
}
```

### Get Results by Provider

```http
GET /api/v1/search/{search_id}/providers?provider=prowlarr
Authorization: Bearer <token>
```

**Query Parameters:**
- `provider` (string) - Filter by provider (tmdb|prowlarr|jackett|streaming)

**Response:** `200 OK`
```json
{
  "search_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "provider": "prowlarr",
  "results": [...]
}
```

### Score Results with AI

```http
POST /api/v1/search/{search_id}/score
Authorization: Bearer <token>

{
  "quality_preference": "high"  // "high" | "medium" | "any"
}
```

**Response:** `202 Accepted`
```json
{
  "search_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "scoring_job_id": "job-uuid",
  "status": "processing"
}
```

---

## 📽️ Media Endpoints

### List Media

```http
GET /api/v1/media?page=1&limit=20&media_type=movie
Authorization: Bearer <token>
```

**Query Parameters:**
- `page` (integer) - Page number
- `limit` (integer) - Items per page (max: 100)
- `media_type` (string) - Filter: movie|tv|episode
- `sort` (string) - Sort field: title|rating|year|created_at
- `order` (string) - asc|desc
- `genre` (string) - Filter by genre
- `year_from` (integer) - Filter by year range
- `year_to` (integer)
- `rating_min` (decimal) - Filter by min rating
- `search` (string) - Full-text search in title

**Response:** `200 OK`
```json
{
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Inception",
      "media_type": "movie",
      "year": 2010,
      "tmdb_id": 27205,
      "poster_url": "https://image.tmdb.org/...",
      "backdrop_url": "https://image.tmdb.org/...",
      "rating": 8.8,
      "genres": ["action", "sci-fi", "thriller"],
      "overview": "A skilled thief who steals corporate secrets...",
      "runtime_minutes": 148,
      "status": "available"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 150,
    "total_pages": 8
  }
}
```

### Get Media Details

```http
GET /api/v1/media/{media_id}
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Inception",
  "original_title": "Inception",
  "media_type": "movie",
  "year": 2010,
  "tmdb_id": 27205,
  "imdb_id": "tt1375666",
  "poster_url": "https://image.tmdb.org/...",
  "backdrop_url": "https://image.tmdb.org/...",
  "rating": 8.8,
  "genres": ["action", "sci-fi", "thriller"],
  "overview": "A skilled thief who steals corporate secrets...",
  "runtime_minutes": 148,
  "status": "available",
  "created_at": "2026-02-15T15:28:37Z",
  "updated_at": "2026-02-15T15:28:37Z"
}
```

### Get Media Files

```http
GET /api/v1/media/{media_id}/files
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "media_id": "550e8400-e29b-41d4-a716-446655440000",
  "files": [
    {
      "id": "file-uuid",
      "file_path": "/media/movies/inception_2010_1080p.mkv",
      "file_size": 2147483648,
      "codec_video": "h264",
      "codec_audio": "aac",
      "resolution": "1080p",
      "quality_score": 95,
      "source": "torrent",
      "downloaded_at": "2026-02-10T10:00:00Z"
    }
  ]
}
```

### Create Media Entry

```http
POST /api/v1/media
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "New Movie",
  "media_type": "movie",
  "year": 2026,
  "tmdb_id": 12345,
  "genres": ["action", "sci-fi"],
  "rating": 7.5,
  "overview": "Movie description"
}
```

**Response:** `201 Created`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "New Movie",
  ...
}
```

### Update Media

```http
PUT /api/v1/media/{media_id}
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Updated Title",
  "rating": 8.0
}
```

**Response:** `200 OK`

### Delete Media

```http
DELETE /api/v1/media/{media_id}
Authorization: Bearer <token>
```

**Response:** `204 No Content`

### Get Episodes (for TV series)

```http
GET /api/v1/media/{series_id}/episodes?season=1
Authorization: Bearer <token>
```

**Query Parameters:**
- `season` (integer) - Season number
- `page` (integer) - Page number

**Response:** `200 OK`
```json
{
  "series_id": "550e8400-e29b-41d4-a716-446655440000",
  "season": 1,
  "episodes": [
    {
      "id": "episode-uuid",
      "title": "Pilot",
      "episode_number": 1,
      "season_number": 1,
      "tmdb_id": 123456,
      "overview": "Episode description",
      "poster_url": "...",
      "rating": 8.5,
      "runtime_minutes": 42
    }
  ]
}
```

---

## ⬇️ Download Endpoints

### Start Download

```http
POST /api/v1/downloads/start
Authorization: Bearer <token>
Content-Type: application/json

{
  "media_id": "550e8400-e29b-41d4-a716-446655440000",
  "source_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",  // From search results
  "magnet_link": "magnet:?xt=urn:btih:...",
  "quality_preference": "high"  // Optional
}
```

**Response:** `202 Accepted`
```json
{
  "download_id": "download-uuid",
  "media_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "pending",
  "created_at": "2026-02-15T15:28:37Z"
}
```

### List Downloads

```http
GET /api/v1/downloads?status=running&page=1&limit=20
Authorization: Bearer <token>
```

**Query Parameters:**
- `status` (string) - Filter: pending|running|completed|failed|paused
- `page` (integer) - Page number
- `limit` (integer) - Items per page

**Response:** `200 OK`
```json
{
  "data": [
    {
      "download_id": "download-uuid",
      "media_id": "550e8400-e29b-41d4-a716-446655440000",
      "media_title": "Inception",
      "status": "running",
      "progress": 45.5,
      "speed_bytes_per_sec": 1258291,
      "eta_seconds": 3600,
      "seeders": 150,
      "leechers": 20,
      "started_at": "2026-02-15T15:00:00Z",
      "estimated_completion": "2026-02-15T16:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 5
  }
}
```

### Get Download Details

```http
GET /api/v1/downloads/{download_id}
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "download_id": "download-uuid",
  "media_id": "550e8400-e29b-41d4-a716-446655440000",
  "media_title": "Inception",
  "status": "running",
  "progress": 45.5,
  "bytes_downloaded": 966367641,
  "total_bytes": 2147483648,
  "speed_bytes_per_sec": 1258291,
  "eta_seconds": 3600,
  "seeders": 150,
  "leechers": 20,
  "peer_list": [...],
  "error_message": null,
  "started_at": "2026-02-15T15:00:00Z",
  "estimated_completion": "2026-02-15T16:00:00Z"
}
```

### Pause Download

```http
POST /api/v1/downloads/{download_id}/pause
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "download_id": "download-uuid",
  "status": "paused",
  "progress": 45.5
}
```

### Resume Download

```http
POST /api/v1/downloads/{download_id}/resume
Authorization: Bearer <token>
```

**Response:** `200 OK`

### Cancel Download

```http
POST /api/v1/downloads/{download_id}/cancel
Authorization: Bearer <token>
```

**Response:** `200 OK`

---

## 🎬 Streaming Endpoints

### Get Stream Links

```http
GET /api/v1/streaming/direct/{media_id}
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "media_id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Inception",
  "sources": [
    {
      "type": "http",
      "url": "https://stream.sokoul.local/media/...",
      "quality": "1080p",
      "codec_video": "h264",
      "codec_audio": "aac",
      "subtitle_tracks": [
        {
          "language": "en",
          "url": "https://stream.sokoul.local/subs/..."
        }
      ]
    },
    {
      "type": "streaming_provider",
      "provider": "netflix",
      "url": "https://netflix.com/...",
      "quality": "4k"
    }
  ]
}
```

### Get Available Providers

```http
GET /api/v1/streaming/providers/{media_id}
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "media_id": "550e8400-e29b-41d4-a716-446655440000",
  "providers": [
    {
      "provider": "netflix",
      "available": true,
      "quality": "4k",
      "subscription_required": true
    },
    {
      "provider": "local",
      "available": true,
      "quality": "1080p",
      "subscription_required": false
    }
  ]
}
```

### Start Streaming Session

```http
POST /api/v1/streaming/{media_id}/start
Authorization: Bearer <token>
Content-Type: application/json

{
  "quality": "1080p",
  "subtitle_language": "en"
}
```

**Response:** `200 OK`
```json
{
  "session_id": "session-uuid",
  "stream_url": "https://stream.sokoul.local/...",
  "expires_at": "2026-02-15T17:28:37Z"
}
```

---

## 📚 Library Endpoints

### Add to Favorites

```http
POST /api/v1/library/favorites
Authorization: Bearer <token>
Content-Type: application/json

{
  "media_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:** `201 Created`

### List Favorites

```http
GET /api/v1/library/favorites?page=1&limit=20
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Inception",
      ...
    }
  ],
  "pagination": {...}
}
```

### Remove from Favorites

```http
DELETE /api/v1/library/favorites/{media_id}
Authorization: Bearer <token>
```

**Response:** `204 No Content`

### Add to Watchlist

```http
POST /api/v1/library/watchlist
Authorization: Bearer <token>
Content-Type: application/json

{
  "media_id": "550e8400-e29b-41d4-a716-446655440000",
  "priority": 1  // Optional, 1-100
}
```

**Response:** `201 Created`

### List Watchlist

```http
GET /api/v1/library/watchlist?sort=priority&order=asc
Authorization: Bearer <token>
```

**Response:** `200 OK`

### Remove from Watchlist

```http
DELETE /api/v1/library/watchlist/{media_id}
Authorization: Bearer <token>
```

**Response:** `204 No Content`

---

## 👁️ Watch History Endpoints

### Record Watch Event

```http
POST /api/v1/history/watch
Authorization: Bearer <token>
Content-Type: application/json

{
  "media_id": "550e8400-e29b-41d4-a716-446655440000",
  "progress_seconds": 3600,
  "total_seconds": 7200,
  "completed": false
}
```

**Response:** `200 OK`

### Get Continue Watching

```http
GET /api/v1/history/continue?limit=10
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "data": [
    {
      "media_id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Inception",
      "progress": 50.0,
      "last_watched": "2026-02-15T15:28:37Z",
      "resume_from_seconds": 3600
    }
  ]
}
```

### Get Watched Media

```http
GET /api/v1/history/watched?page=1&limit=20
Authorization: Bearer <token>
```

**Response:** `200 OK`

---

## 📦 Collections Endpoints

### Create Collection

```http
POST /api/v1/collections
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "My Favorites",
  "description": "Personal favorite movies",
  "is_public": false
}
```

**Response:** `201 Created`
```json
{
  "id": "collection-uuid",
  "name": "My Favorites",
  ...
}
```

### List Collections

```http
GET /api/v1/collections?page=1&limit=20
Authorization: Bearer <token>
```

**Response:** `200 OK`

### Get Collection Details

```http
GET /api/v1/collections/{collection_id}
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "id": "collection-uuid",
  "name": "My Favorites",
  "description": "Personal favorite movies",
  "is_public": false,
  "created_at": "2026-02-15T15:28:37Z",
  "updated_at": "2026-02-15T15:28:37Z",
  "media_count": 15,
  "media": [...]
}
```

### Add Media to Collection

```http
POST /api/v1/collections/{collection_id}/media
Authorization: Bearer <token>
Content-Type: application/json

{
  "media_id": "550e8400-e29b-41d4-a716-446655440000",
  "position": 1  // Optional
}
```

**Response:** `201 Created`

### Remove Media from Collection

```http
DELETE /api/v1/collections/{collection_id}/media/{media_id}
Authorization: Bearer <token>
```

**Response:** `204 No Content`

---

## 🔒 Admin/Security Endpoints

### View Audit Logs

```http
GET /api/v1/admin/audit-logs?page=1&limit=50
Authorization: Bearer <token>  // Admin token required
```

**Response:** `200 OK`
```json
{
  "data": [
    {
      "id": "log-uuid",
      "user_id": "550e8400-e29b-41d4-a716-446655440000",
      "action": "download_started",
      "resource": "media",
      "resource_id": "media-uuid",
      "old_value": null,
      "new_value": {
        "status": "pending"
      },
      "ip_address": "192.168.1.100",
      "timestamp": "2026-02-15T15:28:37Z"
    }
  ]
}
```

### IP Whitelist Management

```http
POST /api/v1/admin/security/whitelist
Authorization: Bearer <token>  // Admin token required
Content-Type: application/json

{
  "ip_address": "192.168.1.100",
  "description": "Home network"
}
```

**Response:** `201 Created`

### Get User List (Admin)

```http
GET /api/v1/admin/users?page=1&limit=20
Authorization: Bearer <token>  // Admin token required
```

**Response:** `200 OK`

### Change User Role (Admin)

```http
PUT /api/v1/admin/users/{user_id}/role
Authorization: Bearer <token>  // Admin token required
Content-Type: application/json

{
  "role": "moderator"  // "user" | "moderator" | "admin"
}
```

**Response:** `200 OK`

### Delete User (Admin)

```http
DELETE /api/v1/admin/users/{user_id}
Authorization: Bearer <token>  // Admin token required
```

**Response:** `204 No Content`

---

## 🏥 Health & Monitoring Endpoints

### Basic Health Check

```http
GET /health
```

**Response:** `200 OK`
```json
{
  "status": "healthy",
  "version": "2.0.0",
  "timestamp": "2026-02-15T15:28:37Z"
}
```

### Deep Health Check

```http
GET /health/deep
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "status": "healthy",
  "components": {
    "api": "healthy",
    "database": "healthy",
    "cache": "healthy",
    "message_queue": "healthy"
  },
  "timestamp": "2026-02-15T15:28:37Z"
}
```

### Prometheus Metrics

```http
GET /metrics
```

**Response:** `200 OK`
```text
# HELP sokoul_api_requests_total Total HTTP requests
# TYPE sokoul_api_requests_total counter
sokoul_api_requests_total{endpoint="/api/v1/search",method="POST",status="200"} 1234

# HELP sokoul_api_request_duration_seconds HTTP request latency
# TYPE sokoul_api_request_duration_seconds histogram
sokoul_api_request_duration_seconds_bucket{endpoint="/api/v1/media",le="0.01"} 50
...
```

---

## 🌐 WebSocket Connection

### Connect to WebSocket

```javascript
const ws = new WebSocket(
  'wss://api.sokoul.local/ws?token=<jwt_token>'
);

ws.onopen = () => {
  console.log('Connected');
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log('Message:', message);
};

ws.onerror = (error) => {
  console.error('Error:', error);
};
```

### Message Types

**Search Results Update**
```json
{
  "type": "search_result",
  "search_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "result": {
    "title": "Inception",
    "provider": "prowlarr",
    ...
  }
}
```

**Download Progress Update**
```json
{
  "type": "download_progress",
  "download_id": "download-uuid",
  "progress": 45.5,
  "speed_bytes_per_sec": 1258291,
  "eta_seconds": 3600
}
```

**Notification**
```json
{
  "type": "notification",
  "level": "info",  // "info" | "warning" | "error"
  "message": "Download completed successfully",
  "timestamp": "2026-02-15T15:28:37Z"
}
```

**Heartbeat**
```json
{
  "type": "ping"
}
```

---

## ⚠️ Error Codes

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK - Success |
| 201 | Created - Resource created |
| 202 | Accepted - Async job accepted |
| 204 | No Content - Success, no response body |
| 400 | Bad Request - Invalid parameters |
| 401 | Unauthorized - Missing/invalid token |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource not found |
| 409 | Conflict - Resource already exists |
| 429 | Too Many Requests - Rate limit exceeded |
| 500 | Internal Server Error |
| 503 | Service Unavailable - Database down |

### Error Response Format

```json
{
  "error": {
    "code": "INVALID_MEDIA_TYPE",
    "message": "Media type must be 'movie' or 'tv'",
    "details": {
      "field": "media_type",
      "received": "invalid"
    }
  }
}
```

### Common Error Codes

| Code | Description |
|------|-------------|
| `INVALID_TOKEN` | JWT token is invalid or expired |
| `MISSING_TOKEN` | Authorization header missing |
| `UNAUTHORIZED` | User not authenticated |
| `FORBIDDEN` | User lacks permissions |
| `NOT_FOUND` | Resource not found |
| `VALIDATION_ERROR` | Input validation failed |
| `RATE_LIMIT` | Rate limit exceeded |
| `DATABASE_ERROR` | Database operation failed |
| `EXTERNAL_API_ERROR` | External API call failed |
| `QUEUE_ERROR` | Job queue error |

---

## 📊 Rate Limiting

### Limits by Endpoint

| Endpoint | Authenticated | Unauthenticated |
|----------|---------------|-----------------|
| Search | 30/min | 5/min |
| Download | 5 concurrent | 1 concurrent |
| Media API | 100/min | 20/min |
| Download API | 50/min | 10/min |
| Library API | 100/min | N/A |
| Auth | N/A | 5 attempts/min |

### Rate Limit Headers

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1708612800
```

When rate limited, receive `429 Too Many Requests`:
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded",
    "retry_after": 60
  }
}
```

---

## 📚 API Examples

### Complete Search Flow

```bash
# 1. Get token
curl -X POST https://api.sokoul.local/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"pass123"}'
# Returns: {"access_token": "eyJhbGc...", ...}

# 2. Start search
curl -X POST https://api.sokoul.local/api/v1/search \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{"query":"Inception","media_type":"movie"}'
# Returns: {"search_id": "f47ac10b-...", ...}

# 3. Poll for results
curl -X GET https://api.sokoul.local/api/v1/search/f47ac10b-... \
  -H "Authorization: Bearer eyJhbGc..."
# Returns: {"results": [...], "status": "completed"}

# 4. Start download
curl -X POST https://api.sokoul.local/api/v1/downloads/start \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{"media_id":"550e8400-...","magnet_link":"magnet:?xt=..."}'
# Returns: {"download_id": "download-uuid", "status": "pending"}
```

---

**API Version:** 2.0  
**Last Updated:** February 2026  
**Authentication Required:** Yes (JWT Bearer Token)
