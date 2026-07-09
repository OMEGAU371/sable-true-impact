# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**True Impact (Rewrite)** — NeoForge 1.21.1 / Java 21 mod adding physics-based impact damage
on top of the Sable physics engine.
Mod ID: `true_impact` | Package root: `io.github.omegau371.trueimpact`
Current version: see `mod_version` in `gradle.properties` (this file goes stale if a version is hardcoded here).

> This is a from-scratch rewrite. The legacy codebase lives in
> `C:\Users\l\Desktop\Projects\TI\archived\pr-4-test` (read-only reference).
> Do NOT copy legacy implementations directly.

## Commands

```powershell
# Build (compiles + runs tests + produces JAR)
.\gradlew.bat build

# Tests only
.\gradlew.bat test
.\gradlew.bat test --rerun-tasks   # bypass test cache

# Single test class
.\gradlew.bat test --tests "io.github.omegau371.trueimpact.damage.Phase3ADamageTest"

# Compile only (force recompile when file timestamps are stale)
.\gradlew.bat compileJava
.\gradlew.bat compileJava --rerun-tasks

# Build + deploy to mods dirs + desktop
.\gradlew.bat deploy

# Dev server with RCON (Claude can send commands autonomously via Python RCON)
.\gradlew.bat copySableToRunMods runServer

# Automated game tests (runs @GameTest in real MC environment)
.\gradlew.bat runGameTestServer
```

## Dev server — autonomous RCON workflow

Claude can start, command, and test the dev server autonomously using RCON.

**Setup (already done):**
- `run/server.properties`: `enable-rcon=true`, `rcon.password=tidev`, `rcon.port=25575`
- `build.gradle` server run: `systemProperty 'neoforge.enabledGameTestNamespaces', "${project.mod_id},sable"`

**Start server in background:**
```powershell
Start-Process cmd.exe -ArgumentList "/c .\gradlew.bat --no-daemon runServer > server.log 2>&1" -WindowStyle Hidden
```
Then poll until RCON port 25575 is open before sending commands.

**Send RCON command (Python one-liner):**
```python
import socket, struct
def rcon(cmd):
    s=socket.socket(); s.settimeout(10); s.connect(('127.0.0.1',25575))
    def pk(r,t,d): b=struct.pack('<ii',r,t)+d.encode()+b'\x00\x00'; return struct.pack('<i',len(b))+b
    s.send(pk(1,3,'tidev')); s.recv(4096); s.send(pk(2,2,cmd)); r=s.recv(4096); s.close()
    return r[12:-2].decode('utf-8','replace') if len(r)>12 else ''
```

**Run GameTests:** `rcon('test runall')` — NOT `/gametest`, the command is `test runall` in MC 1.21.1.

**Stop server:** `rcon('stop')` then delete `run/world/session.lock` if needed.

**Critical gotchas:**
- **Never leave `true_impact-*.jar` in `run/mods/`** — NeoForge loads it instead of `build/classes/`,
  causing the dev build to silently run stale bytecode. `copySableToRunMods` only copies Sable;
  our mod is loaded from sourceSets. If a JAR appears there (e.g. after a bad deploy), delete it.
- **Gradle file-timestamp skew on Windows**: the Write/Edit tools may not update mtime, causing
  `compileJava` to skip recompile even after source changes. Run `compileJava --rerun-tasks`
  when server behavior doesn't reflect code changes after a restart.
- **`localhost` → IPv6 on Windows**: always use `127.0.0.1` for RCON, not `localhost`.

JAR is produced at `build/libs/true_impact-<mod_version>.jar`.

`TrueImpactVersion.java` is generated from `src/main/templates-java/` — do NOT edit the
generated file. Edit the template or `gradle.properties` instead.

## Deploy workflow

After each build that should be tested in-game:

1. Bump `mod_version` in `gradle.properties`
2. Run `.\gradlew.bat deploy`

Deploy targets (removes old rewrite2 JARs, copies the new one):
- `D:\.minecraft\versions\1.21.1-NeoForge_21.1.228\mods`
- `C:\Users\l\Desktop`

The deploy task identifies "its own" JARs by `TI-Project-Line=rewrite2` in `MANIFEST.MF`.
Legacy v1.x JARs are never touched. `DeployFilterTest.java` covers the filter logic.

## Damage pipeline — three parallel paths

All three paths converge at `ServerTickEvent.Post` in `TrueImpactMod`.

