package com.tricrotism.modules.logger;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.ImGuiUtil;
import com.tricrotism.utils.MessageUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Reads the enchantment seed the server hands us, and the clues that come with it.
 * <p>
 * Enchanting is decided by a per-player value on the server, and the client is told what it is. It
 * has to be: the three offers in the table are rendered client-side, so the seed arrives in the
 * menu's data slots alongside the costs and the clues. That makes it the cleanest example of the
 * pattern the rest of this package hunts for, and unlike the others it is not an oversight. The
 * protocol requires the leak.
 * <p>
 * What the seed is worth is prediction. It fully determines the three offers for the item in the
 * slot, and it determines them again for every other item you could put there, so a client holding
 * it can shop for an outcome before spending a level. It also survives the roll: the server advances
 * the seed after an enchant rather than replacing it with anything secret, so the next set is a
 * function of this one.
 * <p>
 * <b>This module reports, it does not predict.</b> Turning a seed into a list of outcomes means
 * reimplementing the weighted enchantment selection against the live registry, including every
 * modifier a datapack can add, and that is a different piece of work with a different maintenance
 * cost. The seed, the costs and the clues are what the server actually sent; the arithmetic on top
 * of them is not written yet, and pretending otherwise in the UI would be worse than saying so.
 */
public final class EnchantSeed extends Module {

    public static final EnchantSeed instance = new EnchantSeed();

    private static final int SLOTS = 3;
    private static final int MAX_HISTORY = 32;

    private final Settings.Bool notify =
        bool("Chat notify", "notify", "Announce each new seed in chat.", false);

    private final Deque<Long> history = new ArrayDeque<>();

    private int seed;
    private boolean open;
    private final int[] costs = new int[SLOTS];
    private final String[] clues = new String[SLOTS];

    private EnchantSeed() {
        super("enchantseed", "Enchant Seed",
            "Read the enchantment seed and clues the server sends to the table.", Category.LOGGING);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null) return;

        if (!(mc.player.containerMenu instanceof EnchantmentMenu menu)) {
            open = false;
            return;
        }

        open = true;
        readClues(menu);

        int current = menu.getEnchantmentSeed();
        if (current == seed) return;

        seed = current;
        if (history.size() >= MAX_HISTORY) history.removeFirst();
        history.addLast((long) current);

        if (notify.get()) announce(current);
    }

    private void readClues(EnchantmentMenu menu) {
        Registry<Enchantment> registry = registry();

        for (int slot = 0; slot < SLOTS; slot++) {
            costs[slot] = menu.costs[slot];

            int clue = menu.enchantClue[slot];
            if (clue < 0) {
                clues[slot] = null;
                continue;
            }

            clues[slot] = name(registry, clue) + " " + menu.levelClue[slot];
        }
    }

    /**
     * Enchantments live in a dynamic registry, so the ids in the clues are only meaningful against
     * the one this server sent. Null on the title screen, which is why every read is guarded.
     */
    private Registry<Enchantment> registry() {
        if (mc.level == null) return null;

        try {
            return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String name(Registry<Enchantment> registry, int id) {
        if (registry == null) return "id " + id;

        Enchantment enchantment = registry.byId(id);
        if (enchantment == null) return "id " + id;

        var key = registry.getResourceKey(enchantment);
        return key.map(k -> k.identifier().getPath()).orElse("id " + id);
    }

    private void announce(int current) {
        MessageUtils.sendMessage(mc, Component.empty().append(MinecraftClientAudiences.of().asNative(
            MiniMessage.format(
                "<info>enchant seed<dark_gray>: <off_white><seed>",
                Placeholder.unparsed("seed", String.valueOf(current))
            )
        )));
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
        history.clear();
        seed = 0;
        open = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            costs[slot] = 0;
            clues[slot] = null;
        }
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        if (!open) {
            ImGui.textUnformatted(history.isEmpty()
                ? "Open an enchanting table"
                : "Table closed, last seed " + seed);
        } else {
            ImGui.textUnformatted("Seed: " + seed);
            ImGui.separator();

            for (int slot = 0; slot < SLOTS; slot++) {
                if (costs[slot] <= 0) {
                    ImGui.textUnformatted(String.format(Locale.ROOT, "slot %d: no offer", slot + 1));
                    continue;
                }

                ImGui.textUnformatted(String.format(Locale.ROOT, "slot %d: %d levels, clue %s",
                    slot + 1, costs[slot], clues[slot] == null ? "none" : clues[slot]));
            }
        }

        ImGui.separator();
        ImGui.textUnformatted("Seeds seen: " + history.size());
        if (ImGui.isItemHovered()) {
            ImGuiUtil.setTooltip("The server advances this after each enchant rather than replacing it, "
                + "so the sequence is one chain rather than independent draws.");
        }

        if (ImGui.button("Copy##enchantSeedCopy")) {
            StringBuilder out = new StringBuilder();
            for (long value : history) out.append(value).append(System.lineSeparator());
            ImGui.setClipboardText(out.toString());
        }
        if (ImGui.button("Clear##enchantSeedClear")) history.clear();
    }
}
