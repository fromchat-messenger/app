#!/usr/bin/env node
/**
 * Export the DMG artboard to PNG + icon slot positions for create-dmg.
 *
 * Args: <stageDir> <distDir>
 *
 * Outputs under distDir:
 *   dmg-background.png
 *   dmg-background@2x.png
 *   icon-positions.json
 */

import { chromium } from "playwright";
import { createServer } from "node:http";
import { readFile, mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { readFile as readFileSync } from "node:fs/promises";

const [stageArg, distArg] = process.argv.slice(2);
if (!stageArg || !distArg) {
  console.error("Usage: node export.mjs <stageDir> <distDir>");
  process.exit(1);
}

const ROOT = path.resolve(stageArg);
const DIST = path.resolve(distArg);
const CONFIG_PATH = path.join(ROOT, "dmg-config.json");

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".png": "image/png",
  ".ttf": "font/ttf",
};

function startStaticServer() {
  return new Promise((resolve, reject) => {
    const server = createServer(async (req, res) => {
      try {
        const urlPath = decodeURIComponent((req.url ?? "/").split("?")[0]);
        const rel = urlPath === "/" ? "/index.html" : urlPath;
        const filePath = path.normalize(path.join(ROOT, rel));
        if (!filePath.startsWith(ROOT)) {
          res.writeHead(403).end("Forbidden");
          return;
        }
        const data = await readFile(filePath);
        const ext = path.extname(filePath).toLowerCase();
        res.writeHead(200, {
          "Content-Type": MIME[ext] ?? "application/octet-stream",
          "Cache-Control": "no-store",
        });
        res.end(data);
      } catch {
        res.writeHead(404).end("Not found");
      }
    });

    server.listen(0, "127.0.0.1", () => {
      const addr = server.address();
      if (!addr || typeof addr === "string") {
        reject(new Error("Failed to bind static server"));
        return;
      }
      resolve({ server, port: addr.port });
    });
    server.on("error", reject);
  });
}

function round(n) {
  return Math.round(n * 100) / 100;
}

async function measureSlots(page) {
  return page.evaluate(() => {
    const artboard = document.querySelector("#dmg");
    if (!artboard) throw new Error("#dmg not found");
    const artRect = artboard.getBoundingClientRect();
    const measureSlot = (id) => {
      const el = document.querySelector("#" + id);
      if (!el) throw new Error("#" + id + " not found");
      const r = el.getBoundingClientRect();
      const width = r.width;
      const height = r.height;
      const x = r.left - artRect.left;
      const y = r.top - artRect.top;
      return {
        id,
        x,
        y,
        width,
        height,
        centerX: x + width / 2,
        centerY: y + height / 2,
      };
    };
    return {
      artboard: {
        width: artboard.clientWidth,
        height: artboard.clientHeight,
      },
      slots: {
        app: measureSlot("slot-app"),
        applications: measureSlot("slot-applications"),
      },
    };
  });
}

function buildPositionsJson(measured) {
  const { artboard, slots } = measured;
  const app = slots.app;
  const applications = slots.applications;
  return {
    canvas: {
      width: round(artboard.width),
      height: round(artboard.height),
      background: "dist/dmg-background.png",
      background2x: "dist/dmg-background@2x.png",
    },
    // Include both the standard "Applications" name and the localized "Программы"
    // so create-dmg (Finder) receives the expected English link while we also
    // keep the localized name for our records and packaging logic.
    icons: {
      "FromChat.app": {
        x: round(app.centerX),
        y: round(app.centerY),
        width: round(app.width),
        height: round(app.height),
        slotId: "slot-app",
      },
      "Applications": {
        x: round(applications.centerX),
        y: round(applications.centerY),
        width: round(applications.width),
        height: round(applications.height),
        slotId: "slot-applications",
      },
      "Программы": {
        x: round(applications.centerX),
        y: round(applications.centerY),
        width: round(applications.width),
        height: round(applications.height),
        slotId: "slot-applications",
      },
    },
    createDmg: {
      windowSize: [round(artboard.width), round(artboard.height)],
      iconSize: Math.round(Math.min(app.width, app.height) * 0.86),
      icons: [
        ["FromChat.app", round(app.centerX), round(app.centerY)],
        ["Applications", round(applications.centerX), round(applications.centerY)],
        ["Программы", round(applications.centerX), round(applications.centerY)],
      ],
    },
  };
}

