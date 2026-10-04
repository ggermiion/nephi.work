package fun.tsukat.windwing;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * TsukatWindwing 2.1 movement core.
 * Uses Bukkit/Paper reflectively so this class can be compiled without a server API jar.
 * The creature is a single ItemDisplay and moves only via Entity#setVelocity — never by teleport.
 */
public final class WindwingFeature {
    private static volatile Object plugin;
    private static volatile Object display;
    private static volatile Object world;
    private static final Object[] FRAME_ITEMS = new Object[8];
    private static boolean runtimeEnabled = true;
    private static boolean autoSpawnAllowed = true;
    private static long ticks;
    private static int frame;
    private static double heading;
    private static double targetX;
    private static double targetZ;
    private static int targetEdge = -1;
    private static long nextAtmosphereTick;
    private static Object currentTicketChunk;
    private static Object aheadTicketChunk;
    private static Object commandProxy;
    private static Object tabProxy;

    private WindwingFeature() {}

    public static void bootstrap(Object p) throws Exception {
        plugin = p;
        runtimeEnabled = boolOpt("windwing.enabled", true);
        autoSpawnAllowed = true;
        buildFrameItems();
        cleanupStaleDisplays();
        registerCommand();
        Object server = call(p, "getServer");
        Object scheduler = call(server, "getScheduler");
        call(scheduler, "runTaskTimer", p, (Runnable) WindwingFeature::tickSafe, 20L, 1L);
        logInfo("[windwing] Smooth single-creature flight enabled. Movement uses velocity only; no teleport movement.");
    }

    public static void shutdown(Object p) {
        try { removeDisplay(); } catch (Throwable ignored) {}
        releaseTickets();
        commandProxy = null;
        tabProxy = null;
        plugin = null;
    }

    public static void logSevere(Object p, String msg, Throwable t) {
        try {
            Object logger = call(p, "getLogger");
            Method m = logger.getClass().getMethod("log", Level.class, String.class, Throwable.class);
            m.invoke(logger, Level.SEVERE, "[TsukatWindwing] " + msg, t);
        } catch (Throwable ignored) {
            System.err.println("[TsukatWindwing] " + msg + ": " + t);
        }
    }

    private static void tickSafe() {
        try { tick(); }
        catch (Throwable t) { if (plugin != null) logSevere(plugin, "tick failed", t); }
    }

    private static void tick() throws Exception {
        ticks++;
        if (!runtimeEnabled) {
            if (display != null) removeDisplay();
            return;
        }

        Object desiredWorld = configuredWorld();
        if (desiredWorld == null) return;

        if (display == null || !isValid(display) || world == null || !sameWorld(world, desiredWorld)) {
            removeDisplay();
            if (autoSpawnAllowed) spawnSingle(desiredWorld);
        }
        if (display == null) return;

        updateFlight();

        if (ticks % 3 == 0) {
            frame = (frame + 1) % FRAME_ITEMS.length;
            call(display, "setItemStack", FRAME_ITEMS[frame]);
        }
        if (ticks % 20 == 0) updateChunkTickets();
        if (ticks >= nextAtmosphereTick) {
            atmosphere();
            nextAtmosphereTick = ticks + 45 + ThreadLocalRandom.current().nextInt(55);
        }
    }

