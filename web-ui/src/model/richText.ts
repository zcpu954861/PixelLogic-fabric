export type MinecraftColor =
  | 'black'
  | 'dark_blue'
  | 'dark_green'
  | 'dark_aqua'
  | 'dark_red'
  | 'dark_purple'
  | 'gold'
  | 'gray'
  | 'dark_gray'
  | 'blue'
  | 'green'
  | 'aqua'
  | 'red'
  | 'light_purple'
  | 'yellow'
  | 'white';

export type HexColor = `#${string}`;
export type RichTextColor = MinecraftColor | HexColor;

export type RichTextStyle = {
  color?: RichTextColor;
  bold?: boolean;
  italic?: boolean;
  underlined?: boolean;
  strikethrough?: boolean;
  obfuscated?: boolean;
};

export type RichTextSegment = {
  text: string;
  style: RichTextStyle;
};

export type RichTextConfig = {
  version: number;
  plainText: string;
  segments: RichTextSegment[];
};

export const richTextStyleKeys = ['bold', 'italic', 'underlined', 'strikethrough', 'obfuscated'] as const;
export type RichTextStyleKey = typeof richTextStyleKeys[number];

export type RichTextSelectionState = {
  color?: RichTextColor | '';
  styles: Record<RichTextStyleKey, boolean>;
};

export const minecraftColors: Array<{ value: MinecraftColor | ''; label: string; css: string }> = [
  { value: '', label: '默认', css: '#17211d' },
  { value: 'black', label: '黑色', css: '#000000' },
  { value: 'dark_gray', label: '深灰', css: '#555555' },
  { value: 'gray', label: '灰色', css: '#aaaaaa' },
  { value: 'white', label: '白色', css: '#ffffff' },
  { value: 'red', label: '红色', css: '#ff5555' },
  { value: 'dark_red', label: '深红', css: '#aa0000' },
  { value: 'gold', label: '金色', css: '#ffaa00' },
  { value: 'yellow', label: '黄色', css: '#ffff55' },
  { value: 'green', label: '绿色', css: '#55ff55' },
  { value: 'dark_green', label: '深绿', css: '#00aa00' },
  { value: 'aqua', label: '青色', css: '#55ffff' },
  { value: 'dark_aqua', label: '深青', css: '#00aaaa' },
  { value: 'dark_blue', label: '深蓝', css: '#0000aa' },
  { value: 'blue', label: '蓝色', css: '#5555ff' },
  { value: 'dark_purple', label: '紫色', css: '#aa00aa' },
  { value: 'light_purple', label: '粉紫', css: '#ff55ff' },
];

const colorValues = new Set<string>(minecraftColors.map((color) => color.value).filter(Boolean));
const hexColorPattern = /^#[0-9a-f]{6}$/i;
const styleKeys = richTextStyleKeys;

export function normalizeRichTextColor(value: string): RichTextColor | undefined {
  if (colorValues.has(value)) {
    return value as MinecraftColor;
  }
  if (hexColorPattern.test(value)) {
    return value.toLowerCase() as HexColor;
  }
  return undefined;
}

export function richTextColorCss(color: RichTextColor | undefined): string | undefined {
  if (!color) {
    return undefined;
  }
  if (hexColorPattern.test(color)) {
    return color;
  }
  return minecraftColors.find((item) => item.value === color)?.css;
}

export function richTextConfig(plainText: string): string {
  return serializeRichText({ segments: [{ text: plainText ?? '', style: {} }] });
}

export function richTextPlainText(raw = ''): string {
  return normalizeRichText(raw).plainText;
}

export function normalizeRichText(raw = ''): RichTextConfig {
  if (!raw) {
    return configFromSegments([]);
  }
  try {
    const parsed = JSON.parse(raw) as Partial<RichTextConfig>;
    const parsedSegments = Array.isArray(parsed.segments)
      ? parsed.segments
        .filter((segment) => typeof segment?.text === 'string')
        .map((segment) => {
          const item = segment as Partial<RichTextSegment> & { text: string };
          return { text: item.text, style: normalizeStyle(item.style) };
        })
      : [];
    if (parsedSegments.length > 0) {
      return configFromSegments(parsedSegments);
    }
    if (typeof parsed.plainText === 'string') {
      return configFromSegments([{ text: parsed.plainText, style: {} }]);
    }
  } catch {
    return configFromSegments([{ text: raw, style: {} }]);
  }
  return configFromSegments([{ text: raw, style: {} }]);
}

export function serializeRichText(config: { segments: RichTextSegment[] }): string {
  return JSON.stringify(configFromSegments(config.segments));
}

export function updateRichTextPlainText(raw: string, nextPlainText: string): string {
  const config = normalizeRichText(raw);
  const previous = config.plainText;
  const next = nextPlainText ?? '';
  if (previous === next) {
    return serializeRichText(config);
  }

  let prefix = 0;
  while (prefix < previous.length && prefix < next.length && previous[prefix] === next[prefix]) {
    prefix += 1;
  }

  let suffix = 0;
  while (
    suffix < previous.length - prefix
    && suffix < next.length - prefix
    && previous[previous.length - 1 - suffix] === next[next.length - 1 - suffix]
  ) {
    suffix += 1;
  }

  const oldEnd = previous.length - suffix;
  const inserted = next.slice(prefix, next.length - suffix);
  const segments = [
    ...sliceSegments(config.segments, 0, prefix),
    ...(inserted ? [{ text: inserted, style: styleAt(config.segments, prefix) }] : []),
    ...sliceSegments(config.segments, oldEnd, previous.length),
  ];
  return serializeRichText({ segments });
}

