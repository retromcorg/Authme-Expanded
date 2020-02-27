package uk.org.whoami.authme.listener;

import com.johnymuffin.beta.evolutioncore.event.PlayerEvolutionAuthEvent;
import com.johnymuffin.uuidcore.event.PlayerUUIDEvent;
import org.bukkit.Bukkit;
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
import uk.org.whoami.authme.event.AuthLoginEvent;
import uk.org.whoami.authme.event.callLogin;
import uk.org.whoami.authme.settings.Messages;
import uk.org.whoami.authme.settings.Settings;

import java.util.UUID;

import static com.johnymuffin.beta.evolutioncore.EvolutionAPI.isUserAuthenticatedInCache;
import static uk.org.whoami.authme.event.callLogin.callLogin;

public class AuthMeCustomListener extends CustomEventListener implements Listener {
    private AuthMe plugin;

    public AuthMeCustomListener(AuthMe plugin) {
        this.plugin = plugin;
    }

    public boolean isClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onCustomEvent(Event event) {

        if (plugin.isBetaEvolutionsEnabled() && (event instanceof PlayerEvolutionAuthEvent)) {
            if (event == null) {
                ConsoleLogger.showError("Received an event with a null value");
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

                //Check if we should authenticate user
                if (!Settings.getInstance().isAuthenticatedSkipLoginEnabled()) {
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
                player.sendMessage(Messages.getInstance()._("userAuthenticated"));
                callLogin(((PlayerEvolutionAuthEvent) event).getPlayer(), callLogin.Reason.EvolutionAuth);
                return;

            } else {
                //User isn't authenticated

                //Is kick staff if not authenticated enabled
                if (Settings.getInstance().isKickNonAuthenticatedStaff()) {
                    if (player.hasPermission("authme.evolutions.staff") || player.isOp()) {
                        player.kickPlayer(Messages.getInstance()._("notifyUnauthenticatedStaff"));
                        return;
                    }
                }

                //Kick non authenticated
                if (Settings.getInstance().isKickNonAuthenticatedEnabled()) {
                    //Kick non authenticated users
                    player.kickPlayer(Messages.getInstance()._("unauthenticatedKick"));
                    return;
                }
                //Is notify of Beta Evolutions turned on
                if (Settings.getInstance().isNotifyNonAuthenticatedEnabled()) {
                    player.sendMessage(Messages.getInstance()._("notifyUnauthenticated"));
                }


            }


        } else if (event instanceof AuthLoginEvent) {
            if (event == null || ((AuthLoginEvent) event).getPlayer() == null) {
                ConsoleLogger.showError("Received an event with a null value");
                return;
            }


            //Send user on registration Beta Evolution download event

            //Check if we already message all unauthenticated evolutions players
            if (Settings.getInstance().isNotifyNonAuthenticatedEnabled()) {
                return;
            }
            if (!Settings.getInstance().isNotifyNonAuthenticatedOnRegistrationEnabled()) {
                return;
            }


            //Is user registering
            if (((AuthLoginEvent) event).getReason() == callLogin.Reason.AuthmeRegister) {
                Player player = ((AuthLoginEvent) event).getPlayer();
                String ip = player.getAddress().getAddress().getHostAddress();
                //If user isn't authenticated using beta evolutions
                if (isClass("com.johnymuffin.beta.evolutioncore.EvolutionAPI") && !isUserAuthenticatedInCache(player.getName(), ip)) {
                    //Wait 3 seconds to ensure Beta Evolutions has responsed
                    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (player.isOnline()) {
                                player.sendMessage(Messages.getInstance()._("notifyUnauthenticated"));
                            }


                        }
                    }, 20 * 3L);

                }


            }


        } else if (plugin.isUUIDCoreEnabled() && event instanceof PlayerUUIDEvent) {
            if (((PlayerUUIDEvent) event).getPlayer() == null) {
                return;
            }
            //Handle UUID Core Event
            Player player = ((PlayerUUIDEvent) event).getPlayer();
            //Check if user is still online
            if (!((PlayerUUIDEvent) event).getPlayer().isOnline()) {
                Bukkit.getServer().getLogger().warning("AuthMe received a UUID event for player \"" + ((PlayerUUIDEvent) event).getPlayer().getName() + "\" when the user isn't online");
                return;
            }
            Boolean uuidStatus = ((PlayerUUIDEvent) event).getUUIDStatus();

            //If UUID Fails
            if (!uuidStatus) {
                if(Settings.getInstance().isKickOnFailedUUIDEnabled()) {
                    player.kickPlayer(Messages.getInstance()._("uuidFetchFailedKick"));
                    return;
                }
                if (Settings.getInstance().isMessageOnFailedUUIDEnabled()) {
                    player.sendMessage(Messages.getInstance()._("uuidFetchFailedMessage"));
                }
                return;
            }
            UUID playerUUID = ((PlayerUUIDEvent) event).getPlayerUUID();


        }
    }


}
