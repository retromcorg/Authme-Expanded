package uk.org.whoami.authme.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class callLogin {
    //This class is just used to make the code look a little nicer

    public static void callLogin(Player p, Reason r) {
        final AuthLoginEvent event = new AuthLoginEvent(r, p);
        Bukkit.getServer().getPluginManager().callEvent(event);
    }

    public enum Reason{
        AuthemeLogin,
        AuthmeRegister,
        AuthmeSession,
        EvolutionAuth
    }
}
