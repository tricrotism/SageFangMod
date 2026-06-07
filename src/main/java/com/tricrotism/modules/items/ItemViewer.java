package com.tricrotism.modules.items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * ImGui window for viewing and editing item data components on the held item.
 * <p>
 * TODO: Redo, it's fucking dogshit atm
 */
public class ItemViewer extends Module implements Menu {

    public static final ItemViewer instance = new ItemViewer();

    /**
     * Edit buffers keyed by component name.
     */
    private final Map<String, ImString> editBuffers = new HashMap<>();

    /**
     * Tracks what item the buffers were built for, so we rebuild on change.
     */
    private int lastItemHash;

    /**
     * Per-component error messages from failed parses.
     */
    private final Map<String, String> errors = new HashMap<>();

    public ItemViewer() {
        super("itemviewer", "Item Viewer", "View and edit item data components.", "Utility");
    }

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!isVisible()) return;

            var player = Minecraft.getInstance().player;
            if (player == null) return;

            ImGui.setNextWindowBgAlpha(0.55f);
            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            ImGui.begin("Item Viewer", flags);

            if (ImGui.checkbox("Enabled##ivEnabled", isActive())) {
                toggle();
            }
            ImGui.separator();

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                ImGui.text("Hold an item to inspect.");
                ImGui.end();
                return;
            }

            int currentHash = ItemStack.hashItemAndComponents(stack);
            if (currentHash != lastItemHash) {
                editBuffers.clear();
                errors.clear();
                lastItemHash = currentHash;
            }

            ImGui.text("Item: " + stack.getHoverName().getString());
            Identifier itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
            ImGui.text("ID: " + (itemKey != null ? itemKey.toString() : "unknown"));
            ImGui.text("Count: " + stack.getCount());

            ImGui.separatorText("Components");

            for (TypedDataComponent<?> component : stack.getComponents()) {
                renderComponent(stack, component);
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in ItemViewer menu", e);
            ImGui.end();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void renderComponent(ItemStack stack, TypedDataComponent<T> component) {
        DataComponentType<T> type = component.type();
        Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        String name = key != null ? key.getPath() : type.toString();
        Codec<T> codec = type.codec();
        boolean transient_ = codec == null;

        if (!ImGui.treeNode(name + (transient_ ? " (transient)" : ""))) {
            return;
        }

        String snbt;
        if (transient_) {
            snbt = String.valueOf(component.value());
        } else {
            DataResult<Tag> encoded = component.encodeValue(NbtOps.INSTANCE);
            snbt = encoded.result().map(Tag::toString).orElse("<encode error>");
        }

        ImGui.textWrapped(snbt);

        if (!transient_) {
            if (isActive()) {
                ImString buf = editBuffers.computeIfAbsent(name, k -> new ImString(snbt, 4096));

                ImGui.pushItemWidth(350);
                if (ImGui.inputText("##edit_" + name, buf)) {
                    errors.remove(name);
                }
                ImGui.popItemWidth();

                ImGui.sameLine();
                if (ImGui.button("Apply##" + name)) {
                    applyEdit(stack, type, codec, name, buf.get());
                    lastItemHash = ItemStack.hashItemAndComponents(stack);
                }

                ImGui.sameLine();
                if (ImGui.button("Reset##" + name)) {
                    editBuffers.put(name, new ImString(snbt, 4096));
                    errors.remove(name);
                }

                String error = errors.get(name);
                if (error != null) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.3f, 0.3f, 1.0f);
                    ImGui.textWrapped(error);
                    ImGui.popStyleColor();
                }
            } else {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);
                ImGui.text("(enable module to edit)");
                ImGui.popStyleColor();
            }
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);
            ImGui.text("(not editable — transient component)");
            ImGui.popStyleColor();
        }

        ImGui.treePop();
    }

    /**
     * Parses user-edited SNBT back into a Tag and decodes it via the component codec.
     * On success, sets the value on the ItemStack (client-side only).
     */
    private <T> void applyEdit(ItemStack stack, DataComponentType<T> type, Codec<T> codec, String name, String input) {
        try {
            Tag tag;
            try {
                CompoundTag compound = TagParser.parseCompoundFully("{v:" + input + "}");
                tag = compound.get("v");
            } catch (Exception e) {
                try {
                    tag = TagParser.parseCompoundFully(input);
                } catch (Exception e2) {
                    errors.put(name, "Parse error: " + e.getMessage());
                    return;
                }
            }

            if (tag == null) {
                errors.put(name, "Parsed tag was null.");
                return;
            }

            DataResult<T> decoded = codec.parse(NbtOps.INSTANCE, tag);
            var optional = decoded.result();
            if (optional.isPresent()) {
                stack.set(type, optional.get());
                errors.remove(name);
            } else {
                String err = decoded.error().map(DataResult.Error::message).orElse("Unknown decode error");
                errors.put(name, "Decode error: " + err);
            }
        } catch (Exception e) {
            errors.put(name, "Error: " + e.getMessage());
        }
    }
}