    private static void spawnSingle(Object w) throws Exception {
        world = w;
        PatrolArea area = patrolArea(w);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        int side = r.nextInt(4);
        double x, z;
        if (side == 0) { x = area.cx - area.half; z = area.cz + r.nextDouble(-area.half * .75, area.half * .75); }
        else if (side == 1) { x = area.cx + area.half; z = area.cz + r.nextDouble(-area.half * .75, area.half * .75); }
        else if (side == 2) { x = area.cx + r.nextDouble(-area.half * .75, area.half * .75); z = area.cz - area.half; }
        else { x = area.cx + r.nextDouble(-area.half * .75, area.half * .75); z = area.cz + area.half; }

        double y = doubleOpt("windwing.flight-altitude", 150.0);
        Object loc = newLocation(w, x, y, z);
        Object type = enumValue("org.bukkit.entity.EntityType", "ITEM_DISPLAY");
        Object d = call(w, "spawnEntity", loc, type);
        display = d;

        invokeIfPresent(d, "setPersistent", false);
        invokeIfPresent(d, "setInvulnerable", true);
        invokeIfPresent(d, "setSilent", true);
        invokeIfPresent(d, "setGravity", false);
        invokeIfPresent(d, "setViewRange", (float) clamp(doubleOpt("windwing.view-range", 10.0), 2.0, 32.0));
        invokeIfPresent(d, "setShadowRadius", 0f);
        invokeIfPresent(d, "setShadowStrength", 0f);
        invokeIfPresent(d, "setInterpolationDuration", 3);
        invokeIfPresent(d, "addScoreboardTag", "tsukat_windwing");
        trySetDisplayTransform(d);
        call(d, "setItemStack", FRAME_ITEMS[0]);

        heading = Math.atan2(area.cz - z, area.cx - x);
        targetEdge = side;
        chooseNextBorderTarget(area, x, z, true);
        setVelocity(d, 0, 0, 0);
        updateChunkTickets();
        logInfo("[windwing] Spawned the single Windwing at Y=" + Math.round(y) + ".");
    }

    private static void updateFlight() throws Exception {
        Object loc = call(display, "getLocation");
        double x = num(call(loc, "getX"));
        double y = num(call(loc, "getY"));
        double z = num(call(loc, "getZ"));
        PatrolArea area = patrolArea(world);

        double dx = targetX - x;
        double dz = targetZ - z;
        double dist = Math.hypot(dx, dz);
        if (dist < clamp(area.size * 0.075, 20.0, 35.0)) {
            chooseNextBorderTarget(area, x, z, false);
            dx = targetX - x;
            dz = targetZ - z;
        }

        double soft = Math.max(10.0, area.half * 0.08);
        if (x < area.cx - area.half + soft || x > area.cx + area.half - soft ||
            z < area.cz - area.half + soft || z > area.cz + area.half - soft) {
            double inwardX = area.cx - x;
            double inwardZ = area.cz - z;
            double inwardLen = Math.max(0.001, Math.hypot(inwardX, inwardZ));
            dx += (inwardX / inwardLen) * 70.0;
            dz += (inwardZ / inwardLen) * 70.0;
        }

        double desired = Math.atan2(dz, dx);
        double delta = normalizeAngle(desired - heading);
        double maxTurn = Math.toRadians(clamp(doubleOpt("windwing.max-turn-degrees-per-tick", 0.85), 0.15, 3.0));
        heading += clamp(delta, -maxTurn, maxTurn);

        double wander = Math.sin((ticks + 271) * 0.0065) * Math.toRadians(0.11);
        heading += wander;

        double baseSpeed = clamp(doubleOpt("windwing.flight-speed-per-tick", 0.22), 0.05, 0.8);
        double turnSlow = 1.0 - Math.min(0.33, Math.abs(delta) / Math.PI * 0.38);
        double breathing = 0.96 + 0.04 * Math.sin(ticks * 0.021);
        double speed = baseSpeed * turnSlow * breathing;

        double variance = clamp(doubleOpt("windwing.altitude-variance", 7.0), 0.0, 25.0);
        double baseY = doubleOpt("windwing.flight-altitude", 150.0);
        double desiredY = baseY + Math.sin(ticks * 0.008) * variance + Math.sin(ticks * 0.0021) * variance * 0.35;
        double vy = clamp((desiredY - y) * 0.018, -0.085, 0.085);

        double vx = Math.cos(heading) * speed;
        double vz = Math.sin(heading) * speed;
        setVelocity(display, vx, vy, vz);

        float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
        float pitch = (float) clamp(-vy * 70.0, -6.0, 6.0);
        invokeIfPresent(display, "setRotation", yaw, pitch);
    }

