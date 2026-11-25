/**
 * CORS middleware configuration.
 * Extracted from server.js -- restricted origin policy. Allowed browser origins
 * come from SOKOUL_ALLOWED_ORIGINS (see utils/allowedOrigins.js); loopback only
 * by default.
 */

const cors = require("cors");
const { isOAuthCorsOriginAllowed } = require('../utils/oauthClients');
const { isAllowedOrigin } = require('../utils/allowedOrigins');

function isAllowedOAuthOrigin(origin) {
  return isOAuthCorsOriginAllowed(origin);
}

const corsMiddleware = cors({
  origin: (origin, callback) => {
    // Allow requests with no origin (like mobile apps or curl requests)
    if (!origin) return callback(null, true);

    if (isAllowedOrigin(origin) || isAllowedOAuthOrigin(origin)) {
      callback(null, true);
    } else {
      callback(new Error("Not allowed by CORS"));
    }
  },
  methods: ["GET", "POST", "OPTIONS", "PUT", "DELETE", "PATCH", "HEAD"],
  allowedHeaders: [
    "Content-Type",
    "Authorization",
    "X-Requested-With",
    "Accept",
    "Origin",
    "X-No-Compression",
    "Access-Control-Request-Headers",
    "baggage",
    "sentry-trace",
    "x-profile-id",
    "x-access-key",
    "x-sokoul-client-id",
    "x-turnstile-token",
  ],
  credentials: true,
  maxAge: 600,
  optionsSuccessStatus: 204,
});

module.exports = corsMiddleware;