### Path 1 — World block damage
`SableImpactCapture.enqueueFromRegistry()` →  `DeferredDamageQueue`
→ `onServerTickPost` → stale-entry guard → confinement scaling → `BlockDamageAccumulator`
→ crack overlay (`destroyBlockProgress`) → `Block.dropResources()` + `level.destroyBlock(pos, false)`

Key helpers: `BlockHardnessProfile` (vanilla-data-driven thresholds), `ConfinementFactor`
(D-3: surrounding block pressure scales break threshold), `CrackOverlayTracker` (rate-limited
fake-breaker-id crack packets), `MaterialResponsePlanner` (dedup via `markBreakScheduled`).

### Path 2 — Physics structure (Sublevel) block damage — Phase 3A
`SableImpactCapture.enqueueEmbeddedContact()` → `DeferredSublevelDamageQueue`
→ `applyDeferredSublevelDamage()` → match `ServerSubLevel` by `runtimeId`
→ `SublevelDamageAccumulator` → crack overlay at absPos → `accessor.getLevel()` +
`Block.dropResources()` + `accessor.destroyBlock(localPos, false, null, 512)`

Contact point (`cpX/Y/Z`) is in **body-COM-local space**, not world space.
World pos: `absPos = plot.getCenterBlock() + faceAwareRound(localXYZ)`.
`Phase3ADamage.faceAwareRound()` maps sub-block offsets (±0.5) to the correct block index.

### Path 3 — Dynamic structure (Create contraption) anchor damage — Phase 4A/4B
Piggybacks the Phase 3A loop: `SableImpactCapture.enqueueContraptionContact()`
→ `DeferredContraptionDamageQueue` → `applyDeferredContraptionDamage()`
→ AABB search for `KinematicContraption` entities near absPos
→ reflection (`findField` walks superclass chain) to get `contraption.anchor` (`BlockPos`)
→ type dispatch on the found entity:
  - bearing/piston/etc. → anchor accumulates damage (`ANCHOR_DAMAGE` map, same crack-overlay
    pattern as world blocks) against a threshold computed from `(Σ contraption block breakThresholdJ
    + anchor breakThresholdJ) × CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER`; `destroyBlock(anchor, false)`
    once ratio ≥ 1.0
  - `CarriageContraptionEntity` → `tryDerailTrain()`, track block accumulates damage, sets
    `Carriage.derailed = true` via reflection once threshold cleared
  - `OrientedContraptionEntity` (minecart) → `tryKillMinecartVehicle()`, kills the riding entity
    once `kImpact ≥ MINECART_KILL_THRESHOLD_J`

### Path 4 — Active-vs-active (sublevel collisions)
`SableImpactCapture.process()` pairMap → `ImpactRecord` → `DamageResolver.resolve()` (stub, returns NONE)

## Drop system ("因力而掘")

`TrueImpactMod.resolveDropTool(kImpact)` returns `ItemStack` or `null`:
- `DISABLED` → null (no drops)
- `ALL` → netherite pickaxe always
- `BY_FORCE` → tier from `[高级.掘取]` thresholds; above wooden limit → null (no drops)

Always call `Block.dropResources(state, level, pos, blockEntity, null, toolStack)` BEFORE
`destroyBlock(pos, false)`. Never use `destroyBlock(pos, true)` (ignores tool tier).
For sublevel blocks: `level = accessor.getLevel()` (returns host `ServerLevel`), `pos = absPos`.

## Key Sable integration facts

- Sable `2.0.x` (sable-neoforge-1.21.1-2.0.1.jar in `libs/` and `run/mods/`)
- All Sable API calls are guarded by `DistInfo.isSableLoaded()` — mod loads fine without Sable
- `EmbeddedPlotLevelAccessor.getLevel()` returns the **host** `ServerLevel` (not the embedded sublevel level)
- `Contraption.anchor` is a public `BlockPos` field, but mixin-injected into `AbstractContraptionEntity` —
  access via reflection with `findField()` which walks the superclass chain
- Sublevel-vs-contraption contacts appear as "world contacts" in narrow-phase (`sawWorldContact=true`,
  one unknown body); the Phase 3A loop double-enqueues both `enqueueEmbeddedContact` AND
  `enqueueContraptionContact` for the same event
- Contact point coordinates from Rapier are in the **sublevel's body-COM-local frame**, not world space
- `SableImpactCapture.process()` runs **unconditionally** every physics tick — diagnostic flags
  control log output only, not pipeline execution (this was a prior bug root cause)

