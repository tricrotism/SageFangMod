# SageFang dev guide

SageFang (`sagefang`) is a **client-side** Fabric mod for Minecraft. Targets the
non-obfuscated 26.1 toolchain (official Mojang names, Java 25). Almost everything
runs on the client; there is no server logic. UI is ImGui; chat/text is Adventure
MiniMessage; commands are client commands under `/sagefang`.

## Working practices

Bias toward caution over speed. Use judgment on trivial tasks.

**Think before coding.** State assumptions. If multiple interpretations exist, present them, don't pick silently. If
something is unclear, stop and ask.

**Simplicity first.** Minimum code that solves the problem. No features, abstractions, configurability, or error
handling beyond what's asked. If you wrote 200 lines and it could be 50, rewrite.

**Surgical changes.** Touch only what you must. Don't refactor adjacent code or "improve" formatting. Match existing
style. Notice unrelated dead code? Mention it, don't delete. Remove imports/variables only if *your* change made them
unused.

**Light logic first.** Reach for the lightest tool: a one-line guard over a helper, a helper over a class, a typed local
over a new field, an existing util over rolling your own, a direct `if` chain over a strategy/registry until 3+ branches
truly vary independently. When unsure between two approaches, write the lighter one first; escalate only when it
provably fails.

**Update deprecated/renamed usages when you touch a file.** 26.1 renamed a lot: `ResourceLocation` → `Identifier`,
`PayloadTypeRegistry.playS2C()` → `clientboundPlay()`, `Camera.setup()` → `alignWithEntity()`, `Chat.addMessage()` →
`addClientSystemMessage()`, mixin compat `JAVA_21` → `JAVA_25`, accesswidener `v1 named` → `v2 official`. Fix these on
lines you'd already be reading or changing. Don't open a file purely to chase them. If a migration is non-trivial (
signature change, callsite cascade, mixin target moved), ask first.

One gotcha worth knowing: the rendered world FOV is the `Camera.fov` field, filled each frame by
`Camera.calculateFov(F)`, so hook that for zoom/FOV mixins. `Camera.getFov()` only feeds horizon projection, so a
mixin on it changes nothing visible.

**Wire a new module/feature everywhere it must register.** A `Module` is invisible until it's hooked into *all* the
registration points. When adding one, mirror an existing peer (e.g. `Blink`, `GhostBlock`) across every site:

- `EVENT_BUS.subscribe(X.instance)` in `SageFang.onInitialize` (only if it has `@EventHandler` methods),
- `event.registerAll(...)` in `SageFang.onMenuRegistration` (if it implements `Menu`),
- the `COMMANDS[]` array in `SFCommandManager` (if it adds a command),
- `sagefang.mixins.json` (if it ships a mixin).

A module that subscribes to events but isn't added to the menu registry (or vice-versa) is a bug, not a half-feature.
Grep for an existing module's name to find every site before you call it done.

---

## Code quality

Apply ordinary code-smell scrutiny when writing or reviewing:

- Null-safety on **external/Minecraft** returns: `mc.player`, `mc.level`, `mc.getConnection()`, `mc.screen`, registry
  `get(...)` (returns `Optional`), config reads. Guard them; internal framework state can be trusted.
- Resource leaks: event listeners that outlive their module, `Timer`/scheduled tasks left running, network receivers not
  cleaned up, ImGui state never reset. Modules toggle via `onActivate`/`onDeactivate`, so make sure deactivate undoes
  what activate did.
- Mutable shared state without thread-safety (see Thread safety below). The render thread, the network thread and
  background timers all touch the same statics.
- Per-frame waste in ImGui `frame(ImGuiIO)`, which runs every frame. Don't allocate, parse, format, or do registry
  lookups
  in the hot path; hoist them.
- Broad `catch (Exception e)` that swallows context. Rethrow, log with context (`SageFang.LOGGER` / SLF4J), or scope the
  catch.
