package com.tricrotism.modules.logger;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.MessageUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds players the server is hiding from us.
 * <p>
 * A vanish plugin can withhold the hidden player's entity and their tab list entry, and the good
 * ones do. What it cannot easily withhold is everything else the server says about them, and every
 * detector here is one of those residues:
 * <ul>
 *   <li><b>untabbed spawn</b> a player entity is spawned for us with no tab list entry behind it.
 *       Hiding a player is a per-viewer tracker write and the tab list is a packet filter, so any
 *       moment the two disagree puts the hidden player's id, uuid and position on the wire. The
 *       window at our own join is the reliable one, since the hide for a new viewer is scheduled a
 *       tick after the tracker has already introduced everybody.</li>
 *   <li><b>barrel desync</b> we open a barrel and the client still draws it shut. A barrel's lid is
 *       a blockstate rather than a block event, so the server only broadcasts it on the first and
 *       last opener. A hidden player took the first, and suppressing that broadcast means our own
 *       open never gets one.</li>
 *   <li><b>silent tab exit</b> a tab list entry is removed, or added, with no join or leave line
 *       naming that player inside the window. A real disconnect carries one, a vanish toggle only
 *       carries one if the plugin fakes it, and faking it is usually off by default.</li>
 *   <li><b>ghost entity</b> a packet references an entity id we were never told about. Item and
 *       experience pickups, leashes, passengers, hurt animations and entity sounds all name an
 *       entity id, and a per-viewer packet filter has to enumerate every such packet to be
 *       complete.</li>
 *   <li><b>synthetic container</b> the same pitch twice on a chest, barrel, ender chest or shulker
 *       open or close. Vanilla rolls a fresh float in [0.9, 1.0) every time and never repeats one,
 *       so a repeat is a plugin replaying the sound a hidden opener suppressed.</li>
 *   <li><b>orphan player sound</b> a PLAYERS-source sound at a position with no player of ours
 *       anywhere near it. Silencing the hidden player's entity covers the sounds the entity makes
 *       and not the ones the world plays at it, experience pickup being the usual survivor.</li>
 *   <li><b>not-online oracle</b> a private message rejected with a plugin's own wording rather than
 *       the server's. That branch only runs when the target is online and hidden from us.</li>
 *   <li><b>unlisted name</b> a player name the server mentions in team or scoreboard data while
 *       refusing to list them in tab. Low confidence on its own, since scoreboards legitimately
 *       carry offline names, but it is the cheapest way to learn a name to probe.</li>
 *   <li><b>plugin roster</b> a reply to {@code /list} that reads like vanilla's but is a literal
 *       component rather than a translatable one. Vanilla answers with {@code commands.list.players}
 *       and a plugin rewriting the roster has to build the line itself.</li>
 * </ul>
 * Packets arrive on the network thread and are queued; every decision that reads the world or the
 * tab list is made on the next client tick, which also removes the false positives from a spawn
 * that simply had not been processed yet.
 */
public final class VanishDetect extends Module {

    public static final VanishDetect instance = new VanishDetect();

    private static final int MAX_HITS = 200;
    private static final long NOTIFY_COOLDOWN_MS = 1500L;
    private static final long NAME_SWEEP_MS = 1000L;

    // a name has to stay absent across this many sweeps before it counts, since the tab list can
    // drop an entry for a moment while the server rebuilds it
    private static final int NAME_STRIKES = 3;

    // The initial tab list burst is one ADD_PLAYER per online player, and none of them has a join
    // line. Behind a proxy that rewrites profiles the burst lands well after the login packets:
    // on fyresmp the tab list was still filling eight seconds in, and every vector fired against
    // the half-built list until it settled.
    private static final long JOIN_GRACE_MS = 15000L;

    // a distinct float per chest open, so the table only grows with genuine use
    private static final int MAX_PITCHES = 8192;

