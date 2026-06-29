export function shortTraceId(traceId: string): string {
  return traceId.length > 8 ? traceId.slice(0, 8) : traceId;
}

export function shortFingerprint(fingerprint: string): string {
  return fingerprint ? fingerprint.slice(0, 8) : '未加载';
}

export function formatTime(raw: string): string {
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) {
    return '--:--:--';
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false });
}

export function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

export function escapeAttr(value: string): string {
  return escapeHtml(value);
}