- Misleading identifiers (`get…` that mutates, `active` vs `visible` confusion, since they mean different things here).
- Mixin correctness: wrong `@At` target, injecting into the wrong overload, `@ModifyArgs`/`@Inject` that silently
  no-ops because the descriptor uses an outdated name.
- Dead branches and unreachable returns introduced by your own changes.

These are intentional in this codebase, so don't flag them: Lombok `@Getter`/`@Setter` field access, the
`public static final X instance` singleton pattern on modules, `public` mutable static fields on `SageFang` (
`lastServerInfo`, `delayedUIPackets`), reflective `@EventHandler` methods that look unused, wildcard imports in a few
files, hardcoded dependency versions, `TODO` comments.

---

## Commit messages

Match the repo's lowercase, informal style. Title is a short imperative line; prefix with a tag when it helps scanning (
`deps:`, `render:`, `commands:`, `macros:`). Dependency bumps follow the existing `[DepUpdate](deps): ...` form.

For non-trivial changes, blank line then a one or two paragraph body:

1. **Problem.** Be concrete: name the class/mixin/module, the symptom, the real impact ("runClient crashed in
   `FabricClientCommandManager.<init>`", "FOV hook no longer fired after `setup()` was renamed").
2. **Fix.** The mechanism and any key invariant or preserved behavior.

Trivial commits (dep bumps, cosmetics, reverts) can stay title-only.

**Never add `Co-Authored-By:` trailers** (Claude, Anthropic, AI tooling, anything) or emoji to commit messages, even if
the workflow seems to imply it.

```
port to non-obfuscated Minecraft 26.1.2

Migrate to net.fabricmc.fabric-loom (no remapping), bump deps to their
26.1 builds, and apply the API renames (Identifier, clientboundPlay,
Camera.alignWithEntity, addClientSystemMessage). fabric.mod.json now
depends on the renamed `fabric-api` mod id, fixing the "requires any
version of fabric, which is missing!" launch failure.
```

---

## Commands: Cloud builder API under `/sagefang`

All commands are **client** commands registered through Cloud's `FabricClientCommandManager` in
`SFCommandManager.init()`. This codebase uses the **builder API**, not Cloud annotations.

- A command class implements `SFCommand` and its `register(manager, root)` builds subcommands off the shared `/sagefang`
  root.
- Register it by adding an instance to the `COMMANDS[]` array in `SFCommandManager`. Nothing else.
- Parsers come from `org.incendo.cloud.parser.standard.*` (e.g. `greedyStringParser()`); send output with
  `ctx.sender().sendFeedback(component)`.
- Render Adventure components to native via `MinecraftClientAudiences.of().asNative(component)` before handing them to
  vanilla `Component` APIs.

---

## Messages: MiniMessage + MessageUtils

**Never use `§` legacy color codes or string concatenation/interpolation in player-facing text.**

Prefixed chat (the `SFM »` line) goes through `MessageUtils`:

```java
MessageUtils.sendMessage(mc, Component.literal("Saved."));
```

Rich/colored text uses the project's cached `MiniMessage` wrapper (`com.tricrotism.api.text.MiniMessage`), which
registers the full `ColorPalette` as named tags:

```java
Component c = MiniMessage.format("<off_white>You are now in <highlight>Ranged</highlight> mode.");
```

For dynamic content use a `TagResolver`/`Placeholder`. Never build the string by hand:

```java
Component c = MiniMessage.format(
        "<off_white>Caught <highlight><type></highlight>, earned <highlight><amount></highlight>.",
        Placeholder.unparsed("type", type.name()),
        Placeholder.unparsed("amount", String.valueOf(amount))
);
```

`MiniMessage.format(String)` is cached (15s, for hot repeats); the var-args overload is not, so prefer it only when you
have dynamic resolvers.

**Color tags** are defined in `ColorPalette` and registered in `MiniMessage`. Prefer the named tags over raw
`<#RRGGBB>`: whites/grays (`<off_white>` `<ivory>` `<silver>` `<slate_gray>` `<dark_gray>`), reds/oranges (`<scarlet>`
`<crimson>` `<orange>` `<gold>` `<light_red>`), blues/purples (`<highlight>`=highlight_blue, `<ios_blue>` `<blurple>`
`<dark_blue>` `<light_purple>`), greens (`<emerald>` `<light_green>`), plus semantic `<success>` `<warning>` `<error>`
`<info>` `<neutral>`. Standard MiniMessage tags (`<bold>`, `<reset>`, etc.) also work.

---

## Modules

A feature is usually a `Module` subclass (`com.tricrotism.api.modules.Module`), exposed as a
`public static final X instance` singleton.

- Modules **self-register** into `Module.REGISTRY` on construction, and the settings UI discovers them from there. Don't
  maintain a parallel hardcoded list for the menu.
- State is config-backed: `active` (`module.<id>.enabled`) and `visible` (`module.<id>.visible`). Toggle through
  `toggle()` / `toggleVisible()`, which persist automatically. Don't set those keys by hand.
- Put activation side-effects in `onActivate()` and make `onDeactivate()` fully undo them.
- `baseConfig` (`"module." + id`) is the prefix for any extra per-module keys.

UI for a module implements the `Menu` interface (`void frame(ImGuiIO io)`) and is added to
`SageFang.onMenuRegistration`.

---

## Events: custom EventBus

SageFang has its own `EventBus` (`SageFang.EVENT_BUS`), not Fabric's callback API, for internal cross-cutting events.

- Mark handlers with `@EventHandler` (optional `priority = EventPriority.HIGH`, etc.). They're invoked reflectively, so
  an `@EventHandler` method that looks unused is not dead code.
- Subscribe the singleton in `SageFang.onInitialize`: `EVENT_BUS.subscribe(MyModule.instance)`.
- Cancellable events implement `ICancellable`.
- For genuine *game* hooks (networking, world events, key bindings) use the appropriate Fabric API / a mixin. The
  custom bus is for SageFang's own events.

---

## Config: avaje-config

`io.avaje.config.Config` is the config layer; `ConfigPersistence` writes changes back to `config/sagefang.properties` (
debounced, 5s flush + shutdown hook).

```java
boolean on = Config.getBool("module." + id + ".enabled", false);
Config.

setProperty("module."+id +".enabled", "true");
```

- Read with `Config.getBool/getInt/get(...)` and **always pass a default**.
- Write with `Config.setProperty(...)`; the persistence layer handles the file. Never write `sagefang.properties`
  yourself.
- `Config.onChange(...)` is already wired for the dirty flag, so don't add competing writers.

---

## Mixins

Mixins live under `com.tricrotism.mixin.impl.*` (and `accessors.*`), are listed in `sagefang.mixins.json` (
`compatibilityLevel: JAVA_25`), and use `sagefang.accesswidener` (`v2 official`).

- Every new mixin must be added to `sagefang.mixins.json`, or it won't load.
- Targets use **official 26.1 names** (e.g. `@Mixin(Camera.class)`,
  `target = "Lnet/minecraft/client/Camera;setRotation(FF)V"`). There is no runtime remapping.
- Prefer MixinExtras (`@WrapOperation`, `@ModifyExpressionValue`, `@WrapWithCondition`) over fragile raw `@Redirect`
  when expressing intent; it's already on the classpath.
- Keep injected methods prefixed (`sagefang$...`) as the existing mixins do.
- When a vanilla method is renamed/moved across versions, update the `method =` / `target =` descriptors. A silently
  non-matching injector is the common cause of "my mixin does nothing".

---

## Thread safety

The render thread, the network thread, and background `Timer`s share state.

- **Touch Minecraft/Bukkit-equivalent client state on the render thread.** From off-thread (network receivers, timers),
  hop on with `mc.execute(() -> ...)`.
- **`ConcurrentHashMap` for shared `static` maps.** Never `HashMap` for a static field touched by more than one thread.
- **`Collections.newSetFromMap(new ConcurrentHashMap<>())` for shared sets.**
- **`merge()` for counter increments**, `computeIfAbsent`/`putIfAbsent` for check-then-set. Never `getOrDefault + put`
  or `containsKey + put`.
- **Local collections inside a single tick/frame can be plain `ArrayList`/`HashSet`.** Faster, and fine when the value
  never escapes.

---

## Comments

No decorative section dividers or banner comments, meaning anything of the form `// ── text ──────`,
`// --- text -------`,
`// ===== text =====`, or a label padded out with runs of `─`, `-`, `=`, `*`, `#`. They add noise and go stale. Don't
introduce them in new code, and don't reformat surrounding code to add them; if a method is long enough to "need"
section banners, that's a signal to split it, not to decorate it. (Some existing files still carry these from before
this rule, so leave them unless you're already rewriting that block.) Add comments only where the logic isn't
self-evident;
keep
the existing Javadoc on public API types.

---

## Build and run

- `./gradlew runClient` launches the dev client (DevAuth handles login). `./gradlew build` to produce the jar.
- Java **25** toolchain; MC **26.1.2**, non-obfuscated → the **new `net.fabricmc.fabric-loom`** plugin with **no
  remapping**: plain `implementation`/`runtimeOnly`, no `mappings` declaration, no `mod*` configs.
- Bundled deps (imgui, cloud, adventure, avaje, checker-qual) ship as jar-in-jar via loom `include`, with no shadow/fat
  jar.
- The benign log line `Runtime mod remapping disabled due to no fabric.remapClasspathFile being specified` is expected
  under the new Loom.
- Third-party Fabric deps must be built for non-obf 26.1 (official names). A dep compiled against intermediary (
  `net.minecraft.class_XXXX`) will throw `NoClassDefFoundError` at runtime, so bump it to a 26.1 build. See the project
  memory note on the 26.1 migration.

---

## Summary: do / don't

| Do                                                       | Don't                                                     |
|----------------------------------------------------------|-----------------------------------------------------------|
| `MessageUtils.sendMessage(mc, component)`                | `mc.gui.getChat().addMessage(...)` with `§` codes         |
| `MiniMessage.format("...", Placeholder.unparsed(...))`   | `MiniMessage.format("..." + value)`                       |
| `<off_white>` / `<highlight>` palette tags               | Raw `<#RRGGBB>` hex when a named tag exists               |
| Implement `SFCommand`, add to `COMMANDS[]`               | Hand-rolling a second command manager                     |
| `@EventHandler` + `EVENT_BUS.subscribe(X.instance)`      | Forgetting to subscribe / register the module's menu      |
| `Config.getBool(key, default)` + `setProperty`           | Writing `sagefang.properties` directly                    |
| `Module.toggle()` / `toggleVisible()`                    | Setting `module.<id>.enabled` by hand                     |
| Official 26.1 names in mixins/AW                         | Intermediary `class_XXXX` names; expecting remapping      |
| MixinExtras (`@WrapOperation`, `@ModifyExpressionValue`) | Fragile raw `@Redirect` when intent fits MixinExtras      |
| Add new mixins to `sagefang.mixins.json`                 | A mixin file that's never listed                          |
| `mc.execute(() -> ...)` from off-thread                  | Touching `mc.player`/world from a network thread or timer |
| `ConcurrentHashMap` for `static` shared maps             | `HashMap`/`HashSet` for static shared state               |
| Hoist work out of ImGui `frame(...)`                     | Allocating/parsing/looking up every frame                 |
| Null-check `mc.player`, `mc.level`, registry `Optional`  | Assuming Minecraft client state is present                |
