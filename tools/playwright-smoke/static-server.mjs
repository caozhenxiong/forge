import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

export async function startStaticServer(rootDir) {
  const normalizedRoot = path.resolve(rootDir);
  return new Promise((resolve, reject) => {
    const server = http.createServer(async (req, res) => {
      try {
        const urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
        const targetPath = path.resolve(normalizedRoot, '.' + (urlPath === '/' ? '/index.html' : urlPath));
        if (!targetPath.startsWith(normalizedRoot)) {
          res.writeHead(403);
          res.end('forbidden');
          return;
        }
        const body = await readFile(targetPath);
        res.writeHead(200, { 'Content-Type': contentType(targetPath) });
        res.end(body);
      } catch {
        res.writeHead(404);
        res.end('not found');
      }
    });
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      resolve({ server, port: address.port });
    });
    server.on('error', reject);
  });
}

function contentType(filePath) {
  if (filePath.endsWith('.html')) return 'text/html; charset=utf-8';
  if (filePath.endsWith('.js') || filePath.endsWith('.mjs') || filePath.endsWith('.cjs')) {
    return 'application/javascript; charset=utf-8';
  }
  if (filePath.endsWith('.css')) return 'text/css; charset=utf-8';
  return 'application/octet-stream';
}
