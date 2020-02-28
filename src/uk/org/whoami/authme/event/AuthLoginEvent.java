package uk.org.whoami.authme.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class AuthLoginEvent extends Event implements Cancellable {
    Player rplayer;
    private callLogin.Reason reason;
    private boolean isCancelled = false;

    public AuthLoginEvent(callLogin.Reason r, Player player) {
        super("AuthmeLoginEvent");
        rplayer = player;
        reason = r;
    }
    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        isCancelled = b;
    }

    public Player getPlayer() {
        return rplayer;
    }
    public callLogin.Reason getReason(){
        return reason;
    }

}
