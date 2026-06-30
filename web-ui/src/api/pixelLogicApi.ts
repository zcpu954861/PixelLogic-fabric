import type { ApiResponse } from '../model/graphTypes';

export class PixelLogicApiError extends Error {
  constructor(message: string, readonly connected: boolean) {
    super(message);
  }
}

export async function api(path: string, init?: RequestInit): Promise<ApiResponse> {
  let response: Response;
  const headers = new Headers(init?.headers);
  headers.set('Accept', 'application/json');
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  try {
    response = await fetch(path, {
      ...init,
      headers,
    });
  } catch {
    throw new PixelLogicApiError('API 未连接，请确认 PixelLogic API server 已启动。', false);
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.toLowerCase().includes('application/json')) {
    throw new PixelLogicApiError('API 未连接：当前 /api 返回的不是 JSON。', false);
  }

  let data: ApiResponse;
  try {
    data = (await response.json()) as ApiResponse;
  } catch {
    throw new PixelLogicApiError('API 未连接：当前 /api 返回的 JSON 无法解析。', false);
  }

  if (!response.ok || !data.ok) {
    throw new PixelLogicApiError(data.error?.message ?? 'PixelLogic API 返回错误。', response.status !== 503);
  }
  return data;
}