## Config system

NeoForge SERVER config is stored GLOBALLY at `config/true_impact-server.toml` (NeoForge 21.x
moved it out of the world folder; `saves/<world>/serverconfig/` only holds per-world OVERRIDES).
In-game editing goes through NeoForge's built-in ConfigurationScreen, registered by
`client/TrueImpactConfigScreenHook` — without it, Catnip/Ponder's generic screen takes over
and crashes with a NightConfig WritingException when saving a server config during world unload.

| Section | Key fields |
|---|---|
| `[总体]` | 方块破坏, 裂纹积累 (ENABLE_DAMAGE_ACCUMULATION), 世界方块受损, 物理结构受损, 互相碰撞受损, 掉落模式 (DropMode enum) |
| `[高级.材质]` | per-material break multipliers (5 tiers) |
| `[高级.贯穿动力学]` | 启用贯穿动力学, 贯穿触发能量, 贯穿再注入速度上限, 贯穿最低触发速度, 贯穿能量损耗系数, 贯穿足印半径 |
| `[高级.平衡]` | 伤害预设 (DamagePreset enum: MILD/CONSERVATIVE/DEFAULT/INTENSE/DRAMATIC), 最低检测阈值 |
| `[高级.受伤倍率]` | 总受伤倍率, 物理结构受伤倍率, 世界方块受伤倍率, 物理结构弹性下限/损伤半衰期/冲击速度上限 |
| `[高级.兼容性.Create]` | 对动态结构生效, 锚点强度倍率, 列车脱轨阈值, 矿车摧毁阈值 |
| `[高级.裂纹]` | 裂纹更新间隔 |
| `[高级.掘取]` | Tool-tier thresholds: 下界合金镐(100J) → 钻石镐(300J) → 铁镐(800J) → 石镐(2000J) → 木镐(5000J) → no drops |

All `TrueImpactConfig.*` calls in hot paths must be wrapped in `try/catch` — unit tests run
without a Minecraft runtime and config is never loaded.

Config is read in `onConfigLoad`/`onConfigReload` then written into `ImpactRuntimeConfig`
static fields for use in tight loops (avoids repeated `.get()` overhead).

## ArchUnit rules — enforced at build time

`src/test/java/.../arch/FoundationArchTest.java` fails the build on violation:

- `physics/` must NOT depend on any other TI package (pure data contract)
- `damage/` must NOT depend on `diagnostic/` or `observation/`
- `observation/` and `diagnostic/` must NOT depend on `damage/`
- `SableImpactCapture` must NOT depend on `DiagnosticConfig` (capture runs unconditionally)
- No `net.minecraft.client.*` in `command/`, `platform/`, `physics/`, `damage/`, `observation/`, `diagnostic/`, root

Add a new `@ArchTest` before writing code that would otherwise violate a new rule.

## Key gotchas

- **Plot-embedded coords are ~4×10⁷ from origin**: Always guard with `level.hasChunkAt(pos)` before
  any world access on embedded coords. Never call `level.getBlockState(pos)` without this guard —
  forces vanilla chunkgen at that offset, causing aquifer AIOOBE on the worker thread.
- **Dedicated server class loading**: `RuntimeDistCleaner` aggressively rejects classes referencing
  client-only types. When reflecting over Sable/Create classes catch `Throwable` (not
  `ReflectiveOperationException`) — `ExceptionInInitializerError` is common.
- **`presetMode = "auto"` silently resets Sable config**: Set `presetMode = custom` before any
  toggle-based diagnostic, or the preset re-applies and invalidates the test.
- **`localhost` → IPv6 on Windows**: Connect to dev server via `127.0.0.1`, not `localhost`.
- **Drop before destroy**: Always `Block.dropResources(...)` before `destroyBlock(pos, false)`.
  Never use `destroyBlock(pos, true)` — it uses an empty tool and ignores tool tier.
- **`SableImpactCapture.process()` is unconditional**: DiagnosticConfig flags affect only log
  output. Re-adding a capture gate to this method re-introduces the prior production bug.

## Version scheme

`MAJOR.MINOR.PATCH-PHASE`
`0.x.x` = rewrite line | `1.x.x` = legacy (archived)
Phase markers: `foundation`, `sable-study`, `phase1`, `phase2`, `phase3`, `config`, `mining`, `dynstruct`, `alpha`, `beta`
Always bump `mod_version` in `gradle.properties` before deploying.
