// EaglesEye Hub — tiny local server (no dependencies)
// Serves the hub UI and persists board + journal to JSON files with automatic backups.
// Run: node server.mjs   (or double-click start-hub.cmd)
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readFile, writeFile, rename, mkdir, readdir, unlink } from "node:fs/promises";

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.join(ROOT, "data");
const BACKUP_DIR = path.join(DATA_DIR, "backups");
const PORT = 4600;
const MAX_BACKUPS = 20;   // kept per data file
const MAX_BODY = 5 * 1024 * 1024;

// name -> { file, key: required array property in PUT payload }
const STORES = {
  tasks:   { file: path.join(DATA_DIR, "tasks.json"),   key: "tasks" },
  journal: { file: path.join(DATA_DIR, "journal.json"), key: "entries" },
};

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
};

async function readStore(store, key) {
  try {
    return JSON.parse(await readFile(store.file, "utf8"));
  } catch {
    return { v: 1, [key]: null }; // client seeds on first run
  }
}

async function writeStore(name, store, payload) {
  await mkdir(BACKUP_DIR, { recursive: true });
  try {
    const current = await readFile(store.file, "utf8");
    const stamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
    await writeFile(path.join(BACKUP_DIR, `${name}-${stamp}.json`), current);
  } catch { /* first write — nothing to back up */ }
  const tmp = store.file + ".tmp";
  await writeFile(tmp, JSON.stringify(payload, null, 2));
  await rename(tmp, store.file);
  try {
    const files = (await readdir(BACKUP_DIR)).filter(f => f.startsWith(name + "-")).sort();
    while (files.length > MAX_BACKUPS) await unlink(path.join(BACKUP_DIR, files.shift()));
  } catch { /* non-fatal */ }
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on("data", c => {
      size += c.length;
      if (size > MAX_BODY) { reject(new Error("body too large")); req.destroy(); return; }
      chunks.push(c);
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  try {
    const apiMatch = url.pathname.match(/^\/api\/(\w+)$/);
    if (apiMatch) {
      const name = apiMatch[1];
      const store = STORES[name];
      if (!store) { res.writeHead(404).end(); return; }
      if (req.method === "GET") {
        res.writeHead(200, { "Content-Type": "application/json", "Cache-Control": "no-store" });
        res.end(JSON.stringify(await readStore(store, store.key)));
        return;
      }
      if (req.method === "PUT") {
        const body = JSON.parse(await readBody(req));
        if (!Array.isArray(body[store.key])) {
          res.writeHead(400, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: `payload must contain a '${store.key}' array` }));
          return;
        }
        await writeStore(name, store, { v: 1, saved: new Date().toISOString(), [store.key]: body[store.key] });
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ ok: true, count: body[store.key].length }));
        return;
      }
      res.writeHead(405).end();
      return;
    }

    // static files (path-traversal safe)
    let file = url.pathname === "/" ? "/index.html" : decodeURIComponent(url.pathname);
    const full = path.normalize(path.join(ROOT, file));
    if (!full.startsWith(ROOT)) { res.writeHead(403).end(); return; }
    try {
      const content = await readFile(full);
      res.writeHead(200, { "Content-Type": MIME[path.extname(full).toLowerCase()] || "application/octet-stream" });
      res.end(content);
    } catch {
      res.writeHead(404, { "Content-Type": "text/plain" });
      res.end("Not found");
    }
  } catch (err) {
    res.writeHead(500, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: String(err.message || err) }));
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log("");
  console.log("  EaglesEye Hub running");
  console.log(`    http://localhost:${PORT}`);
  console.log(`    data: ${DATA_DIR}`);
  console.log("");
  console.log("  Keep this window open while you work. Close it to stop the hub.");
});
