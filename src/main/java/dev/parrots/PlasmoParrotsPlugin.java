package dev.parrots;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;

import java.util.List;

@Addon(
        id = "plasmo_parrots",
        name = "PlasmoParrots",
        scope = AddonLoaderScope.SERVER,
        version = "1.0.0",
        authors = {"Kilo"}
)
public final class PlasmoParrotsPlugin extends JavaPlugin implements AddonInitializer, TabExecutor {
    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    private PluginSettings settings;
    private PlasmoVoiceBridge voiceBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = PluginSettings.load(getConfig());

        PhraseRepeater repeater = new PhraseRepeater(this);
        voiceBridge = new PlasmoVoiceBridge(this, repeater);
        repeater.setVoiceBridge(voiceBridge);

        PluginCommand command = getCommand("plasmoparrots");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        PlasmoVoiceServer.getAddonsLoader().load(this);

        Bukkit.getScheduler().runTaskTimer(this, voiceBridge::flushIdlePhrases, 20L, 10L);
        getLogger().info("PlasmoParrots enabled. Waiting for Plasmo Voice addon initialization.");
    }

    @Override
    public void onAddonInitialize() {
        if (voiceBridge == null) return;

        voiceBridge.enable(voiceServer);
    }

    @Override
    public void onAddonShutdown() {
        if (voiceBridge != null) voiceBridge.disable();
    }

    @Override
    public void onDisable() {
        if (voiceBridge != null) voiceBridge.disable();
        PlasmoVoiceServer.getAddonsLoader().unload(this);
    }

    PluginSettings settings() {
        return settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("plasmoparrots.reload")) {
                sender.sendMessage("You do not have permission to reload PlasmoParrots.");
                return true;
            }

            reloadConfig();
            settings = PluginSettings.load(getConfig());
            if (voiceBridge != null) voiceBridge.reload();
            sender.sendMessage("PlasmoParrots config reloaded.");
            sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("plasmoparrots.reload")) {
                sender.sendMessage("You do not have permission to change PlasmoParrots debug mode.");
                return true;
            }
            if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
                sender.sendMessage("Usage: /" + label + " debug <on|off>");
                return true;
            }

            boolean debug = args[1].equalsIgnoreCase("on");
            getConfig().set("debug", debug);
            saveConfig();
            settings = PluginSettings.load(getConfig());
            sender.sendMessage("PlasmoParrots debug " + (debug ? "enabled" : "disabled") + ".");
            return true;
        }

        sender.sendMessage("Usage: /" + label + " <status|reload|debug>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("status", "reload", "debug");
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) return List.of("on", "off");
        return List.of();
    }

    private void sendStatus(CommandSender sender) {
        boolean ready = voiceBridge != null && voiceBridge.isReady();
        int buffered = voiceBridge == null ? 0 : voiceBridge.bufferedPhrases();
        sender.sendMessage("PlasmoParrots status: " + (ready ? "ready" : "not ready"));
        sender.sendMessage("repeatChance=" + settings.repeatChance()
                + ", radius=" + settings.parrotRadius()
                + ", pitchFactor=" + settings.pitchFactor()
                + ", repeatWindow=" + settings.repeatDurationMinMillis() + "-" + settings.repeatDurationMaxMillis() + "ms"
                + ", buffered=" + buffered
                + ", debug=" + settings.debug());
    }
}
