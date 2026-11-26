let hlsLoading = null;

function loadHls() {
  if (window.Hls) return Promise.resolve(window.Hls);
  if (!hlsLoading) {
    hlsLoading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = '/vendor/hls.min.js';
      script.async = true;
      script.onload = () => window.Hls ? resolve(window.Hls) : reject(new Error('hls.js missing'));
      script.onerror = () => reject(new Error('hls.js failed'));
      document.head.append(script);
    }).catch((error) => {
      hlsLoading = null;
      throw error;
    });
  }
  return hlsLoading;
}

/** Attach the server's HLS through the browser on Safari, or the vendored fallback elsewhere. */
export async function attachMedia(video, started, isCurrent, onFatal) {
  const hls = started.type === 'hls' || started.remux || /\.m3u8(\?|$)/i.test(started.url);
  if (!hls || video.canPlayType('application/vnd.apple.mpegurl')) {
    if (isCurrent()) video.src = started.url;
    return null;
  }
  const Hls = await loadHls();
  if (!isCurrent()) return null;
  if (!Hls.isSupported()) throw new Error('HLS unsupported');
  const instance = new Hls({ enableWorker: false });
  instance.on(Hls.Events.ERROR, (_, data) => {
    if (data?.fatal && isCurrent()) onFatal();
  });
  instance.loadSource(started.url);
  instance.attachMedia(video);
  return instance;
}
