package uk.org.whoami.authme.listener;

import com.johnymuffin.beta.evolutioncore.event.PlayerEvolutionAuthEvent;
import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import uk.org.whoami.authme.AuthMe;
import uk.org.whoami.authme.cache.auth.PlayerCache;

public class AuthMeCustomListener extends CustomEventListener implements Listener {
    private AuthMe plugin;

    public AuthMeCustomListener(AuthMe plugin) {
        this.plugin = plugin;
    }


    @Override
    public void onCustomEvent(Event event) {
        if(event instanceof PlayerEvolutionAuthEvent) {
            if(event == null) {
                return;
            }

            //Lets see if the user is Authenticated
            if(((PlayerEvolutionAuthEvent) event).isPlayerAuthenticated()) {
                //Player is authenticated
                if(PlayerCache.getInstance().isAuthenticated(((PlayerEvolutionAuthEvent) event).getPlayer().getName().toLowerCase())) {
                    //Player is already authenticated with Authme
                    return;
                }
                //Lets authenticate the user




            } else {
                //Player isn't authenticated
            }




        }
    }


}
