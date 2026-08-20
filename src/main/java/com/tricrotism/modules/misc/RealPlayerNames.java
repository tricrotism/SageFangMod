package com.tricrotism.modules.misc;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;

/**
 * Shows real account names above players' heads (and wherever a player's display
 * name is used) instead of nicked/team-decorated display names. The override
 * itself lives in {@code PlayerDisplayNameMixin}, which checks {@link #isActive()}.
 * Ported from the Meteor addon's real-player-names.
 */
public final class RealPlayerNames extends Module {

    public static final RealPlayerNames instance = new RealPlayerNames();

    private RealPlayerNames() {
        super("realplayernames", "Real Player Names", "Show real account names instead of display names.", Category.RENDER);
    }

}
