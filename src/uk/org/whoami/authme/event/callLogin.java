package uk.org.whoami.authme.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class callLogin {
    //This class is just used to make the code look a little nicer

    public static void callLogin(Player p) {
        final AuthLoginEvent event = new AuthLoginEvent(AuthLoginEvent.Reason.AuthemeLogin, p);
        Bukkit.getServer().getPluginManager().callEvent(event);
    }
}
