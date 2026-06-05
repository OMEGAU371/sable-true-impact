# Architecture — True Impact (Rewrite)

> Phase 0: Foundation. This document grows with each phase gate.

## Layer model

```
┌─────────────────────────────────────────────────────────────────┐
│  NeoForge event bus / commands / game thread                    │
│                       TrueImpactMod (@Mod)                      │
│                      ↓               ↓                          │
│                  command/          platform/                     │
│               StatusCommand       DistInfo                       │
│                      ↓                                           │
│              net.minecraft.commands.*  (NeoForge API)           │
│              net.neoforged.fml.ModList (NeoForge API)           │
└─────────────────────────────────────────────────────────────────┘
```

Dependency direction: `TrueImpactMod → command → platform`.  
`platform` is the lowest layer and must never import from `command` or higher.

## Package responsibilities

| Package | Responsibility |
|---|---|
| `trueimpact` (root) | `@Mod` entry point, bus wiring |
| `platform` | Static environment queries — dist, mod presence, version |
| `command` | Brigadier command registration and execution |
| `mixin` *(future)* | Sable engine adapters; must not import from `damage/` |
| `physics` *(future)* | ImpactEvent model, energy inputs |
| `damage` *(future)* | Reducer → Resolver → Accumulator pipeline |
| `compat` *(future)* | Create, DistantHorizons, etc. thin adapters |

## Client/server safety rules

1. No class in `platform`, `command`, `physics`, or `damage` may import `net.minecraft.client.*`.
2. Client-specific code (HUD, rendering, keybindings) lives in dedicated classes annotated `@OnlyIn(Dist.CLIENT)` and loaded only via `DistExecutor` or NeoForge dist-specific event handlers.
3. Sable reflection wrappers must catch `Throwable` (see CLAUDE.md gotchas).

## Phase 0 deliverables (this document's current scope)

- [x] Project scaffold (NeoForge 1.21.1, Java 21, Gradle 9.2.1)
- [x] Package structure with ArchUnit layer guards
- [x] `/trueimpact status` command
- [x] Unit test + arch test baseline
- [x] Deploy task

## Phase 1 additions (planned, not yet implemented)

- `ImpactEvent` record
- `ImpactReducer` (per-tick aggregation)
- `DamageResolver` skeleton (gate only, no actual destruction)
- Sable event hook (read-only observation)

See `docs/rewrite-roadmap.md` for the full roadmap.
