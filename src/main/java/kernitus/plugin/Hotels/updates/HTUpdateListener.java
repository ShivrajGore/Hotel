package kernitus.plugin.Hotels.updates;

import kernitus.plugin.Hotels.HotelsMain;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;

public class HTUpdateListener implements Listener{

	private HotelsMain plugin;
	private final File pluginFile;

	public HTUpdateListener(HotelsMain plugin, File pluginFile){
		this.plugin = plugin;
		this.pluginFile = pluginFile;
	}


	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e){
		final Player p = e.getPlayer();
		if(p.hasPermission("hotels.*")){
			Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {

                HTUpdateChecker updateChecker = new HTUpdateChecker(plugin, pluginFile);

                // Checking for updates
                updateChecker.sendUpdateMessages(p);
            },20L);
		}
	}
}
