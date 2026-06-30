export type RichTextConfig = {
  version: number;
  plainText: string;
  segments: Array<{ text: string; style: Record<string, string> }>;
};

export function richTextConfig(plainText: string): string {
  const text = plainText ?? '';
  return JSON.stringify({
    version: 1,
    plainText: text,
    segments: [{ text, style: {} }],
  } satisfies RichTextConfig);
}

export function richTextPlainText(raw = ''): string {
  if (!raw) {
    return '';
  }
  try {
    const parsed = JSON.parse(raw) as Partial<RichTextConfig>;
    return typeof parsed.plainText === 'string' ? parsed.plainText : raw;
  } catch {
    return raw;
  }
}

export function shortRichText(raw = '', max = 42): string {
  const text = richTextPlainText(raw).replace(/\s+/g, ' ').trim();
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}
