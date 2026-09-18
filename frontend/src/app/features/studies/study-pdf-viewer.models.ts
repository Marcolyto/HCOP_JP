/** Only uploaded study files from this application may be fetched by the viewer. */
export function safeStudyPdfUrl(value: unknown, origin: string): string {
  if (typeof value !== 'string') return '';
  const raw = value.trim();
  if (!raw || /[\u0000-\u0020\\%]/.test(raw) || raw.startsWith('//')) return '';
  if (!raw.startsWith('/') && !/^https?:\/\//i.test(raw)) return '';
  try {
    const base = new URL(origin);
    const url = new URL(raw, base.origin);
    if (url.origin !== base.origin || url.username || url.password || url.search || url.hash) return '';
    if (!/^\/api\/media\/studies\/[a-z0-9][a-z0-9._-]*$/i.test(url.pathname)) return '';
    // Reject paths which URL parsing would silently normalize across directories.
    if (raw.split('/').some(segment => segment === '.' || segment === '..')) return '';
    return url.pathname;
  } catch { return ''; }
}

/** Preserve full page proportions while bounding each canvas backing store. */
export function studyPdfRasterSize(width: number, pageWidth: number, pageHeight: number, devicePixelRatio: number): {
  readonly cssWidth: number; readonly cssHeight: number; readonly outputScale: number;
  readonly pixelWidth: number; readonly pixelHeight: number;
} {
  const cssWidth = Number.isFinite(width) && width > 0 ? width : 1;
  const ratio = Number.isFinite(pageWidth) && pageWidth > 0 && Number.isFinite(pageHeight) && pageHeight > 0 ? pageHeight / pageWidth : 1;
  const cssHeight = cssWidth * ratio;
  const density = Number.isFinite(devicePixelRatio) && devicePixelRatio > 0 ? Math.min(2, devicePixelRatio) : 1;
  const outputScale = Math.min(density, Math.sqrt(8_000_000 / (cssWidth * cssHeight)), 8192 / Math.max(cssWidth, cssHeight));
  return { cssWidth, cssHeight, outputScale, pixelWidth: Math.max(1, Math.floor(cssWidth * outputScale)), pixelHeight: Math.max(1, Math.floor(cssHeight * outputScale)) };
}
