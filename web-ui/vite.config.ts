import { defineConfig } from 'vite';
import type { ServerResponse } from 'node:http';

function writeApiUnavailable(res: ServerResponse | undefined): void {
  if (!res || res.headersSent) {
    return;
  }
  res.statusCode = 503;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(JSON.stringify({ ok: false, error: { message: 'API 未连接，请确认 PixelLogic API server 已启动。' } }));
}

export default defineConfig({
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:18111',
        changeOrigin: false,
        proxyTimeout: 2000,
        timeout: 2000,
        configure(proxy) {
          proxy.on('error', (_error, _request, response) => {
            writeApiUnavailable(response as ServerResponse | undefined);
          });
        },
      },
    },
  },
});
