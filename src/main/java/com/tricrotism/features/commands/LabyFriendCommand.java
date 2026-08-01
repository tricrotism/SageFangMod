package com.tricrotism.features.commands;

import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.modules.clientdetect.labymod.LabySocial;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.UserStatus;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.network.chat.Component;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.util.List;

import static org.incendo.cloud.parser.standard.EnumParser.enumParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * LabyConnect social commands: {@code /sagefang friend add|remove|accept|deny|list|msg}
 * and {@code /sagefang status <state>}.
 */
public class LabyFriendCommand implements SFCommand {

    @Override
    public void register(CommandManager<FabricClientCommandSource> manager, Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("friend").literal("add")
            .required("name", stringParser()).handler(this::add));
        manager.command(root.literal("friend").literal("remove")
            .required("name", stringParser()).handler(this::remove));
        manager.command(root.literal("friend").literal("accept")
            .required("name", stringParser()).handler(this::accept));
        manager.command(root.literal("friend").literal("deny")
            .required("name", stringParser()).handler(this::deny));
        manager.command(root.literal("friend").literal("list").handler(this::list));
        manager.command(root.literal("friend").literal("msg")
            .required("name", stringParser())
            .required("message", greedyStringParser()).handler(this::msg));
        manager.command(root.literal("status")
            .required("status", enumParser(UserStatus.class)).handler(this::status));
    }

    private void add(CommandContext<FabricClientCommandSource> ctx) {
        if (notConnected(ctx)) return;
        String name = ctx.get("name");
        LabySocial.instance.addFriend(name);
        feedback(ctx, MiniMessage.format("<info>Sending friend request to <highlight><name></highlight>…",
            Placeholder.unparsed("name", name)));
    }

    private void remove(CommandContext<FabricClientCommandSource> ctx) {
        Friend f = require(ctx);
        if (f == null) return;
        LabySocial.instance.removeFriend(f);
        feedback(ctx, MiniMessage.format("<success>Removed <highlight><name></highlight>.",
            Placeholder.unparsed("name", f.getName())));
    }

    private void accept(CommandContext<FabricClientCommandSource> ctx) {
        Friend f = require(ctx);
        if (f == null) return;
        LabySocial.instance.acceptRequest(f);
        feedback(ctx, MiniMessage.format("<success>Accepted <highlight><name></highlight>.",
            Placeholder.unparsed("name", f.getName())));
    }

    private void deny(CommandContext<FabricClientCommandSource> ctx) {
        Friend f = require(ctx);
        if (f == null) return;
        LabySocial.instance.denyRequest(f);
        feedback(ctx, MiniMessage.format("<warning>Denied <highlight><name></highlight>.",
            Placeholder.unparsed("name", f.getName())));
    }

    private void list(CommandContext<FabricClientCommandSource> ctx) {
        if (notConnected(ctx)) return;
        List<Friend> friends = LabySocial.instance.getFriends();
        if (friends.isEmpty()) {
            feedback(ctx, MiniMessage.format("<neutral>No friends online."));
            return;
        }
        feedback(ctx, MiniMessage.format("<info>Friends (<highlight><count></highlight>):",
            Placeholder.unparsed("count", String.valueOf(friends.size()))));
        for (Friend f : friends) {
            String where = f.getServer() != null && f.getServer().isPresent()
                ? f.getServer().getAddress() : "—";
            feedback(ctx, MiniMessage.format(f.getStatus().getColorTag() + "● <off_white><name> <dark_gray>(<where>)",
                Placeholder.unparsed("name", f.getName()),
                Placeholder.unparsed("where", where)));
        }
    }

    private void msg(CommandContext<FabricClientCommandSource> ctx) {
        Friend f = require(ctx);
        if (f == null) return;
        String message = ctx.get("message");
        LabySocial.instance.sendMessage(f, message);
        feedback(ctx, MiniMessage.format("<highlight>You <dark_gray>» <off_white><msg>",
            Placeholder.unparsed("msg", message)));
    }

    private void status(CommandContext<FabricClientCommandSource> ctx) {
        if (notConnected(ctx)) return;
        UserStatus status = ctx.get("status");
        LabySocial.instance.setStatus(status);
        feedback(ctx, MiniMessage.format("<success>Status set to " + status.getColorTag() + "<state></highlight>.",
            Placeholder.unparsed("state", status.name().toLowerCase())));
    }

    /**
     * Resolves the {@code name} argument to a known friend/request, or errors.
     */
    private Friend require(CommandContext<FabricClientCommandSource> ctx) {
        if (notConnected(ctx)) return null;
        String name = ctx.get("name");
        Friend f = LabySocial.instance.findFriendByName(name);
        if (f == null) {
            ctx.sender().sendError(asNative(MiniMessage.format("<error>No friend named <name>.",
                Placeholder.unparsed("name", name))));
        }
        return f;
    }

    private boolean notConnected(CommandContext<FabricClientCommandSource> ctx) {
        if (!LabySocial.instance.isConnected()) {
            ctx.sender().sendError(asNative(MiniMessage.format("<error>Not connected to LabyConnect.")));
            return true;
        }
        return false;
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, net.kyori.adventure.text.Component component) {
        ctx.sender().sendFeedback(asNative(component));
    }

    private static Component asNative(net.kyori.adventure.text.Component component) {
        return MinecraftClientAudiences.of().asNative(component);
    }
}