    // the barrel blockstate rides the end-of-tick block update rather than the open screen packet
    private static final long BARREL_DELAY_MS = 250L;

    // how long after a right-click an open screen still belongs to that block
    private static final long CLICK_WINDOW_MS = 1500L;

    private static final Pattern NOT_ONLINE = Pattern.compile("^Player (\\w{1,16}) is not online\\.$");
    private static final Pattern PLAYER_NAME = Pattern.compile("^\\w{3,16}$");
    private static final Pattern ROSTER = Pattern.compile("^There are \\d+ of a max of \\d+ players online:");

    private static final Set<SoundEvent> CONTAINER_SOUNDS = Set.of(
        SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE,
        SoundEvents.BARREL_OPEN, SoundEvents.BARREL_CLOSE,
        SoundEvents.ENDER_CHEST_OPEN, SoundEvents.ENDER_CHEST_CLOSE,
        SoundEvents.SHULKER_BOX_OPEN, SoundEvents.SHULKER_BOX_CLOSE
    );

    private final Settings.Bool untabbedSpawn =
        bool("Untabbed spawns", "untabbedspawn", "Player entities spawned with no tab list entry behind them.", true);
    private final Settings.Bool barrelDesync =
        bool("Barrel desync", "barreldesync", "Barrels we open that the client still draws shut.", true);
    private final Settings.Bool tabSilence =
        bool("Silent tab exits", "tabsilence", "Tab entry added or removed with no join or leave line.", true);
    private final Settings.Bool ghostEntities =
        bool("Ghost entity ids", "ghostentities", "Packets naming an entity we were never told about.", true);
    private final Settings.Bool containerPitch =
        bool("Synthetic containers", "containerpitch", "Container sounds replayed at a pitch vanilla already used.", true);
    private final Settings.Bool orphanSounds =
        bool("Orphan player sounds", "orphansounds", "Player-source sounds with no player near them.", true);
    private final Settings.Bool notOnlineOracle =
        bool("Not-online oracle", "notonlineoracle", "Private messages rejected with a plugin's wording.", true);
    private final Settings.Bool nameHarvest =
        bool("Unlisted names", "nameharvest", "Names in team or scoreboard data that tab does not list.", true);
    private final Settings.Bool rosterFingerprint =
        bool("Plugin roster", "rosterfingerprint", "A /list reply built by a plugin rather than by vanilla.", true);
    private final Settings.Bool spectatorWatch =
        bool("Spectator watch", "spectatorwatch", "Players the tab list reports as spectators.", true);
    private final Settings.Int messageWindow =
        integer("Message window", "messagewindow", "How long a tab change waits for its chat line, in ms.", 1500, 200, 10000);
    private final Settings.Decimal soundRadius =
        decimal("Sound radius", "soundradius", "How close a player has to be to own a sound, in blocks.", 5.0, 1.0, 16.0);
    private final Settings.Bool notify =
        bool("Chat notify", "notify", "Announce hits in chat as well as recording them.", true);

    private final Queue<Probe> probes = new ConcurrentLinkedQueue<>();
    private final Map<UUID, String> tabNames = new ConcurrentHashMap<>();
    private final Set<UUID> everListed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GameType> tabModes = new ConcurrentHashMap<>();
    private final Map<String, String> harvested = new ConcurrentHashMap<>();

    @Nullable
    private volatile Probe lastClick;

    private final Deque<Hit> hits = new ArrayDeque<>();
    private final List<Pending> pending = new ArrayList<>();
    private final List<Probe> deferred = new ArrayList<>();
    private final Deque<Line> lines = new ArrayDeque<>();
    private final Map<String, Long> lastNotify = new HashMap<>();
    private final Map<String, Integer> misses = new HashMap<>();
    private final Map<Float, Integer> containerPitches = new HashMap<>();

    private boolean sawNamedLine;
    private long joinedAt;
    private long lastNameSweep;

