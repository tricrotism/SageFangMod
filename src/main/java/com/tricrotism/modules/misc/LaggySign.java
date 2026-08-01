package com.tricrotism.modules.misc;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;

import java.util.Random;

/**
 * Fills a sign with dense, expensive-to-render Unicode when the sign edit screen
 * opens and sends it immediately (then closes the screen). The send + close is
 * driven by {@code AbstractSignEditScreenMixin}, which reads this module. Ported
 * from the Meteor addon's laggy-sign.
 */
public final class LaggySign extends Module implements Menu {

    public static final LaggySign instance = new LaggySign();

    private static final int[] LAG_CHARS =
        ("䛌ᰄἓ컐욞⻶曘걜쑬忧絷ﺎ㿉배⎶폽㷠൒䍸힉浨鮷蜗叁训뼾綑嬅㙔㵅ᅿ❬調ꉂ濄綵罞簏啨顗य媕븐㱭뉖崏戭꺘頻铀牷朤㼞䘭䯂븦崙订匆쫤果ꀓ₶﷽癤ښ᱕흻飱ᩉ먏又﹉眏章녽좓₷뼞ꯖ僧釒푨㑃伧釴篗䌪ڏ涟군趑轞觎柟衶炯穳麨鷃㋜쯱鶾䩃")
            .codePoints().toArray();

    private final Random random = new Random();
    private int length;

    private LaggySign() {
        super("laggysign", "Laggy Sign", "Fill signs with lag characters and send them on open.", "World");
        length = Config.getInt(baseConfig + ".length", 90);
    }

    /**
     * Four lines of random lag characters, each {@link #length} code points long.
     */
    public String[] randomLines() {
        String[] lines = new String[4];
        for (int i = 0; i < 4; i++) lines[i] = randomLine();
        return lines;
    }

    private String randomLine() {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.appendCodePoint(LAG_CHARS[random.nextInt(LAG_CHARS.length)]);
        }
        return sb.toString();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##laggySignEnabled", isActive())) toggle();

        int[] len = {length};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Length##laggySignLen", len, 1, 1000)) {
            length = len[0];
            Config.setProperty(baseConfig + ".length", String.valueOf(length));
        }

        ImGui.end();
    }
}
