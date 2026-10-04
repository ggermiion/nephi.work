package fun.tsukat.windwing;

import org.bukkit.plugin.java.JavaPlugin;

public final class TsukatWindwingPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        try { WindwingFeature.bootstrap(this); }
        catch (Throwable t) { WindwingFeature.logSevere(this, "Shadow Windwing bootstrap failed", t); }
    }

    @Override
    public void onDisable() {
        try { WindwingFeature.shutdown(this); }
        catch (Throwable t) { WindwingFeature.logSevere(this, "Shadow Windwing shutdown failed", t); }
    }
}