    private VanishDetect() {
        super("vanishdetect", "Vanish Detect", "Find players the server is hiding.", Category.LOGGING);
    }

    private enum Kind {TAB_ADD, TAB_REMOVE, CHAT, ENTITY, CONTAINER, SOUND, SPAWN, BARREL, ROSTER, SPECTATOR}

    private record Probe(Kind kind, long at, int id, double x, double y, double z, float pitch, String text) {}

    private record Pending(Kind kind, String name, long deadline) {}

    private record Line(long at, String text) {}

    private record Hit(long at, String vector, String detail) {}

    /**
     * Called from ConnectionMixin on the network thread, so this only writes the concurrent handoff
     * and decides nothing that needs the world.
     */
    public void onInbound(Packet<?> packet) {
        if (!isActive()) return;

        long now = System.currentTimeMillis();

        switch (packet) {
            case ClientboundLoginPacket ignored -> resetInbound();
            case ClientboundPlayerInfoUpdatePacket info -> onPlayerInfo(info, now);
            case ClientboundPlayerInfoRemovePacket remove -> onPlayerRemove(remove, now);
            case ClientboundAddEntityPacket spawn -> onSpawn(spawn, now);
            case ClientboundOpenScreenPacket ignored -> onOpenScreen(now);
            case ClientboundSystemChatPacket chat -> onChat(chat, now);
            case ClientboundTakeItemEntityPacket take -> reference(now, take.getPlayerId(), "pickup");
            case ClientboundHurtAnimationPacket hurt -> reference(now, hurt.id(), "hurt animation");
            case ClientboundAnimatePacket animate -> reference(now, animate.getId(), "animation");
            case ClientboundSoundEntityPacket sound -> reference(now, sound.getId(), "entity sound");
            case ClientboundSetEntityLinkPacket link -> {
                reference(now, link.getSourceId(), "leashed entity");
                reference(now, link.getDestId(), "leash holder");
            }
            case ClientboundSetPassengersPacket ride -> {
                reference(now, ride.getVehicle(), "vehicle");
                for (int id : ride.getPassengers()) reference(now, id, "passenger");
            }
            case ClientboundSoundPacket sound -> onSound(sound, now);
            case ClientboundSetPlayerTeamPacket team -> {
                for (String name : team.getPlayers()) harvest(name, "team " + team.getName());
            }
            case ClientboundSetScorePacket score -> harvest(score.owner(), "objective " + score.objectiveName());
            default -> {
            }
        }
    }

    /**
     * The block we last right-clicked, so an open screen can be attributed to it.
     */
    public void onOutbound(Packet<?> packet) {
        if (!isActive() || !barrelDesync.get()) return;

        if (packet instanceof ServerboundUseItemOnPacket use) {
            BlockPos pos = use.getHitResult().getBlockPos();

            lastClick = new Probe(Kind.BARREL, System.currentTimeMillis(), 0,
                pos.getX(), pos.getY(), pos.getZ(), 0, "");
        }
    }

    private void onSpawn(ClientboundAddEntityPacket packet, long now) {
        if (!untabbedSpawn.get() || packet.getType() != EntityTypes.PLAYER) return;

        probes.add(new Probe(Kind.SPAWN, now, packet.getId(),
            packet.getX(), packet.getY(), packet.getZ(), 0, packet.getUUID().toString()));
    }

    private void onOpenScreen(long now) {
        Probe click = lastClick;
        if (!barrelDesync.get() || click == null || now - click.at() > CLICK_WINDOW_MS) return;

        probes.add(new Probe(Kind.BARREL, now, 0, click.x(), click.y(), click.z(), 0, ""));
    }

    private void onChat(ClientboundSystemChatPacket packet, long now) {
        probes.add(text(Kind.CHAT, now, packet.content().getString()));

        if (rosterFingerprint.get() && !(packet.content().getContents() instanceof TranslatableContents)
            && ROSTER.matcher(packet.content().getString()).find()) {
            probes.add(text(Kind.ROSTER, now, ""));
        }
    }

