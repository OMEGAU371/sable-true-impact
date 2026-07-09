# True Impact

**Physics-based structural damage for [Sable](https://github.com/ryanhcode/sable) — damage thresholds derived from real block data, mutual structure-vs-structure damage, and energy-driven terrain penetration.**

NeoForge 1.21.1 · Java 21 · Mod ID: `true_impact` · Current: `0.4.0-delta`

💬 **QQ 交流群 / QQ Group:`775113798`** — bugs, feedback, discussion welcome / 欢迎反馈问题、交流想法

---

<details open>
<summary><b>🇬🇧 English</b> (click to collapse)</summary>

## Features

### Overview

True Impact adds physics-based structural damage to Sable-simulated bodies and the surrounding
world. Where most impact-damage mods work off a fixed table of block-to-damage values, this mod
derives its break and crack thresholds directly from each block's vanilla hardness and blast
resistance — the same numbers Minecraft itself already uses for mining and explosions. The
result is a damage model that stays consistent with vanilla balance instead of introducing a
second, disconnected set of numbers to tune.

### World and structure damage

Every real physics contact — whether a falling structure hitting the ground or a swung object
grazing a wall — accumulates damage on the specific blocks it touches, weighted by how much
force that contact actually carried. A block's effective resistance also depends on what
surrounds it: material embedded deep inside a larger structure is measurably harder to break
than the same material sitting exposed on the surface. Light, repeated contact below a
configurable fatigue threshold causes no damage at all, and accumulated damage decays over time
if a structure isn't hit again — so casual handling doesn't slowly wear a build down to nothing.

### Structural mechanics

When a block is destroyed, the resulting stress can propagate through the structure and split it
into independent pieces along the break. Impacts carrying enough energy don't stop at the first
layer of material: the remaining kinetic energy is re-injected as velocity, letting a sufficiently
heavy or fast object continue through several layers of terrain in a single pass, governed by a
speed threshold rather than raw energy so that objects merely settling under gravity are never
mistaken for active impacts. Two physics structures colliding damage each other simultaneously,
with the outcome determined by their relative velocity and each side's material hardness — a
softer object cannot damage a harder one regardless of how it's swung.

### Create integration

Anchor blocks that hold a Create contraption together — mechanical bearings, pistons, pulleys,
and similar components — accumulate damage the same way world blocks do, and the contraption
comes apart once that damage exceeds the anchor's structural threshold. Trains derail and
occupied minecart contraptions are destroyed outright on sufficiently high-speed collisions.

## Requirements

| Dependency | Version | Required |
|---|---|---|
| Minecraft | 1.21.1 | Yes |
| NeoForge | 21.1.228+ | Yes |
| Sable | 2.0.1 | Yes (mod loads without it, but does nothing) |
| Create | 6.0.10+ | Optional (enables contraption anchor damage) |

## Commands

```
/trueimpact status
```
Prints mod version, Minecraft/NeoForge version, Sable detection status, and runtime environment.

```
/trueimpact damage inspect last
/trueimpact damage inspect here
/trueimpact damage clear
/trueimpact damage breaking on|off
```
Inspect accumulated damage state at the last hit position or the player's current position;
clear all accumulated damage; toggle whether damage actually destroys blocks (crack overlay and
accumulation still run either way).

```
/trueimpact debug status
/trueimpact debug contacts on|off
/trueimpact debug callbacks on|off
/trueimpact debug bodies on|off
/trueimpact debug all off
```
Diagnostic logging toggles — see the debug section of the config for the full per-path logging
matrix. All are opt-in and independent of whether damage effects apply.

## Configuration

Server config lives at `config/true_impact-server.toml` (global, not per-world — NeoForge 21.x
moved it out of the world folder). Edit in-game via NeoForge's built-in config screen. Covers:
master toggles + drop mode, per-material strength multipliers, penetration dynamics tuning,
structure-vs-structure toggle, damage presets (mild → dramatic), global damage multipliers,
Create integration toggles, crack overlay update rate, and tool-tier drop thresholds.

## Building

```powershell
.\gradlew.bat build            # compile + test + jar + sources jar
.\gradlew.bat test              # unit tests + ArchUnit
.\gradlew.bat deploy            # build + deploy to mods dirs + desktop
.\gradlew.bat copySableToRunMods runServer   # dev server with RCON
.\gradlew.bat runGameTestServer              # automated @GameTest suite
```

JAR produced at `build/libs/true_impact-<version>.jar`.

## Version scheme

`MAJOR.MINOR.PATCH-PHASE` — `0.x.x` is this rewrite line; `1.x.x` is the legacy line (archived,
read-only reference on the `gamma-legacy` branch). The version is defined once in
`gradle.properties` (`mod_version`); `TrueImpactVersion.java` is auto-generated from it.

## Architecture

See `CLAUDE.md` for the full damage-pipeline breakdown, Sable integration gotchas, and the
ArchUnit-enforced package boundaries. See `docs/architecture.md`, `docs/coordinate-systems.md`,
and `docs/physics-invariants.md` for the underlying design docs.

## Disclaimer

Except for art assets, part of this project's code is AI-assisted. All changes are manually
reviewed and released only after multiple rounds of testing.

## License

[LGPL-3.0-only](https://www.gnu.org/licenses/lgpl-3.0.html)

</details>

<details>
<summary><b>🇨🇳 中文</b>(点击展开)</summary>

## 功能

### 总览

True Impact 给 Sable 模拟的物理结构和周围世界加入了基于物理的结构性伤害。多数撞击伤害类
模组依赖一张"方块对应伤害值"的固定数值表,这个模组不同——破坏与裂纹阈值直接从每个方块的
原版硬度和爆炸抗性推导而来,跟 Minecraft 本身用于挖掘和爆炸判定的是同一套数字。结果是一套
与原版平衡保持一致的伤害模型,而不是另起一套脱节的数值需要单独调校。

### 世界方块与结构伤害

每一次真实的物理接触——不管是坠落的结构砸在地上,还是挥动的物体擦过一堵墙——都会按接触
实际承载的力度,在它触碰到的具体方块上累积伤害。方块的实际抗性还取决于周围有什么在支撑它:
埋在大型结构内部的材料,明显比裸露在表面的同种材料更难打碎。低于可配置疲劳阈值的轻微反复
接触完全不造成伤害,累积的伤害如果不再被命中还会随时间衰减——所以日常操作不会把一个建筑
慢慢磨没。

### 结构力学

方块被摧毁时,产生的应力可能沿结构传播,并沿断裂处把结构拆成独立的几块。能量足够的撞击不会
停在第一层材料——剩余动能会重新注入为速度,让足够重或足够快的物体一次贯穿好几层地形,这个
判定用的是速度阈值而不是绝对能量,确保单纯在重力下沉降的物体不会被误判为主动撞击。两个物理
结构相撞时双方会同时受伤,结果由相对速度和各自接触面的材质硬度决定——较软的一方无论怎么撞
都伤不了更硬的一方。

### Create 联动

把 Create 契约装置固定在一起的锚点方块——机械轴承、活塞、滑轮及同类部件——会像世界方块
一样累积伤害,伤害超过锚点的结构阈值后契约就会散架。足够高速的撞击会让火车直接脱轨、载人的
矿车契约被当场摧毁。

## 依赖

| 依赖 | 版本 | 是否必须 |
|---|---|---|
| Minecraft | 1.21.1 | 是 |
| NeoForge | 21.1.228+ | 是 |
| Sable | 2.0.1 | 是(没装也能进游戏,但不会有任何效果) |
| Create | 6.0.10+ | 可选(启用契约装置锚点伤害) |

## 命令

```
/trueimpact status
```
打印模组版本、Minecraft/NeoForge 版本、Sable 检测状态与运行环境。

```
/trueimpact damage inspect last
/trueimpact damage inspect here
/trueimpact damage clear
/trueimpact damage breaking on|off
```
查看上次命中位置或玩家当前位置的累积伤害状态;清空全部累积伤害;开关"伤害是否真的摧毁方块"
(裂纹贴图和累积逻辑无论开关与否都会照常运行)。

```
/trueimpact debug status
/trueimpact debug contacts on|off
/trueimpact debug callbacks on|off
/trueimpact debug bodies on|off
/trueimpact debug all off
```
诊断日志开关——配置文件"调试"分区里有完整的按路径分类日志矩阵。全部默认关闭,且与伤害效果
本身是否生效相互独立。

## 配置

服务端配置在 `config/true_impact-server.toml`(全局配置,不是按存档存放——NeoForge 21.x 把它
移出了存档目录)。游戏内可以通过 NeoForge 自带的配置界面编辑。涵盖:总开关与掉落模式、按材质
分类的强度倍率、贯穿动力学参数、结构互伤开关、伤害预设(温和到夸张)、全局伤害倍率、Create
兼容开关、裂纹更新频率、工具等级掉落阈值。

## 构建

```powershell
.\gradlew.bat build            # 编译 + 测试 + 打包 + 生成 sources jar
.\gradlew.bat test              # 单元测试 + ArchUnit 架构检查
.\gradlew.bat deploy            # 构建并部署到 mods 目录和桌面
.\gradlew.bat copySableToRunMods runServer   # 带 RCON 的开发服务器
.\gradlew.bat runGameTestServer              # 自动化 @GameTest 套件
```

JAR 产出在 `build/libs/true_impact-<version>.jar`。

## 版本号规则

`MAJOR.MINOR.PATCH-PHASE`——`0.x.x` 是本重写线,`1.x.x` 是 legacy 旧线(已归档,只读参考,在
`gamma-legacy` 分支)。版本号只在 `gradle.properties` 的 `mod_version` 里定义一处,
`TrueImpactVersion.java` 是从它自动生成的。

## 架构

完整的伤害管线拆解、Sable 集成的各种坑、ArchUnit 强制的包依赖边界都写在 `CLAUDE.md` 里。
底层设计文档见 `docs/architecture.md`、`docs/coordinate-systems.md`、
`docs/physics-invariants.md`。

## 免责声明

本项目除艺术资源外,项目代码其中部分由 AI 辅助生成,所有改动均由人工审核并经过多轮测试后
发布。

## 许可协议

[LGPL-3.0-only](https://www.gnu.org/licenses/lgpl-3.0.html)

</details>
