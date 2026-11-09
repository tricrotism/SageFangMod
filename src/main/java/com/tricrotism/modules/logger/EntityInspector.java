package com.tricrotism.modules.logger;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the server has told us about one entity or block entity, on demand.
 * <p>
 * The detector modules in this package work from the outside: they notice that a packet named an
 * entity we never saw spawn, or that a name appears in team data and not in tab. What none of them
 * can do is answer the next question, which is what that thing actually is. Synched entity data is
 * the server's own description of an entity, sent as numbered slots whose meaning is a class detail,
 * and nothing in the game surfaces it.
 * <p>
 * Aim at something and press the key. For an entity that is the type, ids, position, every
 * non-default synced slot, the syncable attributes and the worn equipment; for a block entity it is
 * the saved tag, which is the same shape as what arrived in the block entity data packet. The
 * snapshot is frozen at capture so it can be read while the target moves or despawns.
 * <p>
 * Pairs with {@code VanishDetect}: that reports a ghost entity id, this says what was standing
 * there. Note that a hidden player is hidden from the client's own entity list too, so a ghost id
 * usually has nothing here to inspect. That absence is itself the confirmation.
 */
public final class EntityInspector extends Module {

    public static final EntityInspector instance = new EntityInspector();

    private static final int MAX_LINES = 400;

    private final Settings.Key capture =
        key("Capture", "capture", "Snapshot whatever the crosshair is on", GLFW.GLFW_KEY_I);
    private final Settings.Bool attributes =
        bool("Attributes", "attributes", "Include syncable attributes on living entities.", true);
    private final Settings.Bool equipment =
        bool("Equipment", "equipment", "Include worn and held items.", true);

    private final List<String> snapshot = new ArrayList<>();
    private String heading = "Nothing captured";
    private boolean keyWasDown;

    private EntityInspector() {
        super("entityinspector", "Entity Inspector",
            "Snapshot the server's own description of an entity or block entity.", Category.LOGGING);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        int bound = capture.get();
        if (bound == GLFW.GLFW_KEY_UNKNOWN) return;

        boolean down = KeybindUtil.isKeyDown(bound);
        if (down && !keyWasDown) take();
        keyWasDown = down;
    }

    /**
     * Runs on the tick thread, which is where the world is legal to read.
     */
    private void take() {
        if (mc.level == null) return;

        snapshot.clear();

        Entity target = mc.crosshairPickEntity;
        if (target != null) {
            describeEntity(target);
            return;
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult block)) {
            heading = "Crosshair is on nothing";
            return;
        }

        BlockPos pos = block.getBlockPos();
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);

        if (blockEntity == null) {
            heading = "Block at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " has no block entity";
            snapshot.add("state: " + mc.level.getBlockState(pos));
            return;
        }

        describeBlockEntity(blockEntity, pos);
    }

    private void describeEntity(Entity entity) {
        heading = entity.getType().toShortString() + " (entity " + entity.getId() + ")";

        snapshot.add("uuid: " + entity.getUUID());
        snapshot.add(String.format(Locale.ROOT, "pos: %.3f %.3f %.3f", entity.getX(), entity.getY(), entity.getZ()));
        snapshot.add(String.format(Locale.ROOT, "rot: yaw %.1f pitch %.1f", entity.getYRot(), entity.getXRot()));
        snapshot.add("name: " + entity.getName().getString());
        snapshot.add("pose: " + entity.getPose());
        snapshot.add("flags: invisible " + entity.isInvisible() + ", glowing " + entity.isCurrentlyGlowing()
            + ", sneaking " + entity.isCrouching() + ", sprinting " + entity.isSprinting());

        if (entity.getVehicle() != null) snapshot.add("riding: entity " + entity.getVehicle().getId());
        if (!entity.getPassengers().isEmpty()) {
            StringBuilder riders = new StringBuilder();
            for (Entity passenger : entity.getPassengers()) {
                if (!riders.isEmpty()) riders.append(", ");
                riders.append(passenger.getId());
            }
            snapshot.add("carrying: " + riders);
        }

        describeSynchedData(entity);

        if (!(entity instanceof LivingEntity living)) return;

        if (attributes.get()) describeAttributes(living);
        if (equipment.get()) describeEquipment(living);
    }

    /**
     * The numbered slots are the server's own description. Their meaning belongs to the entity class
     * rather than the protocol, so the index is reported as-is and the serializer names the type.
     */
    private void describeSynchedData(Entity entity) {
        List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
        if (values == null || values.isEmpty()) {
            snapshot.add("synched data: all defaults");
            return;
        }

        snapshot.add("synched data (" + values.size() + " non-default):");
        for (SynchedEntityData.DataValue<?> value : values) {
            if (snapshot.size() >= MAX_LINES) return;
            snapshot.add("  [" + value.id() + "] " + describe(value.value()));
        }
    }

    private void describeAttributes(LivingEntity living) {
        var syncable = living.getAttributes().getSyncableAttributes();
        if (syncable.isEmpty()) return;

        snapshot.add("attributes:");
        for (AttributeInstance instance : syncable) {
            if (snapshot.size() >= MAX_LINES) return;
            snapshot.add(String.format(Locale.ROOT, "  %s base %.3f, effective %.3f",
                instance.getAttribute().getRegisteredName(), instance.getBaseValue(), instance.getValue()));
        }
    }

    private void describeEquipment(LivingEntity living) {
        List<String> worn = new ArrayList<>();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = living.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            worn.add("  " + slot.getName() + ": " + stack.getCount() + "x " + stack.getItem()
                + " (" + stack.getHoverName().getString() + ")");
        }

        if (worn.isEmpty()) return;

        snapshot.add("equipment:");
        snapshot.addAll(worn);
    }

    private void describeBlockEntity(BlockEntity blockEntity, BlockPos pos) {
        heading = blockEntity.getType() + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ();

        snapshot.add("state: " + mc.level.getBlockState(pos));

        try {
            String tag = blockEntity.saveWithoutMetadata(mc.level.registryAccess()).toString();
            for (String line : wrap(tag)) {
                if (snapshot.size() >= MAX_LINES) return;
                snapshot.add(line);
            }
        } catch (RuntimeException e) {
            snapshot.add("tag unavailable: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private static String describe(Object value) {
        if (value == null) return "null";
        if (value instanceof ItemStack stack) {
            return stack.isEmpty() ? "empty stack" : stack.getCount() + "x " + stack.getItem();
        }

        String text = value.toString();
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /**
     * A serialized tag is one very long line, and ImGui does not wrap inside a text item.
     */
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();

        for (int i = 0; i < text.length(); i += 110) {
            lines.add(text.substring(i, Math.min(i + 110, text.length())));
        }

        return lines;
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        clear();
    }

    @Override
    public void onDeactivate() {
        clear();
    }

    private void clear() {
        snapshot.clear();
        heading = "Nothing captured";
        keyWasDown = false;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textUnformatted(heading);

        if (ImGui.button("Capture now##entityInspectorCapture")) take();
        ImGui.sameLine();
        if (ImGui.button("Copy##entityInspectorCopy")) {
            ImGui.setClipboardText(heading + System.lineSeparator() + String.join(System.lineSeparator(), snapshot));
        }

        if (snapshot.isEmpty()) return;

        ImGui.separator();
        for (String line : snapshot) ImGui.textUnformatted(line);
    }
}
