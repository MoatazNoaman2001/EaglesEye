# Capture Listener (T-104)

A deliberately dumb TCP logger for capturing raw GPS device traffic. It never replies —
devices retry and buffer, which is exactly what we want: their raw frames, untouched,
to build and regression-test decoders against (T-209).

## Run

```powershell
cd tools\capture-listener
node capture.mjs                          # defaults: 5027=teltonika, 5023=gt06
node capture.mjs 5027=teltonika 5023=gt06 5100=queclink   # any port=label pairs
```

## Point a device at it

1. Find this PC's LAN IP (`ipconfig` — the WiFi/Ethernet one, e.g. 192.168.100.5).
2. Configure the device's server setting to `<that-IP>:<port>` (Teltonika → 5027, GT06 family → 5023).
3. If nothing arrives, open the Windows Firewall for the port:
   `New-NetFirewallRule -DisplayName "EE capture 5027" -Direction Inbound -Protocol TCP -LocalPort 5027 -Action Allow`

## Output

- Console: live hex + ASCII dump per frame.
- `captures/<label>-<date>.jsonl` — one JSON line per event (`connect` / `data` / `close`),
  with full hex payloads. Append-only, safe to copy while running. These files are the
  input for decoder unit-test fixtures and the M0 exit evidence ("streams captured and documented").
