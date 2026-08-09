# Troubleshooting — Docker engine will not start (Windows)

Recorded 06 Aug 2026 while setting up the dev stack (T-106), on the development machine
(Windows 11 Home 23H2, Intel i5-12450H, Docker Desktop 4.13.0).

## Symptom

`docker` commands hang or fail; Docker Desktop never reaches "running". The log
(`%LOCALAPPDATA%\Docker\log.txt`) ends with:

```
[Engines][Error] Start failed with Hardware assisted virtualization and
data execution protection must be enabled in the BIOS.
```

WSL reports:

```
Please enable the Virtual Machine Platform Windows feature and ensure
virtualization is enabled in the BIOS.
```

## The error message is misleading

Both messages point at the BIOS and at Windows features. On this machine **both were already correct**:

| Check | Command | Result |
|---|---|---|
| CPU virtualization in firmware | `Get-CimInstance Win32_Processor \| Select VirtualizationFirmwareEnabled` | `True` |
| Firmware virtualization (systeminfo) | `systeminfo \| findstr Virtualization` | `Enabled In Firmware: Yes` |
| Windows features | `Get-CimInstance Win32_OptionalFeature` | `VirtualMachinePlatform`, `Microsoft-Windows-Subsystem-Linux`, `HypervisorPlatform` all **ENABLED** |
| Hypervisor actually running | `Get-CimInstance Win32_ComputerSystem \| Select HypervisorPresent` | **`False`** ← the real problem |

The hypervisor is enabled everywhere but is **not being launched at boot**. That is controlled by
the boot setting `hypervisorlaunchtype`, which gets set to `off` by game anti-cheat software,
VirtualBox/VMware setups, some "performance tweak" guides, and certain Windows update paths.

A second, independent symptom on this machine: the `docker-desktop` WSL distro was missing
(`wsl -l -v` listed only `docker-desktop-data`), because the engine VM had never been able to start.
Docker Desktop recreates it automatically from `resources\wsl\wsl-bootstrap.tar` once the
hypervisor works.

## Fix

Run **PowerShell as Administrator** (Start → type "PowerShell" → Ctrl+Shift+Enter):

```powershell
bcdedit /set hypervisorlaunchtype auto
```

Then **restart Windows**. A reboot is mandatory — this is a boot-time setting.

After the reboot, verify:

```powershell
Get-CimInstance Win32_ComputerSystem | Select-Object HypervisorPresent   # must be True
wsl -l -v                                                                # docker-desktop should reappear
docker version                                                           # Server section must be present
```

Then start the stack: `docker compose -f infra/dev/docker-compose.yml up -d`

## If it still fails after the reboot

1. **Check Memory Integrity / Core Isolation** — Windows Security → Device Security → Core Isolation.
   Conflicts here can block the hypervisor; toggling it off and rebooting is a common fix.
2. **Docker Desktop 4.13 is from 2022.** If the engine still misbehaves, upgrade to the current
   Docker Desktop. Old versions have known incompatibilities with newer Windows builds, and the
   BIOS-centric error message above is itself a symptom of that vintage.
3. **Fallback that avoids Docker Desktop entirely:** install Docker Engine inside a WSL2 Ubuntu
   distro (`wsl --install -d Ubuntu`, then Docker's Linux install steps). WSL2 forwards ports to
   Windows `localhost`, so the dev stack and connection strings work unchanged. This also matches
   how the platform will run in production on Linux.

## Why this is worth recording

`docs/SolutionArchitecture-Phase1.md` assumes a container-based dev and deployment story
throughout. Losing an afternoon to a misleading BIOS error is exactly the kind of thing that
should cost the next person five minutes instead.
