# EaglesEye — Dev Stack (T-106)

Everything the platform needs locally, one command.

## Start / stop

```powershell
cd infra\dev
docker compose up -d      # start everything
docker compose ps         # check status
docker compose down       # stop (data survives in volumes)
docker compose down -v    # stop AND wipe all data
```

## What runs where

| Service | Address | Credentials | Purpose |
|---|---|---|---|
| Kafka (KRaft) | `localhost:9092` | — | event backbone |
| TimescaleDB + PostGIS (PG16) | `localhost:5433` | `eagleseye` / `eagleseye-dev`, db `eagleseye` | positions, geofences, everything |
| Valkey | `localhost:6379` | — | live state, cooldowns |
| Keycloak (dev mode) | http://localhost:8090 | `admin` / `admin` | identity (T-403) |
| Mosquitto MQTT | `localhost:1883` | anonymous (dev only) | phone/OwnTracks + generic JSON devices |
| Traccar bridge (ADR-9) | web http://localhost:8083 · GT06 `5023` · Teltonika `5027` · OsmAnd `5055` | create admin on first visit | decodes device protocols, forwards to Kafka topics `traccar.positions` / `traccar.events` |

App ports (Quarkus dev mode): core **8080**, gateway **8081**, pipeline **8082**.

## Notes

- Credentials here are dev-only. Production secrets never live in git (NFR-08).
- Kafka auto-creates topics in dev; explicit topic provisioning with proper partitions/retention is T-201.
- Phone as device #1 (T-110): install OwnTracks, point it at `<your-PC-LAN-IP>:1883`, watch
  messages with: `docker exec ee-mosquitto mosquitto_sub -t 'owntracks/#' -v`
