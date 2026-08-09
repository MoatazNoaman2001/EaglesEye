#!/usr/bin/env node
// EaglesEye — raw device traffic capture listener (T-104)
//
// A deliberately dumb TCP logger: accepts connections on one port per protocol,
// logs every byte as hex + ASCII, and never replies. Devices will retry/buffer —
// that's fine; we only want their raw frames to build and test decoders against.
//
// Usage:
//   node capture.mjs 5027=teltonika 5023=gt06 5000=unknown
//   (any  port=label  pairs; defaults shown below if no args)
//
// Output:
//   console: live pretty hex dump
//   files:   captures/<label>-YYYY-MM-DD.jsonl  (one JSON line per event, replayable)

import net from "node:net";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const CAPTURE_DIR = path.join(ROOT, "captures");
fs.mkdirSync(CAPTURE_DIR, { recursive: true });

const DEFAULTS = ["5027=teltonika", "5023=gt06"];
const specs = (process.argv.length > 2 ? process.argv.slice(2) : DEFAULTS)
  .map(s => {
    const [port, label] = s.split("=");
    if (!port || !Number.isInteger(+port) || !label) {
      console.error(`bad argument "${s}" — expected port=label, e.g. 5027=teltonika`);
      process.exit(1);
    }
    return { port: +port, label };
  });

let connSeq = 0;

function today() { return new Date().toISOString().slice(0, 10); }

function logLine(label, obj) {
  const file = path.join(CAPTURE_DIR, `${label}-${today()}.jsonl`);
  fs.appendFileSync(file, JSON.stringify(obj) + "\n");
}

function hexDump(buf) {
  const hex = buf.toString("hex");
  const pairs = hex.match(/.{1,2}/g) ?? [];
  const ascii = [...buf].map(b => (b >= 32 && b < 127 ? String.fromCharCode(b) : ".")).join("");
  return { hex: pairs.join(" "), ascii };
}

for (const { port, label } of specs) {
  const server = net.createServer(socket => {
    const id = `${label}-${++connSeq}`;
    const peer = `${socket.remoteAddress}:${socket.remotePort}`;
    console.log(`\n[${new Date().toISOString()}] [${id}] CONNECT from ${peer}`);
    logLine(label, { ts: new Date().toISOString(), conn: id, event: "connect", peer });

    socket.on("data", buf => {
      const { hex, ascii } = hexDump(buf);
      console.log(`[${new Date().toISOString()}] [${id}] ${buf.length} bytes`);
      console.log(`  hex:   ${hex}`);
      console.log(`  ascii: ${ascii}`);
      logLine(label, { ts: new Date().toISOString(), conn: id, event: "data", bytes: buf.length, hex: buf.toString("hex") });
    });

    socket.on("close", () => {
      console.log(`[${new Date().toISOString()}] [${id}] DISCONNECT`);
      logLine(label, { ts: new Date().toISOString(), conn: id, event: "close" });
    });

    socket.on("error", err => {
      logLine(label, { ts: new Date().toISOString(), conn: id, event: "error", message: err.message });
    });
  });

  server.listen(port, "0.0.0.0", () => {
    console.log(`listening on ${port}  ->  ${label}  (captures/${label}-${today()}.jsonl)`);
  });
  server.on("error", err => {
    console.error(`FAILED to listen on ${port} (${label}): ${err.message}`);
  });
}

console.log("\nEaglesEye capture listener — point devices at this machine and watch the bytes.");
console.log("Ctrl+C to stop. Files are append-only JSON lines; safe to copy while running.\n");
