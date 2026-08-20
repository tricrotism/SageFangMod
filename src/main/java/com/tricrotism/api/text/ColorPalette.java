package com.tricrotism.api.text;

import net.kyori.adventure.text.format.TextColor;

// these colors are from arkgroovy's codebase
public class ColorPalette {

    public static final TextColor ERROR = TextColor.color(220, 53, 69);
    public static final TextColor WARNING = TextColor.color(255, 193, 7);
    public static final TextColor INFO = TextColor.color(23, 162, 184);
    public static final TextColor SUCCESS = TextColor.color(40, 167, 69);

    // Reds and oranges
    public static final TextColor DARK_RED = TextColor.color(139, 0, 0);
    public static final TextColor RED = ERROR;
    public static final TextColor LIGHT_RED = TextColor.color(255, 102, 102);
    public static final TextColor CRIMSON = TextColor.color(230, 57, 70);
    public static final TextColor SCARLET = TextColor.color(215, 38, 56);
    public static final TextColor ORANGE = TextColor.color(255, 140, 0);
    public static final TextColor TANGERINE = TextColor.color(255, 107, 53);
    public static final TextColor WARM_ORANGE = TextColor.color(255, 149, 0);
    public static final TextColor FIRE_ORANGE = TextColor.color(242, 92, 5);
    public static final TextColor DARK_CHERRY = TextColor.color(178, 58, 72);

    // Yellows and golds
    public static final TextColor GOLD = TextColor.color(255, 196, 0);
    public static final TextColor YELLOW = TextColor.color(255, 235, 59);
    public static final TextColor GOLDEN_YELLOW = TextColor.color(255, 214, 10);
    public static final TextColor SUN_GLOW = TextColor.color(255, 221, 0);
    public static final TextColor HONEY_GOLD = TextColor.color(247, 179, 43);
    public static final TextColor WARM_YELLOW = TextColor.color(244, 211, 94);
    public static final TextColor AMBER = TextColor.color(255, 180, 0);
    public static final TextColor MUSTARD = TextColor.color(238, 198, 67);
    public static final TextColor DARK_GOLD = TextColor.color(201, 162, 39);

    // Greens
    public static final TextColor DARK_GREEN = TextColor.color(6, 64, 43);
    public static final TextColor GREEN = SUCCESS;
    public static final TextColor LIGHT_GREEN = TextColor.color(102, 187, 106);
    public static final TextColor NEON_GREEN = TextColor.color(52, 199, 89);
    public static final TextColor EMERALD = TextColor.color(46, 204, 113);
    public static final TextColor FOREST_GREEN = TextColor.color(39, 174, 96);
    public static final TextColor MINT = TextColor.color(107, 203, 119);
    public static final TextColor TEAL_GREEN = TextColor.color(0, 168, 120);
    public static final TextColor JUNGLE_GREEN = TextColor.color(0, 127, 95);
    public static final TextColor MOSS = TextColor.color(1, 77, 64);

    // Blues and cyans
    public static final TextColor DARK_BLUE = TextColor.color(36, 60, 110);
    public static final TextColor BLUE = INFO;
    public static final TextColor HIGHLIGHT_BLUE = TextColor.color(0, 191, 255);
    public static final TextColor BRIGHT_BLUE = TextColor.color(10, 132, 255);
    public static final TextColor SKY_BLUE = TextColor.color(29, 161, 242);
    public static final TextColor IOS_BLUE = TextColor.color(0, 122, 255);
    public static final TextColor VIBRANT_CYAN = TextColor.color(78, 168, 222);
    public static final TextColor AQUA_GLOW = TextColor.color(0, 207, 255);
    public static final TextColor NAVY = TextColor.color(0, 78, 137);
    public static final TextColor MIDNIGHT_BLUE = TextColor.color(2, 62, 125);

    // Purples and violets
    public static final TextColor DARKISH_PURPLE = TextColor.color(106, 27, 154);
    public static final TextColor LIGHT_PURPLE = TextColor.color(149, 117, 205);
    public static final TextColor BLURPLE = TextColor.color(69, 79, 191);
    public static final TextColor NEON_PURPLE = TextColor.color(191, 90, 242);
    public static final TextColor ROYAL_VIOLET = TextColor.color(157, 78, 221);
    public static final TextColor DEEP_PURPLE = TextColor.color(123, 44, 191);
    public static final TextColor INDIGO = TextColor.color(106, 76, 147);
    public static final TextColor LAVENDER = TextColor.color(199, 125, 255);
    public static final TextColor ELECTRIC_VIOLET = TextColor.color(147, 54, 253);
    public static final TextColor DARK_PLUM = TextColor.color(60, 9, 108);

    // Pinks and magentas
    public static final TextColor HOT_PINK = TextColor.color(255, 45, 85);
    public static final TextColor ROSE_PINK = TextColor.color(255, 111, 145);
    public static final TextColor BUBBLEGUM = TextColor.color(241, 91, 181);
    public static final TextColor DEEP_MAGENTA = TextColor.color(216, 17, 89);
    public static final TextColor CRIMSON_PINK = TextColor.color(233, 64, 87);
    public static final TextColor NEON_FUCHSIA = TextColor.color(255, 60, 172);
    public static final TextColor WINE_ROSE = TextColor.color(164, 19, 60);

    // Whites
    public static final TextColor DARK_OFF_WHITE = TextColor.color(189, 189, 189);
    public static final TextColor OFF_WHITE = TextColor.color(224, 224, 224);
    public static final TextColor ALABASTER = TextColor.color(250, 250, 250);
    public static final TextColor IVORY = TextColor.color(255, 253, 240);
    public static final TextColor WHITE = OFF_WHITE;

    // Grays
    public static final TextColor LIGHT_GRAY = TextColor.color(222, 226, 230);
    public static final TextColor SOFT_GRAY = TextColor.color(200, 200, 200);
    public static final TextColor SILVER = TextColor.color(192, 192, 192);
    public static final TextColor MEDIUM_GRAY = TextColor.color(160, 160, 160);
    public static final TextColor SLATE_GRAY = TextColor.color(112, 128, 144);
    public static final TextColor CHARCOAL = TextColor.color(73, 80, 87);
    public static final TextColor DARK_GRAY = TextColor.color(44, 47, 51);
    public static final TextColor GRAPHITE = TextColor.color(33, 37, 41);
    public static final TextColor NEUTRAL = TextColor.color(108, 117, 125);

    // Black
    public static final TextColor BLACK = TextColor.color(0, 0, 0);
    public static final TextColor RICH_BLACK = TextColor.color(28, 28, 30);
    public static final TextColor JET_BLACK = TextColor.color(20, 20, 20);
    public static final TextColor MIDNIGHT_INDIGO = TextColor.color(44, 44, 84);

    private ColorPalette() {
    }
}
