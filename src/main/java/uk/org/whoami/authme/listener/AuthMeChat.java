package uk.org.whoami.authme.listener;

import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerListener;
import uk.org.whoami.authme.cache.auth.PlayerCache;

public class AuthMeChat extends PlayerListener {

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        //HOTFIX - Start
        if (PlayerCache.getInstance().isAuthenticated(event.getPlayer().getName().toLowerCase())) {
            return;
        }
        event.setCancelled(true);

    }

}
