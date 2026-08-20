package com.tricrotism.modules.misc;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;

/**
 * Allows typing/pasting any character, control characters like NUL included,
 * in chat, signs, anvils and other text fields, by bypassing the vanilla chat
 * character filter. The bypass itself lives in {@code ChatTextLimitsMixin}, which
 * checks {@link #isActive()}. Ported from the Meteor addon's allow-invalid-chars.
 */
public final class AllowInvalidChars extends Module {

    public static final AllowInvalidChars instance = new AllowInvalidChars();

    private AllowInvalidChars() {
        super("allowinvalidchars", "Allow Invalid Chars",
            "Allow any character (incl. control chars) in text fields.", Category.UTILITY);
    }

}
