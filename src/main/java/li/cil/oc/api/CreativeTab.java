package li.cil.oc.api;

import net.minecraft.item.ItemGroup;

/**
 * Allows access to the creative tab used by OpenComputers.
 */
public final class CreativeTab {
    /**
     * The creative tab used by OpenComputers.
     * <p/>
     * Changed to the actual tab if OC is present. Preferably you do
     * <em>not</em> try to access this anyway when OpenComputers isn't
     * present (don't ship the API in your mod), so don't rely on this!
     */
    public static ItemGroup instance = ItemGroup.TAB_REDSTONE;

    private CreativeTab() {
    }
}