    private static void chooseNextBorderTarget(PatrolArea area, double x, double z, boolean first) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int next;
        if (first) next = (targetEdge + 2) & 3;
        else {
            int choice = r.nextInt(100);
            next = choice < 65 ? ((targetEdge + 2) & 3) : ((targetEdge + (r.nextBoolean() ? 1 : 3)) & 3);
            if (next == targetEdge) next = (targetEdge + 2) & 3;
        }
        targetEdge = next;
        double spread = area.half * 0.82;
        if (next == 0) { targetX = area.cx - area.half; targetZ = area.cz + r.nextDouble(-spread, spread); }
        else if (next == 1) { targetX = area.cx + area.half; targetZ = area.cz + r.nextDouble(-spread, spread); }
        else if (next == 2) { targetX = area.cx + r.nextDouble(-spread, spread); targetZ = area.cz - area.half; }
        else { targetX = area.cx + r.nextDouble(-spread, spread); targetZ = area.cz + area.half; }
    }

    private static void setVelocity(Object entity, double x, double y, double z) throws Exception {
        ClassLoader cl = plugin.getClass().getClassLoader();
        Class<?> vc = Class.forName("org.bukkit.util.Vector", true, cl);
        Object v = construct(vc, x, y, z);
        call(entity, "setVelocity", v);
    }

    private static void buildFrameItems() throws Exception {
        for (int i = 0; i < FRAME_ITEMS.length; i++) FRAME_ITEMS[i] = createModelItem("windwing_frame_" + i);
    }

    private static Object createModelItem(String key) throws Exception {
        Object paper = enumValue("org.bukkit.Material", "PAPER");
        ClassLoader cl = plugin.getClass().getClassLoader();
        Class<?> itemStack = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
        Object item;
        try { item = construct(itemStack, paper); }
        catch (Throwable ex) { item = callStatic(itemStack, "of", paper); }
        Object meta = call(item, "getItemMeta");
        Class<?> nk = Class.forName("org.bukkit.NamespacedKey", true, cl);
        Object namespaced = construct(nk, plugin, key);
        call(meta, "setItemModel", namespaced);
        invokeIfPresent(meta, "setEnchantmentGlintOverride", Boolean.FALSE);
        call(item, "setItemMeta", meta);
        return item;
    }

    private static void trySetDisplayTransform(Object d) {
        try {
            Object fixed = enumValue("org.bukkit.entity.ItemDisplay$ItemDisplayTransform", "FIXED");
            invokeIfPresent(d, "setItemDisplayTransform", fixed);
        } catch (Throwable ignored) {}
        try {
            ClassLoader cl = plugin.getClass().getClassLoader();
            Class<?> v3 = Class.forName("org.joml.Vector3f", true, cl);
            Class<?> q4 = Class.forName("org.joml.Quaternionf", true, cl);
            float scale = (float) clamp(doubleOpt("windwing.model-scale", 40.0), 8.0, 80.0);
            Object translation = construct(v3, 0f, -0.10f, 0f);
            Object left = construct(q4);
            Object scaleV = construct(v3, scale, scale, scale);
            Object right = construct(q4);
            Class<?> tc = Class.forName("org.bukkit.util.Transformation", true, cl);
            Object transform = construct(tc, translation, left, scaleV, right);
            call(d, "setTransformation", transform);
        } catch (Throwable t) { logWarn("display transform: " + rootMessage(t)); }
        try {
            ClassLoader cl = plugin.getClass().getClassLoader();
            Class<?> bc = Class.forName("org.bukkit.entity.Display$Brightness", true, cl);
            Object brightness = construct(bc, 12, 12);
            invokeIfPresent(d, "setBrightness", brightness);
        } catch (Throwable ignored) {}
        invokeIfPresent(d, "setDisplayWidth", 52f);
        invokeIfPresent(d, "setDisplayHeight", 28f);
        try { invokeIfPresent(d, "setBillboard", enumValue("org.bukkit.entity.Display$Billboard", "FIXED")); }
        catch (Throwable ignored) {}
    }

    private static PatrolArea patrolArea(Object w) throws Exception {
        double fallbackSize = clamp(doubleOpt("windwing.fallback-area-size", 10000.0), 120.0, 50000.0);
        double cx = doubleOpt("windwing.fallback-center-x", 0.0);
        double cz = doubleOpt("windwing.fallback-center-z", 0.0);
        double size = fallbackSize;
        if (boolOpt("windwing.use-world-border", true)) {
            try {
                Object border = call(w, "getWorldBorder");
                double bs = num(call(border, "getSize"));
                double max = doubleOpt("windwing.max-auto-border-size", 20000.0);
                if (bs >= 100.0 && bs <= max) {
                    Object c = call(border, "getCenter");
                    cx = num(call(c, "getX"));
                    cz = num(call(c, "getZ"));
                    size = bs;
                }
            } catch (Throwable ignored) {}
        }
        double margin = clamp(doubleOpt("windwing.border-margin", 12.0), 2.0, size * 0.2);
        return new PatrolArea(cx, cz, size, Math.max(30.0, size * 0.5 - margin));
    }

    private static void atmosphere() {
        try {
            Object loc = call(display, "getLocation");
            double radius = clamp(doubleOpt("windwing.atmosphere-horizontal-radius", 86.0), 16.0, 160.0);
            for (Object p : onlinePlayers()) {
                try {
                    if (!sameWorld(call(p, "getWorld"), world)) continue;
                    Object pl = call(p, "getLocation");
                    double dx = num(call(pl, "getX")) - num(call(loc, "getX"));
                    double dz = num(call(pl, "getZ")) - num(call(loc, "getZ"));
                    if (dx*dx + dz*dz > radius*radius) continue;
                    int r = ThreadLocalRandom.current().nextInt(100);
                    String sound = r < 62 ? "tsukatwindwing:windwing.wing" : (r < 87 ? "tsukatwindwing:windwing.hush" : "tsukatwindwing:windwing.cry");
                    playCustom(p, loc, sound, r < 62 ? 0.55f : 0.72f, r < 62 ? 0.72f : 0.90f);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static void playCustom(Object player, Object loc, String sound, float volume, float pitch) {
        try { call(player, "playSound", loc, sound, volume, pitch); }
        catch (Throwable ignored) {}
    }

    private static void updateChunkTickets() {
        try {
            if (display == null || world == null) { releaseTickets(); return; }
            Object loc = call(display, "getLocation");
            int bx = (int)Math.floor(num(call(loc, "getX")));
            int bz = (int)Math.floor(num(call(loc, "getZ")));
            int cx = bx >> 4, cz = bz >> 4;
            int ax = ((int)Math.floor(bx + Math.cos(heading) * 28.0)) >> 4;
            int az = ((int)Math.floor(bz + Math.sin(heading) * 28.0)) >> 4;
            Object c1 = call(world, "getChunkAt", cx, cz);
            Object c2 = call(world, "getChunkAt", ax, az);
            if (currentTicketChunk != c1) {
                if (currentTicketChunk != null) invokeIfPresent(currentTicketChunk, "removePluginChunkTicket", plugin);
                currentTicketChunk = c1;
                invokeIfPresent(c1, "addPluginChunkTicket", plugin);
            }
            if (aheadTicketChunk != c2) {
                if (aheadTicketChunk != null && aheadTicketChunk != currentTicketChunk) invokeIfPresent(aheadTicketChunk, "removePluginChunkTicket", plugin);
                aheadTicketChunk = c2;
                if (c2 != c1) invokeIfPresent(c2, "addPluginChunkTicket", plugin);
            }
        } catch (Throwable ignored) {}
    }

    private static void releaseTickets() {
        try { if (currentTicketChunk != null && plugin != null) invokeIfPresent(currentTicketChunk, "removePluginChunkTicket", plugin); } catch (Throwable ignored) {}
        try { if (aheadTicketChunk != null && aheadTicketChunk != currentTicketChunk && plugin != null) invokeIfPresent(aheadTicketChunk, "removePluginChunkTicket", plugin); } catch (Throwable ignored) {}
        currentTicketChunk = null;
        aheadTicketChunk = null;
    }

    private static void removeDisplay() {
        releaseTickets();
        Object d = display;
        display = null;
        world = null;
        if (d != null) try { invokeIfPresent(d, "remove"); } catch (Throwable ignored) {}
    }

    private static void cleanupStaleDisplays() {
        try {
            Object server = call(plugin, "getServer");
            Object worlds = call(server, "getWorlds");
            if (!(worlds instanceof Iterable<?> it)) return;
            for (Object w : it) {
                Object ents = call(w, "getEntities");
                if (!(ents instanceof Iterable<?> ei)) continue;
                for (Object e : ei) {
                    try {
                        Object tags = call(e, "getScoreboardTags");
                        if (tags instanceof Set<?> s && s.contains("tsukat_windwing")) invokeIfPresent(e, "remove");
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static Object configuredWorld() {
        try {
            Object server = call(plugin, "getServer");
            String name = strOpt("windwing.world", "world");
            Object w = call(server, "getWorld", name);
            if (w != null) return w;
            Object worlds = call(server, "getWorlds");
            if (worlds instanceof List<?> l && !l.isEmpty()) return l.get(0);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Collection<?> onlinePlayers() {
        try {
            Object server = call(plugin, "getServer");
            Object c = call(server, "getOnlinePlayers");
            return c instanceof Collection<?> cc ? cc : List.of();
        } catch (Throwable t) { return List.of(); }
    }

    private static void registerCommand() throws Exception {
        Object cmd = call(plugin, "getCommand", "windwing");
        if (cmd == null) return;
        ClassLoader cl = plugin.getClass().getClassLoader();
        Class<?> ce = Class.forName("org.bukkit.command.CommandExecutor", true, cl);
        Class<?> tc = Class.forName("org.bukkit.command.TabCompleter", true, cl);
        commandProxy = Proxy.newProxyInstance(cl, new Class<?>[]{ce}, (proxy, method, args) -> {
            if (method.getName().equals("onCommand")) return onCommand(args);
            return defaultValue(method.getReturnType());
        });
        tabProxy = Proxy.newProxyInstance(cl, new Class<?>[]{tc}, (proxy, method, args) -> {
            if (method.getName().equals("onTabComplete")) return onTab(args);
            return defaultValue(method.getReturnType());
        });
        call(cmd, "setExecutor", commandProxy);
        call(cmd, "setTabCompleter", tabProxy);
    }

    private static Object onCommand(Object[] args) {
        Object sender = args != null && args.length > 0 ? args[0] : null;
        String[] a = args != null && args.length > 3 && args[3] instanceof String[] ss ? ss : new String[0];
        try {
            if (!hasPermission(sender, "tsukatwindwing.admin")) { send(sender, "§cНет права tsukatwindwing.admin"); return true; }
            String sub = a.length == 0 ? "status" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "spawn" -> {
                    autoSpawnAllowed = true;
                    Object w = configuredWorld();
                    if (w == null) send(sender, "§cМир для ветрокрыла не найден.");
                    else {
                        if (display == null || !isValid(display)) { removeDisplay(); spawnSingle(w); send(sender, "§8[§4TSUKAT§8] §7Теневой ветрокрыл создан. Он один на весь мир."); }
                        else send(sender, "§8[§4TSUKAT§8] §7Ветрокрыл уже существует — второй создан не будет.");
                    }
                }
                case "clear" -> { autoSpawnAllowed = false; removeDisplay(); send(sender, "§7Ветрокрыл удалён. Вернуть его: §f/windwing spawn"); }
                case "toggle" -> {
                    runtimeEnabled = !runtimeEnabled;
                    try { Object cfg = call(plugin, "getConfig"); call(cfg, "set", "windwing.enabled", runtimeEnabled); call(plugin, "saveConfig"); } catch (Throwable ignored) {}
                    if (!runtimeEnabled) { autoSpawnAllowed = false; removeDisplay(); } else { autoSpawnAllowed = true; }
                    send(sender, "§7TsukatWindwing: " + (runtimeEnabled ? "§aON" : "§cOFF"));
                }
                case "pack" -> send(sender, "§7RP TsukatWindwing теперь загружается только через ResourcePackManager.");
                default -> send(sender, statusLine());
            }
        } catch (Throwable t) {
            send(sender, "§cОшибка TsukatWindwing: " + rootMessage(t));
            if (plugin != null) logSevere(plugin, "command failed", t);
        }
        return true;
    }

    private static Object onTab(Object[] args) {
        String[] a = args != null && args.length > 3 && args[3] instanceof String[] ss ? ss : new String[0];
        if (a.length <= 1) {
            String p = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : List.of("spawn","clear","status","toggle")) if (s.startsWith(p)) out.add(s);
            return out;
        }
        return List.of();
    }

    private static String statusLine() {
        try {
            PatrolArea a = world != null ? patrolArea(world) : null;
            String area = a == null ? "?" : Math.round(a.size) + "×" + Math.round(a.size);
            return "§8[§4TSUKAT§8] §7Ветрокрыл: " + (display != null && isValid(display) ? "§a1/1" : "§c0/1") + " §8| §7движение: §aплавное (velocity) §8| §7Y≈" + Math.round(doubleOpt("windwing.flight-altitude",150)) + " §8| §7зона " + area;
        } catch (Throwable t) { return "§7TsukatWindwing status unavailable"; }
    }

    private static boolean isValid(Object entity) {
        if (entity == null) return false;
        try {
            Object dead = call(entity, "isDead");
            Object valid = call(entity, "isValid");
            return !bool(dead) && bool(valid);
        } catch (Throwable t) { return false; }
    }

    private static boolean sameWorld(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        try { return Objects.equals(call(a,"getUID"), call(b,"getUID")); }
        catch (Throwable t) { return a.equals(b); }
    }

    private static Object newLocation(Object w, double x, double y, double z) throws Exception {
        Class<?> c = Class.forName("org.bukkit.Location", true, plugin.getClass().getClassLoader());
        return construct(c, w, x, y, z);
    }

    private static boolean hasPermission(Object sender, String perm) {
        try { return bool(call(sender, "hasPermission", perm)); }
        catch (Throwable t) { return true; }
    }

    private static void send(Object sender, String s) {
        if (sender == null) return;
        try { call(sender, "sendMessage", s); } catch (Throwable ignored) {}
    }

    private static double doubleOpt(String path, double def) {
        try { return ((Number)call(call(plugin,"getConfig"),"getDouble",path,def)).doubleValue(); } catch(Throwable t){ return def; }
    }
    private static boolean boolOpt(String path, boolean def) {
        try { return bool(call(call(plugin,"getConfig"),"getBoolean",path,def)); } catch(Throwable t){ return def; }
    }
    private static String strOpt(String path, String def) {
        try { Object v = call(call(plugin,"getConfig"),"getString",path,def); return v == null ? def : String.valueOf(v); } catch(Throwable t){ return def; }
    }

    private static void logInfo(String s) { try { call(call(plugin,"getLogger"),"info",s); } catch(Throwable ignored){} }
    private static void logWarn(String s) { try { call(call(plugin,"getLogger"),"warning",s); } catch(Throwable ignored){} }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object enumValue(String cls, String name) throws Exception {
        Class<?> c = Class.forName(cls, true, plugin.getClass().getClassLoader());
        return Enum.valueOf((Class<? extends Enum>)c.asSubclass(Enum.class), name);
    }

    private static Object construct(Class<?> c, Object... args) throws Exception {
        for (Constructor<?> k : c.getConstructors()) if (compatible(k.getParameterTypes(),args)) return k.newInstance(adaptArgs(k.getParameterTypes(), args));
        for (Constructor<?> k : c.getDeclaredConstructors()) if (compatible(k.getParameterTypes(),args)) { k.setAccessible(true); return k.newInstance(adaptArgs(k.getParameterTypes(), args)); }
        throw new NoSuchMethodException("constructor " + c.getName()+Arrays.toString(argTypes(args)));
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        if (target == null) throw new NullPointerException("target for " + name);
        Method m = findMethod(target.getClass(), name, args, false);
        if (m == null) throw new NoSuchMethodException(target.getClass().getName()+"."+name+Arrays.toString(argTypes(args)));
        try { return m.invoke(target, adaptArgs(m.getParameterTypes(), args)); }
        catch (InvocationTargetException e) { Throwable c=e.getCause(); if(c instanceof Exception ex) throw ex; if(c instanceof Error er) throw er; throw e; }
    }

    private static Object callStatic(Class<?> c, String name, Object... args) throws Exception {
        Method m = findMethod(c,name,args,true);
        if (m == null) throw new NoSuchMethodException(c.getName()+"."+name);
        try { return m.invoke(null, adaptArgs(m.getParameterTypes(), args)); }
        catch (InvocationTargetException e) { Throwable x=e.getCause(); if(x instanceof Exception ex) throw ex; if(x instanceof Error er) throw er; throw e; }
    }

    private static boolean invokeIfPresent(Object target, String name, Object... args) {
        try { Method m=findMethod(target.getClass(),name,args,false); if(m==null) return false; m.invoke(target, adaptArgs(m.getParameterTypes(), args)); return true; }
        catch(Throwable t){ return false; }
    }

    private static Method findMethod(Class<?> c, String name, Object[] args, boolean stat) {
        for (Method m : c.getMethods()) if (m.getName().equals(name) && Modifier.isStatic(m.getModifiers())==stat && compatible(m.getParameterTypes(),args)) return m;
        for (Class<?> k=c;k!=null;k=k.getSuperclass()) for (Method m:k.getDeclaredMethods()) if(m.getName().equals(name)&&Modifier.isStatic(m.getModifiers())==stat&&compatible(m.getParameterTypes(),args)){m.setAccessible(true);return m;}
        return null;
    }

    private static Object[] adaptArgs(Class<?>[] p, Object[] a) {
        Object[] out = new Object[a.length];
        for (int i = 0; i < a.length; i++) out[i] = adaptArg(p[i], a[i]);
        return out;
    }

    private static Object adaptArg(Class<?> p, Object a) {
        if (a == null || !p.isPrimitive()) return a;
        if (p == boolean.class) return (a instanceof Boolean b) ? b : a;
        if (p == char.class) return (a instanceof Character c) ? c : a;
        if (!(a instanceof Number n)) return a;
        if (p == byte.class) return n.byteValue();
        if (p == short.class) return n.shortValue();
        if (p == int.class) return n.intValue();
        if (p == long.class) return n.longValue();
        if (p == float.class) return n.floatValue();
        if (p == double.class) return n.doubleValue();
        return a;
    }

    private static boolean compatible(Class<?>[] p, Object[] a) {
        if (p.length != a.length) return false;
        for(int i=0;i<p.length;i++) if(!compatible(p[i],a[i])) return false;
        return true;
    }
    private static boolean compatible(Class<?> p, Object a) {
        if(a==null) return !p.isPrimitive();
        Class<?> c=a.getClass();
        if(p.isAssignableFrom(c)) return true;
        if(!p.isPrimitive()) return false;
        return (p==boolean.class&&c==Boolean.class)||(p==byte.class&&c==Byte.class)||(p==short.class&&(c==Short.class||c==Byte.class))||(p==int.class&&(c==Integer.class||c==Short.class||c==Byte.class))||(p==long.class&&Number.class.isAssignableFrom(c))||(p==float.class&&Number.class.isAssignableFrom(c))||(p==double.class&&Number.class.isAssignableFrom(c))||(p==char.class&&c==Character.class);
    }
    private static Class<?>[] argTypes(Object[] a){Class<?>[] r=new Class<?>[a.length];for(int i=0;i<a.length;i++)r[i]=a[i]==null?Object.class:a[i].getClass();return r;}
    private static Object defaultValue(Class<?> c){if(!c.isPrimitive())return null;if(c==boolean.class)return false;if(c==char.class)return ' ';if(c==byte.class)return (byte)0;if(c==short.class)return (short)0;if(c==int.class)return 0;if(c==long.class)return 0L;if(c==float.class)return 0f;if(c==double.class)return 0d;return null;}
    private static double num(Object o){return o instanceof Number n?n.doubleValue():0.0;}
    private static boolean bool(Object o){return o instanceof Boolean b&&b;}
    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
    private static double normalizeAngle(double a){while(a>Math.PI)a-=Math.PI*2;while(a<-Math.PI)a+=Math.PI*2;return a;}
    private static String rootMessage(Throwable t){Throwable r=t;while(r.getCause()!=null&&r.getCause()!=r)r=r.getCause();String m=r.getMessage();return r.getClass().getSimpleName()+(m==null?"":": "+m);}

    private record PatrolArea(double cx, double cz, double size, double half) {}
}
