package com.tricrotism.api.text;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class MiniMessage {
    private static final Cache<String, Component> FORMATTED_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(15L, TimeUnit.SECONDS)
            .maximumSize(500_000)
            .build();

    private static final net.kyori.adventure.text.minimessage.MiniMessage MINI_MESSAGE =
            net.kyori.adventure.text.minimessage.MiniMessage.builder()
                    .strict(false)
                    .tags(
                            TagResolver.builder()
                                    .resolvers(StandardTags.defaults())
                                    .build()
                    )
                    .editTags(tagFactory -> {
                        var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

                        // core palette
                        tagFactory.tag("success", Tag.styling(ColorPalette.SUCCESS));
                        tagFactory.tag("warning", Tag.styling(ColorPalette.WARNING));
                        tagFactory.tag("error", Tag.styling(ColorPalette.ERROR));
                        tagFactory.tag("info", Tag.styling(ColorPalette.INFO));
                        tagFactory.tag("neutral", Tag.styling(ColorPalette.NEUTRAL));

                        // reds & oranges
                        tagFactory.tag("dark_red", Tag.styling(ColorPalette.DARK_RED));
                        tagFactory.tag("red", Tag.styling(ColorPalette.RED));
                        tagFactory.tag("light_red", Tag.styling(ColorPalette.LIGHT_RED));
                        tagFactory.tag("crimson", Tag.styling(ColorPalette.CRIMSON));
                        tagFactory.tag("scarlet", Tag.styling(ColorPalette.SCARLET));
                        tagFactory.tag("tangerine", Tag.styling(ColorPalette.TANGERINE));
                        tagFactory.tag("warm_orange", Tag.styling(ColorPalette.WARM_ORANGE));
                        tagFactory.tag("fire_orange", Tag.styling(ColorPalette.FIRE_ORANGE));
                        tagFactory.tag("dark_cherry", Tag.styling(ColorPalette.DARK_CHERRY));
                        tagFactory.tag("orange", Tag.styling(ColorPalette.ORANGE));

                        // yellows & golds
                        tagFactory.tag("yellow", Tag.styling(ColorPalette.YELLOW));
                        tagFactory.tag("gold", Tag.styling(ColorPalette.GOLD));
                        tagFactory.tag("golden_yellow", Tag.styling(ColorPalette.GOLDEN_YELLOW));
                        tagFactory.tag("sun_glow", Tag.styling(ColorPalette.SUN_GLOW));
                        tagFactory.tag("honey_gold", Tag.styling(ColorPalette.HONEY_GOLD));
                        tagFactory.tag("warm_yellow", Tag.styling(ColorPalette.WARM_YELLOW));
                        tagFactory.tag("amber", Tag.styling(ColorPalette.AMBER));
                        tagFactory.tag("mustard", Tag.styling(ColorPalette.MUSTARD));
                        tagFactory.tag("dark_gold", Tag.styling(ColorPalette.DARK_GOLD));

                        // greens
                        tagFactory.tag("green", Tag.styling(ColorPalette.GREEN));
                        tagFactory.tag("dark_green", Tag.styling(ColorPalette.DARK_GREEN));
                        tagFactory.tag("light_green", Tag.styling(ColorPalette.LIGHT_GREEN));
                        tagFactory.tag("neon_green", Tag.styling(ColorPalette.NEON_GREEN));
                        tagFactory.tag("emerald", Tag.styling(ColorPalette.EMERALD));
                        tagFactory.tag("forest_green", Tag.styling(ColorPalette.FOREST_GREEN));
                        tagFactory.tag("mint", Tag.styling(ColorPalette.MINT));
                        tagFactory.tag("teal_green", Tag.styling(ColorPalette.TEAL_GREEN));
                        tagFactory.tag("jungle_green", Tag.styling(ColorPalette.JUNGLE_GREEN));
                        tagFactory.tag("moss", Tag.styling(ColorPalette.MOSS));

                        // blues & cyan's
                        tagFactory.tag("blue", Tag.styling(ColorPalette.BLUE));
                        tagFactory.tag("dark_blue", Tag.styling(ColorPalette.DARK_BLUE));
                        tagFactory.tag("highlight_blue", Tag.styling(ColorPalette.HIGHLIGHT_BLUE));
                        tagFactory.tag("highlight", Tag.styling(ColorPalette.HIGHLIGHT_BLUE));
                        tagFactory.tag("bright_blue", Tag.styling(ColorPalette.BRIGHT_BLUE));
                        tagFactory.tag("sky_blue", Tag.styling(ColorPalette.SKY_BLUE));
                        tagFactory.tag("ios_blue", Tag.styling(ColorPalette.IOS_BLUE));
                        tagFactory.tag("vibrant_cyan", Tag.styling(ColorPalette.VIBRANT_CYAN));
                        tagFactory.tag("aqua_glow", Tag.styling(ColorPalette.AQUA_GLOW));
                        tagFactory.tag("navy", Tag.styling(ColorPalette.NAVY));
                        tagFactory.tag("midnight_blue", Tag.styling(ColorPalette.MIDNIGHT_BLUE));

                        // purples & violets
                        tagFactory.tag("darkish_purple", Tag.styling(ColorPalette.DARKISH_PURPLE));
                        tagFactory.tag("light_purple", Tag.styling(ColorPalette.LIGHT_PURPLE));
                        tagFactory.tag("blurple", Tag.styling(ColorPalette.BLURPLE));
                        tagFactory.tag("neon_purple", Tag.styling(ColorPalette.NEON_PURPLE));
                        tagFactory.tag("royal_violet", Tag.styling(ColorPalette.ROYAL_VIOLET));
                        tagFactory.tag("deep_purple", Tag.styling(ColorPalette.DEEP_PURPLE));
                        tagFactory.tag("indigo", Tag.styling(ColorPalette.INDIGO));
                        tagFactory.tag("lavender", Tag.styling(ColorPalette.LAVENDER));
                        tagFactory.tag("electric_violet", Tag.styling(ColorPalette.ELECTRIC_VIOLET));
                        tagFactory.tag("dark_plum", Tag.styling(ColorPalette.DARK_PLUM));

                        // pinks & magenta's
                        tagFactory.tag("hot_pink", Tag.styling(ColorPalette.HOT_PINK));
                        tagFactory.tag("rose_pink", Tag.styling(ColorPalette.ROSE_PINK));
                        tagFactory.tag("bubblegum", Tag.styling(ColorPalette.BUBBLEGUM));
                        tagFactory.tag("deep_magenta", Tag.styling(ColorPalette.DEEP_MAGENTA));
                        tagFactory.tag("crimson_pink", Tag.styling(ColorPalette.CRIMSON_PINK));
                        tagFactory.tag("neon_fuchsia", Tag.styling(ColorPalette.NEON_FUCHSIA));
                        tagFactory.tag("wine_rose", Tag.styling(ColorPalette.WINE_ROSE));

                        // whites
                        tagFactory.tag("white", Tag.styling(ColorPalette.WHITE));
                        tagFactory.tag("off_white", Tag.styling(ColorPalette.OFF_WHITE));
                        tagFactory.tag("dark_off_white", Tag.styling(ColorPalette.DARK_OFF_WHITE));
                        tagFactory.tag("alabaster", Tag.styling(ColorPalette.ALABASTER));
                        tagFactory.tag("ivory", Tag.styling(ColorPalette.IVORY));

                        // grays
                        tagFactory.tag("light_gray", Tag.styling(ColorPalette.LIGHT_GRAY));
                        tagFactory.tag("soft_gray", Tag.styling(ColorPalette.SOFT_GRAY));
                        tagFactory.tag("silver", Tag.styling(ColorPalette.SILVER));
                        tagFactory.tag("medium_gray", Tag.styling(ColorPalette.MEDIUM_GRAY));
                        tagFactory.tag("slate_gray", Tag.styling(ColorPalette.SLATE_GRAY));
                        tagFactory.tag("charcoal", Tag.styling(ColorPalette.CHARCOAL));
                        tagFactory.tag("dark_gray", Tag.styling(ColorPalette.DARK_GRAY));
                        tagFactory.tag("graphite", Tag.styling(ColorPalette.GRAPHITE));

                        // blacks
                        tagFactory.tag("black", Tag.styling(ColorPalette.BLACK));
                        tagFactory.tag("rich_black", Tag.styling(ColorPalette.RICH_BLACK));
                        tagFactory.tag("jet_black", Tag.styling(ColorPalette.JET_BLACK));
                        tagFactory.tag("midnight_indigo", Tag.styling(ColorPalette.MIDNIGHT_INDIGO));
                    })
                    .build();

    private MiniMessage() {
    }

    public static net.kyori.adventure.text.minimessage.MiniMessage miniMessage() {
        return MINI_MESSAGE;
    }

    public static Component format(String text) {
        try {
            return FORMATTED_CACHE.get(text, () -> MINI_MESSAGE.deserialize(text));
        } catch (Exception exception) {
            throw new RuntimeException("Failed to deserialize MiniMessage text: " + text, exception);
        }
    }

    public static Component format(@NotNull String text, @NotNull TagResolver... tagResolvers) {
        return MINI_MESSAGE.deserialize(text, TagResolver.resolver(tagResolvers));
    }

    public static String stripFormatting(String component) {
        return MINI_MESSAGE.stripTags(component);
    }
}