    private void onPlayerInfo(ClientboundPlayerInfoUpdatePacket packet, long now) {
        boolean added = packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);

        // the gamemode field only carries a real value when one of these two actions asked for it
        boolean modes = added || packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE);

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            if (entry.profile() == null) continue;

            String name = entry.profile().name();
            if (name == null || name.isEmpty()) continue;

            tabNames.put(entry.profileId(), name);
            everListed.add(entry.profileId());
            if (added && tabSilence.get()) probes.add(text(Kind.TAB_ADD, now, name));
            if (modes) onGameMode(entry, name, now);
        }
    }

    /**
     * Staff who watch without vanishing are in the tab list saying so. Vanilla puts a real gamemode
     * in the entry and a vanish plugin filtering the tab list never has to touch this field, since
     * the whole entry is normally gone by then. Transitions only, or a spectator would be reported
     * on every latency sweep.
     */
    private void onGameMode(ClientboundPlayerInfoUpdatePacket.Entry entry, String name, long now) {
        GameType mode = entry.gameMode();
        GameType before = tabModes.put(entry.profileId(), mode);

        if (!spectatorWatch.get() || mode != GameType.SPECTATOR || before == GameType.SPECTATOR) return;

        probes.add(text(Kind.SPECTATOR, now, name));
    }

    private void onPlayerRemove(ClientboundPlayerInfoRemovePacket packet, long now) {
        for (UUID id : packet.profileIds()) {
            String name = tabNames.remove(id);
            tabModes.remove(id);

            if (name != null && tabSilence.get()) probes.add(text(Kind.TAB_REMOVE, now, name));
        }
    }

    /**
     * Container sounds are collected for the pitch table, and player-source sounds are checked
     * against the visible players a tick later.
     */
    private void onSound(ClientboundSoundPacket packet, long now) {
        SoundEvent sound = packet.getSound().value();
        String label = sound.location().getPath();

        if (containerPitch.get() && packet.getSource() == SoundSource.BLOCKS && CONTAINER_SOUNDS.contains(sound)) {
            probes.add(at(Kind.CONTAINER, now, packet, label));
            return;
        }

        if (orphanSounds.get() && packet.getSource() == SoundSource.PLAYERS) {
            probes.add(at(Kind.SOUND, now, packet, label));
        }
    }

    private void reference(long now, int entityId, String label) {
        if (ghostEntities.get() && entityId > 0) probes.add(new Probe(Kind.ENTITY, now, entityId, 0, 0, 0, 0, label));
    }

    private void harvest(String name, String source) {
        if (nameHarvest.get() && PLAYER_NAME.matcher(name).matches()) harvested.put(name, source);
    }

    private static Probe text(Kind kind, long at, String text) {
        return new Probe(kind, at, 0, 0, 0, 0, 0, text);
    }

    private static Probe at(Kind kind, long at, ClientboundSoundPacket packet, String text) {
        return new Probe(kind, at, 0, packet.getX(), packet.getY(), packet.getZ(), packet.getPitch(), text);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.level == null || mc.getConnection() == null) {
            probes.clear();
            return;
        }

        long now = System.currentTimeMillis();

        drain(now);
        expire(now);
        sweepNames(now);
        prune(now);
    }

    private void drain(long now) {
        Probe probe;

        while ((probe = probes.poll()) != null) {
            switch (probe.kind()) {
                case CHAT -> onLine(probe);
                case TAB_ADD, TAB_REMOVE -> {
                    if (now - joinedAt >= JOIN_GRACE_MS && !cancelOpposite(probe)) {
                        pending.add(new Pending(probe.kind(), probe.text(), probe.at() + messageWindow.get()));
                    }
                }
                case ENTITY -> {
                    if (mc.level.getEntity(probe.id()) == null) {
                        report("ghost entity", probe.text() + " named entity " + probe.id() + ", which we never saw spawn");
                    }
                }
                case CONTAINER -> onContainer(probe);
                case SOUND -> {
                    if (!playerNear(probe)) report("orphan player sound", probe.text() + " " + where(probe));
                }
                case SPAWN -> onSpawnProbe(probe);
                case BARREL -> deferred.add(probe);
                case ROSTER -> report("plugin roster", "/list is answered by a plugin, so its count is filtered");
                case SPECTATOR -> report("spectator watch", probe.text() + " is listed as a spectator");
            }
        }
    }

    /**
     * A tab entry that is removed and added straight back is a refresh, which servers use to restage
     * a prefix or a skin. The first half of the pair is still waiting out its window when the second
     * arrives, so a match drops both rather than reporting a vanish and an unvanish.
     */
    private boolean cancelOpposite(Probe probe) {
        Kind opposite = probe.kind() == Kind.TAB_ADD ? Kind.TAB_REMOVE : Kind.TAB_ADD;

        return pending.removeIf(entry -> entry.kind() == opposite && entry.name().equals(probe.text()));
    }

    /**
     * A player entity with nothing behind it in the tab list. Resolved a tick late so an add that
     * simply trailed the spawn in the same flush does not count, and split by whether we have ever
     * seen the uuid listed, since a server's NPCs are player entities that are never listed at all.
     */
    private void onSpawnProbe(Probe probe) {
        UUID id = uuid(probe.text());
        if (id == null || mc.getConnection().getPlayerInfo(id) != null) return;

        report("untabbed spawn", (everListed.contains(id)
            ? "listed earlier and now hidden, entity " : "never listed, possibly an npc, entity ")
            + probe.id() + " " + where(probe) + " " + id);
    }

    private static UUID uuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Vanilla rolls the pitch from the world random on every open and close, so two container sounds
     * carrying the identical float did not both come from the world.
     */
    private void onContainer(Probe probe) {
        if (containerPitches.size() >= MAX_PITCHES) containerPitches.clear();

        if (containerPitches.merge(probe.pitch(), 1, Integer::sum) < 2) return;

        report("synthetic container", probe.text() + " repeats pitch " + probe.pitch() + " " + where(probe));
    }

    private void onLine(Probe probe) {
        lines.addLast(new Line(probe.at(), probe.text()));

        if (!notOnlineOracle.get()) return;

        Matcher matcher = NOT_ONLINE.matcher(probe.text());
        if (matcher.matches()) {
            report("not-online oracle", matcher.group(1) + " is online and hidden from us");
        }
    }

    /**
     * A tab change is only a hit once its window has closed with no line naming that player.
     */
    private void expire(long now) {
        Iterator<Pending> iterator = pending.iterator();

        while (iterator.hasNext()) {
            Pending entry = iterator.next();
            if (now < entry.deadline()) continue;

            iterator.remove();

            if (mentioned(entry.name())) {
                sawNamedLine = true;
                continue;
            }

            report(entry.kind() == Kind.TAB_REMOVE ? "silent tab exit" : "silent tab entry", entry.name());
        }

        Iterator<Probe> barrels = deferred.iterator();

        while (barrels.hasNext()) {
            Probe probe = barrels.next();
            if (now - probe.at() < BARREL_DELAY_MS) continue;

            barrels.remove();
            onBarrel(probe);
        }
    }

    /**
     * Vanilla broadcasts a barrel's lid on the first opener and the last, so our own open only goes
     * unannounced when somebody we cannot see already took the first.
     */
    private void onBarrel(Probe probe) {
        if (mc.player == null || mc.player.containerMenu == mc.player.inventoryMenu) return;

        BlockPos pos = BlockPos.containing(probe.x(), probe.y(), probe.z());
        BlockState state = mc.level.getBlockState(pos);

        if (!(state.getBlock() instanceof BarrelBlock) || state.getValue(BarrelBlock.OPEN)) return;

        report("barrel desync", "we hold this barrel open and the client still draws it shut " + where(probe));
    }

    private boolean mentioned(String name) {
        String needle = name.toLowerCase(Locale.ROOT);

        for (Line line : lines) {
            if (line.text().toLowerCase(Locale.ROOT).contains(needle)) return true;
        }

        return false;
    }

    private void sweepNames(long now) {
        if (!nameHarvest.get() || now - lastNameSweep < NAME_SWEEP_MS) return;

        lastNameSweep = now;

        for (Map.Entry<String, String> entry : harvested.entrySet()) {
            String name = entry.getKey();
            if (mc.getConnection().getPlayerInfoIgnoreCase(name) != null) {
                misses.remove(name);
                continue;
            }

            int strikes = misses.merge(name, 1, Integer::sum);
            if (strikes == NAME_STRIKES) {
                report("unlisted name", name + " is named by " + entry.getValue() + " but absent from tab");
            }
        }
    }

    private void prune(long now) {
        long horizon = now - messageWindow.get() * 2L;

        while (!lines.isEmpty() && lines.peekFirst().at() < horizon) lines.removeFirst();
    }

    private boolean playerNear(Probe probe) {
        double radius = soundRadius.get();
        double limit = radius * radius;

        for (Player player : mc.level.players()) {
            if (player.distanceToSqr(probe.x(), probe.y(), probe.z()) <= limit) return true;
        }

        return false;
    }

    private static String where(Probe probe) {
        return "at " + Math.round(probe.x()) + " " + Math.round(probe.y()) + " " + Math.round(probe.z());
    }

    private void report(String vector, String detail) {
        // Nothing the server sends in the first seconds of a join is evidence of anything. The tab
        // list, the scoreboard and the entity tracker all arrive out of order and half-built, and
        // every vector here reads one of them.
        if (System.currentTimeMillis() - joinedAt < JOIN_GRACE_MS) return;

        if (hits.size() >= MAX_HITS) hits.removeFirst();
        hits.addLast(new Hit(System.currentTimeMillis(), vector, detail));

        if (!notify.get()) return;

        long now = System.currentTimeMillis();
        Long last = lastNotify.get(vector);
        if (last != null && now - last < NOTIFY_COOLDOWN_MS) return;

        lastNotify.put(vector, now);
        MessageUtils.sendMessage(mc, Component.empty().append(MinecraftClientAudiences.of().asNative(
            MiniMessage.format(
                "<warning><vector><dark_gray>: <off_white><detail>",
                Placeholder.unparsed("vector", vector),
                Placeholder.unparsed("detail", detail)
            )
        )));
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        joinedAt = System.currentTimeMillis();
        // onInbound already cleared the netty-fed state at the login packet. Clearing it again here
        // would throw away the tab burst that arrives first.
        resetDrained();
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        resetInbound();
        resetDrained();
    }

    private void resetInbound() {
        probes.clear();
        tabNames.clear();
        everListed.clear();
        tabModes.clear();
        harvested.clear();
    }

    private void resetDrained() {
        lastClick = null;
        hits.clear();
        pending.clear();
        deferred.clear();
        lines.clear();
        lastNotify.clear();
        misses.clear();
        containerPitches.clear();
        sawNamedLine = false;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textUnformatted("Hits: " + hits.size());
        ImGui.textUnformatted("Names tracked: " + harvested.size());
        ImGui.textUnformatted(sawNamedLine
            ? "Join/leave lines seen, silent tab changes are meaningful"
            : "No join/leave line matched yet, tab hits are unproven");

        if (ImGui.button("Clear##vanishDetectClear")) {
            hits.clear();
            misses.clear();
        }

        if (!ImGui.treeNode("Log##vanishDetectLog")) return;

        for (Hit hit : hits.reversed()) {
            ImGui.textUnformatted(hit.vector() + ": " + hit.detail());
        }

        ImGui.treePop();
    }
}
