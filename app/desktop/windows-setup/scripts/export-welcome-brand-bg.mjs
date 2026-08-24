#!/usr/bin/env node
/**
 * Rasterize welcome_brand_bg.html with the same Chromium CSS as the macOS DMG.
 * Run from app/desktop/dmg-background (needs its playwright install):
 *   node ../windows-setup/scripts/export-welcome-brand-bg.mjs
 */
import { createRequire } from "node:module";
import { createServer } from "node:http";
import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../assets");
const OUT = path.join(ROOT, "welcome_brand_bg.png");
const require = createRequire(path.resolve(__dirname, "../../dmg-background/package.json"));
const { chromium } = require("playwright");

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".png": "image/png",
};

function startStaticServer() {
  return new Promise((resolve, reject) => {
    const server = createServer(async (req, res) => {
      try {
        const urlPath = decodeURIComponent((req.url ?? "/").split("?")[0]);
        const rel = urlPath === "/" ? "/welcome_brand_bg.html" : urlPath;
        const filePath = path.normalize(path.join(ROOT, rel));
        if (!filePath.startsWith(ROOT)) {
          res.writeHead(403).end("Forbidden");
          return;
        }
        const data = await readFile(filePath);
        const ext = path.extname(filePath).toLowerCase();
        res.writeHead(200, { "Content-Type": MIME[ext] ?? "application/octet-stream" });
        res.end(data);
      } catch {
        res.writeHead(404).end("Not found");
      }
    });
    server.listen(0, "127.0.0.1", () => {
      const addr = server.address();
      if (!addr || typeof addr === "string") {
        reject(new Error("bind failed"));
        return;
      }
      resolve({ server, port: addr.port });
    });
    server.on("error", reject);
  });
}

const { server, port } = await startStaticServer();
const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({
    viewport: { width: 480, height: 520 },
    deviceScaleFactor: 2,
  });
  await page.goto(`http://127.0.0.1:${port}/welcome_brand_bg.html`, {
    waitUntil: "networkidle",
  });
  await page.waitForTimeout(200);
  const buf = await page.locator("#bg").screenshot({ type: "png" });
  await writeFile(OUT, buf);
  console.log(`wrote ${OUT} (${buf.length} bytes)`);
} finally {
  await browser.close();
  server.close();
}
