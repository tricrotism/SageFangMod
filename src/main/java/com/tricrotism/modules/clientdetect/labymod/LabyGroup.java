package com.tricrotism.modules.clientdetect.labymod;

import java.util.HashMap;
import java.util.Map;

/**
 * Static lookup for LabyMod rank groups: the id, display name and badge color
 * delivered per user by {@code PacketUserBadge} (the rank byte is a group id;
 * see {@code LabyConnectSession.handleUserBadge}).
 *
 * <p>LabyMod resolves these ids at runtime from {@code laby.net/api/v3/groups}.
 * That table is small and stable, so it is mirrored here verbatim rather than
 * fetched. Group {@code 0} (default) carries no badge and is not in the map.
 */
public record LabyGroup(int id, String name, int color) {

    private static final Map<Integer, LabyGroup> BY_ID = build();

    private static Map<Integer, LabyGroup> build() {
        Map<Integer, LabyGroup> m = new HashMap<>();
        m.put(1, new LabyGroup(1, "Administrator", 0xE84C3C));
        m.put(2, new LabyGroup(2, "Developer", 0xE84C3C));
        m.put(3, new LabyGroup(3, "Sr Moderator", 0xE84C3C));
        m.put(4, new LabyGroup(4, "Moderator", 0xE84C3C));
        m.put(5, new LabyGroup(5, "Jr Moderator", 0xE84C3C));
        m.put(6, new LabyGroup(6, "Content", 0xE84C3C));
        m.put(7, new LabyGroup(7, "Translator", 0x06996F));
        m.put(8, new LabyGroup(8, "Partner", 0xBE00BE));
        m.put(9, new LabyGroup(9, "VIP", 0xBE00BE));
        m.put(10, new LabyGroup(10, "Laby+", 0xFFC700));
        m.put(11, new LabyGroup(11, "Cosmetic Creator", 0x06996F));
        m.put(12, new LabyGroup(12, "Jr Developer", 0xE84C3C));
        m.put(13, new LabyGroup(13, "Founder", 0xE84C3C));
        m.put(14, new LabyGroup(14, "Jr Content", 0xE84C3C));
        m.put(15, new LabyGroup(15, "Quality Assurance", 0x06996F));
        m.put(16, new LabyGroup(16, "World Moderator", 0xE84C3C));
        m.put(17, new LabyGroup(17, "World Builder", 0x06996F));
        m.put(18, new LabyGroup(18, "Sr World Builder", 0x06996F));
        m.put(19, new LabyGroup(19, "Shadow", 0xFFC700));
        return m;
    }

    /**
     * The group for an id, or {@code null} for the default group / any unknown id.
     */
    public static LabyGroup byId(int id) {
        return BY_ID.get(id);
    }

    /**
     * The badge color as an ImGui-friendly {0..1} RGB triple.
     */
    public float[] rgb() {
        return new float[]{((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f};
    }
}
