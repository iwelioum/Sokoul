const test = require('node:test');
const assert = require('node:assert/strict');

const { isAllowedOrigin, readAllowedOrigins } = require('../allowedOrigins');

test('loopback origins only by default', () => {
  const env = {};
  for (const origin of ['http://localhost:3000', 'http://127.0.0.1:8088', 'https://localhost', 'http://[::1]:5173']) {
    assert.equal(isAllowedOrigin(origin, env), true, origin);
  }
  for (const origin of ['https://sokoul.example', 'http://192.168.0.10:8088', 'null', '', undefined, 'not a url', 'file:///x']) {
    assert.equal(isAllowedOrigin(origin, env), false, String(origin));
  }
});

test('SOKOUL_ALLOWED_ORIGINS adds exact origins and bare hosts', () => {
  const env = { SOKOUL_ALLOWED_ORIGINS: ' https://Sokoul.example/ , http://192.168.0.10:8088, media.home.arpa, bogus value' };
  assert.equal(readAllowedOrigins(env).length, 3);
  assert.equal(isAllowedOrigin('https://sokoul.example', env), true);
  assert.equal(isAllowedOrigin('https://sokoul.example/watch/42', env), true, 'referer URL');
  assert.equal(isAllowedOrigin('http://sokoul.example', env), false, 'scheme must match');
  assert.equal(isAllowedOrigin('https://sokoul.example:8443', env), false, 'port must match');
  assert.equal(isAllowedOrigin('https://evil.sokoul.example', env), false, 'no implied subdomains');
  assert.equal(isAllowedOrigin('https://sokoul.example.evil.invalid', env), false);
  assert.equal(isAllowedOrigin('http://192.168.0.10:8088', env), true);
  assert.equal(isAllowedOrigin('http://192.168.0.10:9999', env), false);
  assert.equal(isAllowedOrigin('https://media.home.arpa:1234', env), true, 'bare host: any port');
  assert.equal(isAllowedOrigin('http://localhost:3000', env), true, 'loopback stays allowed');
});

test('domainRestriction uses the shared allowlist', () => {
  const { domainRestriction } = require('../../middleware/security');
  const run = (headers, path = '/api/search') => {
    let passed = false;
    let status = 200;
    const req = { path, get: (name) => headers[name.toLowerCase()] };
    const res = { status(code) { status = code; return this; }, json() { return this; } };
    domainRestriction(req, res, () => { passed = true; });
    return passed ? 'next' : status;
  };
  assert.equal(run({}), 'next', 'no Origin nor Referer');
  assert.equal(run({ origin: 'http://localhost:5173' }), 'next');
  assert.equal(run({ referer: 'http://127.0.0.1:8088/watch' }), 'next');
  assert.equal(run({ origin: 'https://third-party.invalid' }), 404);
});
