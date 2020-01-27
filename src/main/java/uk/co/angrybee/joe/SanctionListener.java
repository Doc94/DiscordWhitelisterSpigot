package uk.co.angrybee.joe;

import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SanctionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(PlayerKickEvent event) {
        if(event.getReason().equalsIgnoreCase("You are banned from this server")) {
            return;
        }
        if(event.getReason().contains("§8[§2FurroProtect§8]§c")) {
            DiscordClient.sendFurroProtect(event.getPlayer().getName(),event.getReason().replace("§8[§2FurroProtect§8]§c ",""));
            return;
        }
        DiscordClient.sendKick(event.getPlayer().getName(),event.getReason());
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBan(PlayerQuitEvent event) {
        if(event.getPlayer().isBanned()) {
            BanEntry banEntry = event.getPlayer().getServer().getBanList(BanList.Type.NAME).getBanEntry(event.getPlayer().getName());

            if(banEntry != null) {
                DiscordClient.sendBan(banEntry.getTarget(),banEntry.getReason());
            }
        }
    }
}
