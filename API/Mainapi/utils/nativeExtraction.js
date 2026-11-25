'use strict';

const axios = require('axios');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { internalHeaders } = require('./mediaSigning');

let enginePromise;
async function loadEngine() {
    if (!enginePromise) enginePromise = (async () => {
        const bundled = path.join(__dirname, '../generated/nativeExtractors/index.cjs');
        if (fs.existsSync(bundled)) return require(bundled);
        // Source-checkout launches build once outside the repository; Docker uses its bundled assets.
        const script = path.resolve(__dirname, '../../../extraction/scripts/build-native-extractors.mjs');
        const output = fs.mkdtempSync(path.join(os.tmpdir(), 'sokoul-native-extractors-'));
        const { buildNativeExtractors } = await import(pathToFileURL(script).href);
        await buildNativeExtractors(output);
        return require(path.join(output, 'index.cjs'));
    })();
    return enginePromise;
}

function createNativeResolver({ engine = loadEngine, post = (url, body, options) => axios.post(url, body, options) } = {}) {
    // Shared across catalogue routes: opening many providers must not spawn dozens of VMs.
    let active = 0;
    const waiting = [];
    const enter = (signal) => new Promise((resolve) => {
        if (signal.aborted || waiting.length >= 128) return resolve(false);
        if (active < 4) { active++; return resolve(true); }
        const job = () => { signal.removeEventListener('abort', abort); active++; resolve(true); };
        const abort = () => {
            const index = waiting.indexOf(job);
            if (index >= 0) waiting.splice(index, 1);
            resolve(false);
        };
        signal.addEventListener('abort', abort, { once: true });
        waiting.push(job);
    });
    const leave = () => { active--; waiting.shift()?.(); };
    return async function resolve(embedUrl, accessKey, proxyBase) {
        const controller = new AbortController();
        const deadline = setTimeout(() => controller.abort(), 8_000);
        let entered = false;
        try {
            entered = await enter(controller.signal);
            if (!entered) return null;
            const options = {
                headers: internalHeaders({ 'x-access-key': accessKey || '', Accept: 'application/json' }),
                timeout: 8_000, signal: controller.signal,
                maxContentLength: 8 * 1024 * 1024, maxBodyLength: 4 * 1024 * 1024,
            };
            const runtime = await engine();
            const fetch = async (url, init = {}) => {
                const body = init.body == null ? undefined : typeof init.body === 'string' ? init.body : Buffer.from(init.body).toString('base64');
                const response = await post(`${proxyBase}/api/native-fetch`, {
                    url: String(url), method: init.method || 'GET', headers: Object.fromEntries(new Headers(init.headers || {}).entries()),
                    body, encoding: init.body != null && typeof init.body !== 'string' ? 'base64' : undefined,
                    redirect: init.redirect || 'follow',
                }, { ...options, signal: init.signal ? AbortSignal.any([init.signal, controller.signal]) : controller.signal });
                const value = response.data;
                const bytes = Buffer.from(value.body || '', 'base64');
                const status = Number(value.status);
                const empty = [101, 204, 205, 304].includes(status) || (init.method || '').toUpperCase() === 'HEAD';
                const result = new Response(empty ? null : bytes, { status, headers: value.headers || {} });
                Object.defineProperty(result, 'url', { value: value.url || String(url) });
                return result;
            };
            const result = await runtime.extract(embedUrl, { fetch, timeoutMs: 6_500, signal: controller.signal });
            if (!result?.success || !result.url) return null;
            const registered = await post(`${proxyBase}/api/native-media`, result, options);
            const media = registered.data;
            if (typeof media?.url !== 'string') return null;
            // Origin/Cookie belong to the relay's private state, never to a public source payload.
            return { url: media.url, format: media.format || result.format, source: result.source, subtitles: media.subtitles || [] };
        } catch {
            // One host failure triggers the existing extractor fallback; signed URLs and keys stay private.
            return null;
        } finally {
            clearTimeout(deadline);
            if (entered) leave();
        }
    };
}

module.exports = { createNativeResolver, extractNative: createNativeResolver() };
