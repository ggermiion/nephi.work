package me.tsukat.tab;

import org.bukkit.Server;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class TsukatTabPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final String LOGO = "\uE001";
    private static final String FIRE = "\uE020";
    private static final long HOUR = 3_600_000L;

    private final Map<UUID, Streak> streaks = new ConcurrentHashMap<>();
    private final Properties stored = new Properties();
    private File streakFile;
    private long lastSave;

    private final List<Rank> ranks = List.of(
            new Rank("owner", RankType.STAFF, 100, "\uE002"),
            new Rank("observer", RankType.STAFF, 90, "\uE003"),
            new Rank("curator", RankType.STAFF, 80, "\uE004"),
            new Rank("moderator", RankType.STAFF, 70, "\uE005"),
            new Rank("helper", RankType.STAFF, 60, "\uE006"),
            new Rank("trainee", RankType.STAFF, 50, "\uE007"),
            new Rank("builder", RankType.STAFF, 40, "\uE008"),
            new Rank("content_creator", RankType.STAFF, 30, "\uE009"),
            new Rank("arx", RankType.DONATE, 50, "\uE00A"),
            new Rank("zyn", RankType.DONATE, 40, "\uE00B"),
            new Rank("vyrlok", RankType.DONATE, 30, "\uE00C"),
            new Rank("nejl", RankType.DONATE, 20, "\uE00D"),
            new Rank("lux", RankType.DONATE, 10, "\uE00E")
    );

    @Override public void onEnable() {
        saveDefaultConfig();
        streakFile = new File(getDataFolder(), "streaks.properties");
        loadStreaks();
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("tsukattab");
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        long now = System.currentTimeMillis();
        for (Player p : getServer().getOnlinePlayers()) beginSession(p, now);
        getServer().getScheduler().runTaskTimer(this, this::refreshSafe, 20L, Math.max(10, getConfig().getInt("refresh-ticks", 40)));
        getLogger().info("TsukatTab 2.0 started: external RP mode, skin heads preserved, streaks enabled.");
    }

    @Override public void onDisable() {
        long now = System.currentTimeMillis();
        for (Player p : getServer().getOnlinePlayers()) endSession(p, now);
        saveStreaks();
        getServer().getScheduler().cancelTasks(this);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { beginSession(e.getPlayer(), System.currentTimeMillis()); refreshSafe(); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { endSession(e.getPlayer(), System.currentTimeMillis()); saveStreaks(); }

    private void beginSession(Player p, long now) {
        Streak s = loadRecord(p.getUniqueId());
        if (s.lastSeen > 0 && now - s.lastSeen > graceMillis()) s.activeMillis = 0;
        s.lastTick = now; s.lastSeen = now; streaks.put(p.getUniqueId(), s);
    }
    private void endSession(Player p, long now) {
        Streak s = streaks.computeIfAbsent(p.getUniqueId(), this::loadRecord);
        accrue(s, now); s.lastSeen = now; s.lastTick = 0; storeRecord(p.getUniqueId(), s);
    }
    private void accrue(Streak s, long now) {
        if (s.lastTick <= 0) { s.lastTick = now; return; }
        long delta = now - s.lastTick;
        if (delta > 0 && delta < HOUR) s.activeMillis += delta;
        s.lastTick = now; s.lastSeen = now;
    }
    private void refreshSafe() { try { refresh(); } catch (Throwable t) { getLogger().log(Level.WARNING, "TAB refresh failed", t); } }
    private void refresh() {
        long now = System.currentTimeMillis();
        List<Player> players = new ArrayList<>(getServer().getOnlinePlayers());
        for (Player p : players) accrue(streaks.computeIfAbsent(p.getUniqueId(), this::loadRecord), now);
        players.sort(Comparator.comparingInt(this::sortScore).reversed().thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int order = 1;
        for (Player p : players) {
            try { p.playerListName(displayName(p)); } catch (Throwable ignored) {}
            try { p.setPlayerListOrder(order++); } catch (Throwable ignored) {}
        }
        for (Player viewer : players) try { viewer.sendPlayerListHeaderAndFooter(header(viewer), footer(viewer)); } catch (Throwable ignored) {}
        if (now - lastSave > 300_000L) { saveStreaks(); lastSave = now; }
    }
    private Component displayName(Player p) {
        Set<String> groups = groups(p);
        Rank staff = highest(groups, RankType.STAFF), donate = highest(groups, RankType.DONATE);
        Component out = Component.empty();
        if (staff != null) out = out.append(glyph(staff.glyph)).append(Component.text(" "));
        if (donate != null) out = out.append(glyph(donate.glyph)).append(Component.text(" "));
        out = out.append(Component.text(cleanName(p.getDisplayName(), p.getName())));
        int days = streakDays(p.getUniqueId());
        if (days > 0 && getConfig().getBoolean("streak.enabled", true)) out = out.append(Component.text(" ")).append(glyph(FIRE)).append(Component.text(" " + days + "д"));
        return out;
    }
    private Component header(Player p) {
        int online = getServer().getOnlinePlayers().size(), max = getServer().getMaxPlayers();
        return Component.empty().append(glyph(LOGO)).append(Component.text("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\nДобро пожаловать, " + p.getName() + "!\nОнлайн: " + online + "/" + max));
    }
    private Component footer(Player p) {
        int days = streakDays(p.getUniqueId());
        Component out = Component.text("\n");
        if (days > 0) out = out.append(glyph(FIRE)).append(Component.text(" " + days + "д | "));
        return out.append(Component.text("Пинг: " + safePing(p) + " ms\ntsukats.fun"));
    }
    private Component glyph(String raw) { return Component.text(raw).font(Key.key("tsukat:tab")); }
    private int safePing(Player p) { try { return p.getPing(); } catch (Throwable t) { return 0; } }
    private int sortScore(Player p) {
        Set<String> groups = groups(p);
        Rank staff = highest(groups, RankType.STAFF), donate = highest(groups, RankType.DONATE);
        return (staff == null ? 0 : 1000 + staff.priority) + (donate == null ? 0 : donate.priority);
    }
    private Rank highest(Set<String> groups, RankType type) {
        Rank best = null;
        for (Rank r : ranks) if (r.type == type && groups.contains(r.group) && (best == null || r.priority > best.priority)) best = r;
        return best;
    }
    private Set<String> groups(Player p) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            Class<?> lpClass = Class.forName("net.luckperms.api.LuckPerms");
            Object lp = getServer().getServicesManager().load((Class) lpClass);
            if (lp != null) {
                Object user = invoke(invoke(lp, "getUserManager"), "getUser", p.getUniqueId());
                if (user != null) {
                    Object pg = invoke(user, "getPrimaryGroup");
                    if (pg != null) out.add(String.valueOf(pg).toLowerCase(Locale.ROOT));
                    Object nodes = invoke(user, "getNodes");
                    if (nodes instanceof Iterable<?> it) for (Object node : it) {
                        Object key = invoke(node, "getKey");
                        if (key != null) {
                            String k = String.valueOf(key).toLowerCase(Locale.ROOT);
                            if (k.startsWith("group.")) out.add(k.substring(6));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        for (Rank r : ranks) try { if (p.hasPermission("group." + r.group)) out.add(r.group); } catch (Throwable ignored) {}
        return out;
    }
    private static Object invoke(Object target, String name, Object... args) throws Exception {
        if (target == null) return null;
        for (Method m : target.getClass().getMethods()) if (m.getName().equals(name) && m.getParameterCount() == args.length) {
            try { return m.invoke(target, args); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }
    private String cleanName(String display, String fallback) {
        if (display == null || display.isBlank()) return fallback;
        String s = display.replace('\n',' ').replace('\r',' ').replaceAll("(?i)§[0-9A-FK-ORX]", "").trim();
        return s.length() > 48 ? s.substring(0,48) : s;
    }
    private long graceMillis() { return Math.max(1,getConfig().getInt("streak.offline-grace-hours",12))*HOUR; }
    private long hoursPerDayMillis() { return Math.max(1,getConfig().getInt("streak.hours-per-day",24))*HOUR; }
    private int streakDays(UUID id) { Streak s=streaks.get(id); return s==null?0:(int)Math.min(999,s.activeMillis/hoursPerDayMillis()); }
    private void loadStreaks() {
        try { if(!getDataFolder().exists()) getDataFolder().mkdirs(); if(streakFile.isFile()) try(Reader r=Files.newBufferedReader(streakFile.toPath(),StandardCharsets.UTF_8)){stored.load(r);} }
        catch(Throwable t){getLogger().warning("Could not load streaks.properties: "+t.getMessage());}
    }
    private Streak loadRecord(UUID id) {
        String[] a=stored.getProperty(id.toString(),"0,0").split(",",-1);
        try{return new Streak(Long.parseLong(a[0]),a.length>1?Long.parseLong(a[1]):0L,0L);}catch(Throwable ignored){return new Streak(0,0,0);}
    }
    private void storeRecord(UUID id,Streak s){stored.setProperty(id.toString(),s.activeMillis+","+s.lastSeen);}
    private synchronized void saveStreaks() {
        try { if(!getDataFolder().exists())getDataFolder().mkdirs(); for(Map.Entry<UUID,Streak> e:streaks.entrySet())storeRecord(e.getKey(),e.getValue()); try(Writer w=Files.newBufferedWriter(streakFile.toPath(),StandardCharsets.UTF_8)){stored.store(w,"TsukatTab play streaks: activeMillis,lastSeenEpochMs");} }
        catch(Throwable t){getLogger().warning("Could not save streaks.properties: "+t.getMessage());}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("info")){sender.sendMessage("§dTsukatTab §f2.0.0 §8| §7RP: ResourcePackManager §8| §7streak grace: §f"+getConfig().getInt("streak.offline-grace-hours",12)+"h");return true;}
        if(!sender.hasPermission("tsukattab.admin")){sender.sendMessage("§cНет прав.");return true;}
        if(args[0].equalsIgnoreCase("reload")){reloadConfig();refreshSafe();sender.sendMessage("§aTsukatTab перезагружен.");return true;}
        if(args[0].equalsIgnoreCase("refresh")){refreshSafe();sender.sendMessage("§aTAB обновлён.");return true;}
        if(args[0].equalsIgnoreCase("streakreset")&&args.length>=2){Player p=getServer().getPlayer(args[1]);if(p==null){sender.sendMessage("§cИгрок не найден.");return true;}Streak s=new Streak(0,System.currentTimeMillis(),System.currentTimeMillis());streaks.put(p.getUniqueId(),s);storeRecord(p.getUniqueId(),s);saveStreaks();refreshSafe();sender.sendMessage("§aОгонёк игрока "+p.getName()+" сброшен.");return true;}
        sender.sendMessage("§7/tsukattab <info|reload|refresh|streakreset игрок>");return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(args.length==1)return List.of("info","reload","refresh","streakreset").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();return List.of();}
    private enum RankType{STAFF,DONATE}
    private record Rank(String group,RankType type,int priority,String glyph){}
    private static final class Streak{long activeMillis,lastSeen,lastTick;Streak(long a,long s,long t){activeMillis=a;lastSeen=s;lastTick=t;}}
}
