package com.johnymuffin.beta.authme.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class AuthLoginEvent extends Event implements Cancellable {
    Player rplayer;
    private Reason reason;
    private boolean isCancelled = false;

    public AuthLoginEvent(Reason reason, Player player) {
        super("AuthmeLogin");
        rplayer = player;
        System.out.println("Event For " + rplayer.getName());
    }
    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean b) {
        isCancelled = b;
    }

    public Player getPlayer() {
        return rplayer;
    }
    public Reason getReason(){
        return Reason.AuthemeLogin;
    }

    public enum Reason{
        AuthemeLogin
    }
}
