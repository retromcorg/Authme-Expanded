package uk.org.whoami.authme.listener;

import com.johnymuffin.beta.evolutioncore.event.PlayerEvolutionAuthEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import uk.org.whoami.authme.AuthMe;
import uk.org.whoami.authme.ConsoleLogger;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.event.callLogin;
import uk.org.whoami.authme.settings.Settings;

import static uk.org.whoami.authme.event.callLogin.callLogin;

public class AuthMeCustomListener extends CustomEventListener implements Listener {
    private AuthMe plugin;

    public AuthMeCustomListener(AuthMe plugin) {
        this.plugin = plugin;
    }


    @Override
    public void onCustomEvent(Event event) {
        if (event instanceof PlayerEvolutionAuthEvent) {
            if (event == null) {
                return;
            }

            //Lets see if the user is Authenticated
            Player player = ((PlayerEvolutionAuthEvent) event).getPlayer();
            if (!player.isOnline()) {
                return;
            }

            String playerName = player.getName().toLowerCase();
            if (((PlayerEvolutionAuthEvent) event).isPlayerAuthenticated()) {
                //Player is authenticated
                if (PlayerCache.getInstance().isAuthenticated(playerName)) {
                    //Player is already authenticated with Authme
                    return;
                }
                //Check if user is brand new
                if (!plugin.getAuthDatabase().isAuthAvailable(playerName)) {
                    //User needs to register a password to prevent other cracked users from taking the account
                    return;
                }

                //Lets authenticate the user
                PlayerAuth auth = plugin.getAuthDatabase().getAuth(playerName);
                PlayerCache.getInstance().addPlayer(auth);
                LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(playerName);
                //Remove from limbo if player is in Limbo
                if (limbo != null) {
                    player.getInventory().setContents(limbo.getInventory());
                    player.getInventory().setArmorContents(limbo.getArmour());
                    if (Settings.getInstance().isTeleportToSpawnEnabled()) {
                        player.teleport(limbo.getLoc());
                    }
                    plugin.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
                    LimboCache.getInstance().deleteLimboPlayer(playerName);


                }
                ConsoleLogger.info(player.getDisplayName() + " Automatically Authenticated With Evolution!");
                String msg = "&6You have been Authenticated &6with Beta Evolution";
                msg = msg.replaceAll("(&([a-f0-9]))", "\u00A7$2");
                player.sendMessage(msg);

                callLogin(((PlayerEvolutionAuthEvent) event).getPlayer(), callLogin.Reason.EvolutionAuth);


            }


        }
    }


}