export function applyRichTextStyle(
  raw: string,
  start: number,
  end: number,
  patch: Partial<RichTextStyle>,
  options: { clear?: boolean } = {},
): string {
  const config = normalizeRichText(raw);
  const from = Math.max(0, Math.min(start, end));
  const to = Math.min(config.plainText.length, Math.max(start, end));
  if (from === to) {
    return serializeRichText(config);
  }

  const styled = sliceSegments(config.segments, from, to).map((segment) => ({
    text: segment.text,
    style: options.clear ? {} : cleanStyle({ ...segment.style, ...patch }),
  }));
  return serializeRichText({
    segments: [
      ...sliceSegments(config.segments, 0, from),
      ...styled,
      ...sliceSegments(config.segments, to, config.plainText.length),
    ],
  });
}

export function richTextSelectionState(raw: string, start: number, end: number): RichTextSelectionState {
  const config = normalizeRichText(raw);
  const from = Math.max(0, Math.min(start, end));
  const to = Math.min(config.plainText.length, Math.max(start, end));
  const selected = from === to ? [] : sliceSegments(config.segments, from, to);
  const styles = Object.fromEntries(
    styleKeys.map((key) => [key, selected.length > 0 && selected.every((segment) => segment.style[key] === true)]),
  ) as Record<RichTextStyleKey, boolean>;
  if (selected.length === 0) {
    return { styles };
  }
  const firstColor = selected[0].style.color ?? '';
  const color = selected.every((segment) => (segment.style.color ?? '') === firstColor) ? firstColor : undefined;
  return { color, styles };
}

export function shortRichText(raw = '', max = 42): string {
  const text = richTextPlainText(raw).replace(/\s+/g, ' ').trim();
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

function configFromSegments(rawSegments: RichTextSegment[]): RichTextConfig {
  const segments = mergeSegments(rawSegments
    .map((segment) => ({ text: segment.text ?? '', style: normalizeStyle(segment.style) }))
    .filter((segment) => segment.text.length > 0));
  const plainText = segments.map((segment) => segment.text).join('');
  return {
    version: 1,
    plainText,
    segments: segments.length > 0 ? segments : [{ text: '', style: {} }],
  };
}

function sliceSegments(segments: RichTextSegment[], start: number, end: number): RichTextSegment[] {
  const result: RichTextSegment[] = [];
  let cursor = 0;
  for (const segment of segments) {
    const segmentStart = cursor;
    const segmentEnd = cursor + segment.text.length;
    cursor = segmentEnd;
    if (segmentEnd <= start || segmentStart >= end) {
      continue;
    }
    const from = Math.max(start, segmentStart) - segmentStart;
    const to = Math.min(end, segmentEnd) - segmentStart;
    const text = segment.text.slice(from, to);
    if (text) {
      result.push({ text, style: { ...segment.style } });
    }
  }
  return result;
}

function styleAt(segments: RichTextSegment[], index: number): RichTextStyle {
  let cursor = 0;
  for (const segment of segments) {
    const next = cursor + segment.text.length;
    if (index <= next) {
      return { ...segment.style };
    }
    cursor = next;
  }
  return segments.length > 0 ? { ...segments[segments.length - 1].style } : {};
}

function mergeSegments(segments: RichTextSegment[]): RichTextSegment[] {
  const result: RichTextSegment[] = [];
  for (const segment of segments) {
    if (!segment.text) {
      continue;
    }
    const previous = result[result.length - 1];
    if (previous && sameStyle(previous.style, segment.style)) {
      previous.text += segment.text;
    } else {
      result.push({ text: segment.text, style: { ...segment.style } });
    }
  }
  return result;
}

function normalizeStyle(raw: unknown): RichTextStyle {
  if (!raw || typeof raw !== 'object') {
    return {};
  }
  const source = raw as Record<string, unknown>;
  const style: RichTextStyle = {};
  if (typeof source.color === 'string') {
    const color = normalizeRichTextColor(source.color);
    if (color) {
      style.color = color;
    }
  }
  styleKeys.forEach((key) => {
    if (source[key] === true) {
      style[key] = true;
    }
  });
  return style;
}

function cleanStyle(style: Partial<RichTextStyle>): RichTextStyle {
  const result: RichTextStyle = {};
  if (style.color) {
    const color = normalizeRichTextColor(style.color);
    if (color) {
      result.color = color;
    }
  }
  styleKeys.forEach((key) => {
    if (style[key] === true) {
      result[key] = true;
    }
  });
  return result;
}

function sameStyle(left: RichTextStyle, right: RichTextStyle): boolean {
  return JSON.stringify(cleanStyle(left)) === JSON.stringify(cleanStyle(right));
}
