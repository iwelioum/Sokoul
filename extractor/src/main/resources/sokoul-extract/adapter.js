(function (G) {
  G.fetch = function (url, init) {
    init = init || {};
    Object.keys(init.headers || {}).forEach(function (key) { if (key.toLowerCase() === 'user-agent') G.__extractionUserAgent = init.headers[key]; });
    return new Promise(function (resolve, reject) {
      var signal = init.signal, handle;
      var abort = function () { if (handle) handle.abort(); reject(new DOMException('cancelled', 'AbortError')); };
      if (signal && signal.aborted) return abort();
      var clean = function () { if (signal) signal.removeEventListener('abort', abort); };
      handle = GM_xmlhttpRequest({ url: String(url), method: init.method || 'GET', headers: init.headers || {}, data: init.body,
        redirect: init.redirect || 'follow', responseType: 'arraybuffer', timeout: 12000,
        onload: function (r) {
          clean();
          var headers = {};
          r.responseHeaders.split(/\r?\n/).forEach(function (line) { var p = line.indexOf(':'); if(p > 0) headers[line.slice(0,p).toLowerCase()] = line.slice(p+1).trim(); });
          var text = function () { return new TextDecoder().decode(r.response); };
          resolve({ status: r.status, ok: r.status >= 200 && r.status < 300, url: r.finalUrl,
            headers: { get: function (name) { return headers[String(name).toLowerCase()] || null; } },
            text: function () { return Promise.resolve(text()); }, json: function () { return Promise.resolve(JSON.parse(text())); },
            arrayBuffer: function () { return Promise.resolve(r.response); }, body: { cancel: function () { return Promise.resolve(); } } });
        },
        onerror: function () { clean(); reject(new Error('network_error')); },
        ontimeout: function () { clean(); reject(new Error('timeout')); }, onabort: function () { clean(); reject(new DOMException('cancelled', 'AbortError')); }
      });
      if (signal) signal.addEventListener('abort', abort, { once: true });
    });
  };
  G.SokoulExtractors = G.SokoulExtractionCore.createExtractors(G);
  G.sokoulExtractM3u8 = async function (_, url) {
    var source = G.SokoulExtractors.detectEmbedType(url);
    if (!source) return { success: false, error: 'unsupported_host' };
    var result = await G.SokoulExtractors.extractSingle(source, url);
    var media = result.m3u8Url || result.hlsUrl || result.url;
    if (!result.success || !media) return { success: false, error: 'no_media' };
    var rule = await G.SokoulExtractors.setupHeadersForService(source, media, result.referer || url);
    result.headers = Object.assign({}, rule && rule.headers);
    if (result.referer) result.headers.Referer = result.referer;
    if (result.origin) result.headers.Origin = result.origin;
    result.headers['User-Agent'] = result.headers['User-Agent'] || G.__extractionUserAgent || navigator.userAgent;
    result.url = media;
    result.format = result.format || (/\.mp4(?:[?#]|$)/i.test(media) ? 'mp4' : /\.m3u8(?:[?#]|$)|\/hls\//i.test(media) || ['voe','fsvid','vidzy','vidmoly','veev','lulustream','vidara','seekstreaming'].indexOf(source) >= 0 ? 'hls' : 'mp4');
    return result;
  };
})(globalThis);
