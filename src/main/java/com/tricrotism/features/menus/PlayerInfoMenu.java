package com.tricrotism.features.menus;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.config.SageFangConfig;
import com.tricrotism.utils.GraphHistory;
import com.tricrotism.utils.GraphRenderer;
import com.tricrotism.utils.ImGuiUtil;
import com.tricrotism.utils.NumberUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;

@Slf4j
public class PlayerInfoMenu implements Menu {

    private static final long MEMORY_REFRESH_NANOS = 250_000_000L;

    // Each text is rebuilt only when the value it shows changes, never per frame.
    private int shownFps = -1;
    private String fpsText = "";
    private long shownX = Long.MIN_VALUE;
    private long shownY;
    private long shownZ;
    private String xyzFormat = "";
    private Object shownDimension;
    private String worldName = "";
    private long memoryAt;
    private String memoryText = "";
    private long shownPackets = -1;
    private String packetsText = "";

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!SageFangConfig.isPlayerInfoMenuEnabled() || SageFangConfig.isMergedInfoMenu()) {
                return;
            }

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().gui.screen() == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Player Info", flags);

            if (Minecraft.getInstance().player != null) {
                ImGui.textUnformatted("FPS");
                ImGui.sameLine();
                int fps = Minecraft.getInstance().getFps();
                if (fps != shownFps) {
                    shownFps = fps;
                    fpsText = String.valueOf(fps);
                }
                ImGui.textUnformatted(fpsText);

                long hundredthsX = Math.round(Minecraft.getInstance().player.getX() * 100D);
                long hundredthsY = Math.round(Minecraft.getInstance().player.getY() * 100D);
                long hundredthsZ = Math.round(Minecraft.getInstance().player.getZ() * 100D);
                if (hundredthsX != shownX || hundredthsY != shownY || hundredthsZ != shownZ) {
                    shownX = hundredthsX;
                    shownY = hundredthsY;
                    shownZ = hundredthsZ;
                    xyzFormat = String.format("%.2f, %.2f, %.2f", hundredthsX / 100D, hundredthsY / 100D, hundredthsZ / 100D);
                }
                ImGui.textUnformatted("Position");
                ImGui.sameLine();
                ImGui.textUnformatted(xyzFormat);
                if (ImGui.isItemHovered()) {
                    ImGuiUtil.setTooltip("Click to copy");
                }
                if (ImGui.isItemClicked()) {
                    ImGui.setClipboardText(xyzFormat);
                    log.info("Copied position to clipboard: {}", xyzFormat);
                }

                var dimension = Minecraft.getInstance().player.level().dimension();
                if (dimension != shownDimension) {
                    shownDimension = dimension;
                    worldName = dimension.identifier().getPath();
                }
                ImGui.textUnformatted("World");
                ImGui.sameLine();
                ImGui.textUnformatted(worldName);

                // Used memory moves with every allocation, so the text refreshes on a timer instead.
                long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long now = System.nanoTime();
                if (now - memoryAt >= MEMORY_REFRESH_NANOS) {
                    memoryAt = now;
                    long memoryMax = Runtime.getRuntime().maxMemory();
                    memoryText = NumberUtils.formatMemorySize(memoryUsed) + "/" + NumberUtils.formatMemorySize(memoryMax);
                }
                ImGui.textUnformatted("Memory");
                ImGui.sameLine();
                ImGui.textUnformatted(memoryText);

                long packets = Math.round(Minecraft.getInstance().player.connection.getConnection().getAverageReceivedPackets());
                if (packets != shownPackets) {
                    shownPackets = packets;
                    packetsText = String.valueOf(packets);
                }
                ImGui.textUnformatted("Packets/s");
                ImGui.sameLine();
                ImGui.textUnformatted(packetsText);

                if (!Minecraft.getInstance().hasSingleplayerServer()) {
                    var remoteAddress = Minecraft.getInstance().player.connection.getConnection().getRemoteAddress().toString();
                    var serverData = Minecraft.getInstance().player.connection.getServerData();
                    if (serverData != null) {
                        var protocol = serverData.protocol;
                        var brand = Minecraft.getInstance().player.connection.serverBrand();

                        ImGui.textUnformatted("IP");
                        ImGui.sameLine();
                        ImGui.textUnformatted(remoteAddress.split("/")[0]);

                        ImGui.textUnformatted("Address");
                        ImGui.sameLine();
                        ImGui.textUnformatted(remoteAddress.split("/")[1]);

                        ImGui.textUnformatted("Protocol");
                        ImGui.sameLine();
                        ImGui.textUnformatted(String.valueOf(protocol));

                        ImGui.textUnformatted("Brand");
                        ImGui.sameLine();
                        ImGui.textUnformatted(String.valueOf(brand));

                        var detect = com.tricrotism.modules.clientdetect.ClientDetect.instance;
                        int totalPlayers = Minecraft.getInstance().getConnection() != null ? Minecraft.getInstance().getConnection().getOnlinePlayers().size() : 0;
                        if (detect.isActive()) {
                            int labyCount = detect.getLabyModUserCount();
                            if (labyCount > 0 || detect.isLabyModConnected()) {
                                ImGui.textUnformatted("LabyMod");
                                ImGui.sameLine();
                                ImGui.textUnformatted(totalPlayers > 0 ? labyCount + "/" + totalPlayers + " (" + (int) Math.round(100.0 * labyCount / totalPlayers) + "%)" : labyCount + " users");
                            }
                        }
                    }
                }

                if (ImGui.button("Copy Location")) {
                    ImGui.openPopup("##copyLocPopup");
                }
                if (ImGui.beginPopup("##copyLocPopup")) {
                    var mc = Minecraft.getInstance();
                    double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
                    if (ImGui.selectable("X Y Z")) {
                        ImGui.setClipboardText(String.format("%.2f %.2f %.2f", x, y, z));
                    }
                    if (ImGui.selectable("X, Y, Z")) {
                        ImGui.setClipboardText(xyzFormat);
                    }
                    if (ImGui.selectable("X Y Z (Integers)")) {
                        ImGui.setClipboardText(String.format("%d %d %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
                    }
                    if (ImGui.selectable("/tp X Y Z")) {
                        ImGui.setClipboardText(String.format("/tp %.2f %.2f %.2f", x, y, z));
                    }
                    if (ImGui.selectable("Dimension: X, Y, Z")) {
                        ImGui.setClipboardText(String.format("Dimension: %s, Position: %s", worldName, xyzFormat));
                    }
                    if (ImGui.selectable("[Dimension] X Y Z")) {
                        ImGui.setClipboardText(String.format("[%s] %.2f %.2f %.2f", worldName, x, y, z));
                    }
                    if (ImGui.selectable("Bukkit Location")) {
                        ImGui.setClipboardText(String.format(
                            "new Location(Bukkit.getWorld(\"%s\"), %s, %s, %s, %sf, %sf)",
                            worldName, x, y, z, mc.player.getYRot(), mc.player.getXRot()));
                    }
                    ImGui.endPopup();
                }

                // Push client metrics for graphs
                GraphHistory.INSTANCE.ensureCapacity();
                GraphHistory.INSTANCE.pushClientMetrics(
                    Minecraft.getInstance().getFps(),
                    memoryUsed / (1024f * 1024f)
                );

                // Graphs section
                if (SageFangConfig.isShowGraphs() && GraphHistory.INSTANCE.fps.size() > 1) {
                    ImGui.separator();
                    if (ImGui.collapsingHeader("Graphs##clientGraphs")) {
                        if (SageFangConfig.isGraphFps())
                            GraphRenderer.plotLines("FPS", GraphHistory.INSTANCE.fps, 0f, Float.NaN);
                        if (SageFangConfig.isGraphClientMemory())
                            GraphRenderer.plotLines("Client Memory (MB)", GraphHistory.INSTANCE.clientMemory, 0f, Float.NaN);
                    }
                }
            } else {
                ImGui.textUnformatted("Not in world");
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in PlayerInfoMenu", e);
        }
    }

}