async function applyLabelsAndMeasure(page, config, measured) {
  // Set labels from config into DOM, measure their rendered widths and apply inline styles,
  // then position the label pills centered under measured slot centers.
  return page.evaluate(
    ({ cfg, measured }) => {
      document.documentElement.style.setProperty("--primary-pill", cfg.primaryColor || "#7e22ce");

      const setLabel = (selector, text) => {
        const el = document.querySelector(selector);
        if (!el) return null;
        el.textContent = text;
        return el;
      };

      const appLabel = setLabel('.slot__label[data-for="slot-app"]', cfg.appLabel || "FromChat");
      const appsLabel = setLabel('.slot__label[data-for="slot-applications"]', cfg.applicationsLabel || "Программы");

      const measureTextWidth = (el) => {
        if (!el) return 0;
        const span = document.createElement("span");
        span.style.visibility = "hidden";
        span.style.position = "absolute";
        span.style.whiteSpace = "nowrap";
        span.style.font = window.getComputedStyle(el).font;
        span.textContent = el.textContent;
        document.body.appendChild(span);
        const w = Math.ceil(span.getBoundingClientRect().width);
        document.body.removeChild(span);
        return w;
      };

      const horizPad = 18;
      const appW = measureTextWidth(appLabel);
      const appsW = measureTextWidth(appsLabel);

      if (appLabel) {
        appLabel.style.width = (appW + horizPad) + "px";
        appLabel.style.padding = "4px 9px";
      }
      if (appsLabel) {
        appsLabel.style.width = (appsW + horizPad) + "px";
        appsLabel.style.padding = "4px 9px";
      }

      // Position labels using translate-based offsets from 50% (keeps consistent centering and avoids
      // coordinate-space mismatches). Compute translateX so that the pill is centered at slot.centerX.
      const artboard = document.querySelector("#dmg");
      const artWidth = measured?.artboard?.width || artboard.clientWidth;
      const verticalOffset = 54; // px, matches previous visual placement

      const computeTranslateX = (slotCenterX, labelWidth) => {
        // desiredLeft = slotCenterX - labelWidth/2
        // with left:50% the origin is artWidth/2, so translateX = desiredLeft - artWidth/2
        return Math.round(slotCenterX - labelWidth / 2 - artWidth / 2);
      };

      if (appLabel && measured?.slots?.app) {
        const s = measured.slots.app;
        const tx = computeTranslateX(s.centerX, appW + horizPad);
        appLabel.style.left = "50%";
        appLabel.style.transform = `translate(${tx}px, ${verticalOffset}px)`;
      }
      if (appsLabel && measured?.slots?.applications) {
        const s = measured.slots.applications;
        const tx = computeTranslateX(s.centerX, appsW + horizPad);
        appsLabel.style.left = "50%";
        appsLabel.style.transform = `translate(${tx}px, ${verticalOffset}px)`;
      }

      const rectFor = (el) => {
        if (!el) return null;
        const r = el.getBoundingClientRect();
        const bg = getComputedStyle(el, "::before").backgroundColor;
        return { left: Math.round(r.left), top: Math.round(r.top), width: Math.round(r.width), height: Math.round(r.height), bg };
      };

      return {
        labels: {
          app: { text: appLabel?.textContent || "", width: appW + horizPad, rect: rectFor(appLabel) },
          applications: { text: appsLabel?.textContent || "", width: appsW + horizPad, rect: rectFor(appsLabel) },
        },
      };
    },
    { cfg: config, measured },
  );
}

async function screenshotArtboard(page, outPath, deviceScaleFactor) {
  const dmg = page.locator("#dmg");
  await dmg.waitFor({ state: "visible" });
  await page.evaluate(async () => {
    if (document.fonts?.ready) await document.fonts.ready;
  });
  await page.waitForTimeout(150);
  await dmg.screenshot({
    path: outPath,
    type: "png",
    omitBackground: false,
    scale: deviceScaleFactor === 2 ? "device" : "css",
  });
}

async function main() {
  await mkdir(DIST, { recursive: true });
  const { server, port } = await startStaticServer();
  const baseUrl = `http://127.0.0.1:${port}/`;
  let browser;
  try {
    browser = await chromium.launch({ headless: true });

    {
      const context = await browser.newContext({
        viewport: { width: 1280, height: 900 },
        deviceScaleFactor: 1,
      });
      const page = await context.newPage();
      await page.goto(baseUrl, { waitUntil: "networkidle" });
      await page.evaluate(async () => {
        if (document.fonts?.ready) await document.fonts.ready;
      });
      // load config
      let cfg = {};
      try {
        const cfgText = await readFileSync(CONFIG_PATH, "utf8");
        cfg = JSON.parse(cfgText);
      } catch (e) {
        cfg = {};
      }
      // measure slots first
      const measured = await measureSlots(page);
      // apply labels/pill widths and position them based on measured slot coordinates
      const labelMetrics = await applyLabelsAndMeasure(page, cfg, measured);
      console.log("label metrics:", JSON.stringify(labelMetrics));
      const positions = buildPositionsJson(measured);
      // attach label metrics to the output so build tasks can pre-calc sizes
      positions.labels = labelMetrics.labels;
      await writeFile(
        path.join(DIST, "icon-positions.json"),
        `${JSON.stringify(positions, null, 2)}\n`,
        "utf8",
      );
      await screenshotArtboard(page, path.join(DIST, "dmg-background.png"), 1);
      await context.close();
      console.log(`canvas ${positions.canvas.width}×${positions.canvas.height}`);
    }

    {
      const context = await browser.newContext({
        viewport: { width: 1280, height: 900 },
        deviceScaleFactor: 2,
      });
      const page = await context.newPage();
      await page.goto(baseUrl, { waitUntil: "networkidle" });
      await page.evaluate(async () => {
        if (document.fonts?.ready) await document.fonts.ready;
      });
      await screenshotArtboard(page, path.join(DIST, "dmg-background@2x.png"), 2);
      await context.close();
    }

    console.log(`wrote ${path.join(DIST, "dmg-background.png")}`);
    console.log(`wrote ${path.join(DIST, "dmg-background@2x.png")}`);
    console.log(`wrote ${path.join(DIST, "icon-positions.json")}`);
  } finally {
    if (browser) await browser.close();
    server.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
