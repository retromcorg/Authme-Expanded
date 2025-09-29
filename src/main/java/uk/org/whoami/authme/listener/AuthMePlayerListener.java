/*
 * Copyright 2011 Sebastian Köhler <sebkoehler@whoami.org.uk>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.whoami.authme.listener;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.johnymuffin.beta.evolutioncore.EvolutionAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import uk.org.whoami.authme.AuthMe;
import uk.org.whoami.authme.ConsoleLogger;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.citizens.CitizensCommunicator;
import uk.org.whoami.authme.datasource.DataSource;
import uk.org.whoami.authme.event.AuthLoginEvent;
import uk.org.whoami.authme.event.callLogin;
import uk.org.whoami.authme.settings.Messages;
import uk.org.whoami.authme.settings.Settings;
import uk.org.whoami.authme.task.MessageTask;
import uk.org.whoami.authme.task.TimeoutTask;

import static uk.org.whoami.authme.event.callLogin.callLogin;

public class AuthMePlayerListener extends PlayerListener {

    private Settings settings = Settings.getInstance();
    private Messages m = Messages.getInstance();
    private AuthMe plugin;
    private DataSource data;

    public AuthMePlayerListener(AuthMe plugin, DataSource data) {
        this.plugin = plugin;
        this.data = data;
    }

    @Override
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!settings.isForcedRegistrationEnabled()) {
                return;
            }
        }

        String msg = event.getMessage();
        //WorldEdit GUI Shit
        if (msg.equalsIgnoreCase("/worldedit cui")) {
            return;
        }

        String cmd = msg.split(" ")[0];
        if (cmd.equalsIgnoreCase("/login") || cmd.equalsIgnoreCase("/register")) {
            return;
        }

        event.setMessage("/notloggedin");
        event.setCancelled(true);
    }

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        //HOTFIX - Start
        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        event.getRecipients().clear();
        event.setCancelled(true);
        player.sendMessage(m._("login_msg"));
        //HOTFIX - End

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (data.isAuthAvailable(name)) {
            player.sendMessage(m._("login_msg"));
        } else {
            if (!settings.isForcedRegistrationEnabled()) {
                return;
            }
            if (settings.isChatAllowed()) {
                return;
            }
            player.sendMessage(m._("reg_msg"));
        }
        event.setCancelled(true);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (data.isAuthAvailable(name)) {
            event.setTo(event.getFrom());
            return;
        }

        if (!settings.isForcedRegistrationEnabled()) {
            return;
        }

        if (!settings.isMovementAllowed()) {
            event.setTo(event.getFrom());
            return;
        }

        if (settings.getMovementRadius() == 0) {
            return;
        }

        int radius = settings.getMovementRadius();
        Location spawn = player.getWorld().getSpawnLocation();
        Location to = event.getTo();

        if (to.getX() > spawn.getX() + radius || to.getX() < spawn.getX() - radius ||
                to.getY() > spawn.getY() + radius || to.getY() < spawn.getY() - radius ||
                to.getZ() > spawn.getZ() + radius || to.getZ() < spawn.getZ() - radius) {
            event.setTo(event.getFrom());
        }
    }

    @Override
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != Result.ALLOWED || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();


        //TODO: A bunch of this stuff should only be run if Poseidon is present.

        //Beta EVO Staff Check
        if (plugin.isRunningPoseidon()) {
            if (settings.isKickNonAuthenticatedStaff()) {
                if (player.hasPermission("authme.evolutions.staff") || player.isOp()) {
                    if (!EvolutionAPI.isUserAuthenticatedInCache(event.getPlayer().getName(), event.getAddress().getHostAddress())) {
                        event.setKickMessage(Messages.getInstance()._("notifyUnauthenticatedStaff"));
                        event.setResult(Result.KICK_OTHER);
                        return;
                    }
                }
            }
            if (settings.isKickNonAuthenticatedEnabled() && !EvolutionAPI.isUserAuthenticatedInCache(event.getPlayer().getName(), event.getAddress().getHostAddress())) {
                //PLayers without BetaEVO should be kicked
                if (settings.isAllowRegisteredNonAuthenticatedBypassEnabled() && plugin.getAuthDatabase().isAuthAvailable(name)) {
                    ConsoleLogger.info(player.getName() + " Has been allowed to join as they are registered, and the registered bypass for BetaEVO is activated.");
                } else {
                    event.setKickMessage(Messages.getInstance()._("unauthenticatedKick"));
                    event.setResult(Result.KICK_OTHER);
                    return;
                }

            }

        }
        //Beta Evo Staff Check


        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        int min = settings.getMinNickLength();
        int max = settings.getMaxNickLength();
        String regex = settings.getNickRegex();

        if (name.length() > max || name.length() < min) {
            event.disallow(Result.KICK_OTHER, "Your nickname has the wrong length. MaxLen: " + max + ", MinLen: " + min);
            return;
        }
        if (!player.getName().matches(regex) || name.equals("Player")) {
            event.disallow(Result.KICK_OTHER, "Your nickname contains illegal characters. Allowed chars: " + regex);
            return;
        }

        //Remove doubles from premises
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (onlinePlayer.getName().equals(player.getName())) {
                event.disallow(Result.KICK_OTHER, m._("same_nick"));
                return;
            }
        }

        if (settings.isKickNonRegisteredEnabled()) {
            if (!data.isAuthAvailable(name)) {
                event.disallow(Result.KICK_OTHER, m._("reg_only"));
                return;
            }
        }
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        String ip = player.getAddress().getAddress().getHostAddress();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (data.isAuthAvailable(name)) {
            if (settings.isSessionsEnabled()) {
                PlayerAuth auth = data.getAuth(name);
                long timeout = settings.getSessionTimeout() * 60000;
                long lastLogin = auth.getLastLogin();
                long cur = new Date().getTime();

                if (auth.getNickname().equals(name) && auth.getIp().equals(ip) && (cur - lastLogin < timeout || timeout == 0)) {
                    //Login Event Start
                    final AuthLoginEvent loginEvent = new AuthLoginEvent(callLogin.Reason.AuthemeLogin, player);
                    Bukkit.getServer().getPluginManager().callEvent(loginEvent);
                    //Login Event End

                    if (!loginEvent.isCancelled()) {
                        PlayerCache.getInstance().addPlayer(auth);
                        player.sendMessage(m._("valid_session"));
                        callLogin(player, callLogin.Reason.AuthmeSession); // Run Event
                        return;
                    }

                }
            }
        } else {
            if (!settings.isForcedRegistrationEnabled()) {
                return;
            }
        }

        LimboCache.getInstance().addLimboPlayer(player);
        player.getInventory().setArmorContents(new ItemStack[0]);
        player.getInventory().setContents(new ItemStack[36]);
        if (settings.isTeleportToSpawnEnabled()) {
            player.teleport(player.getWorld().getSpawnLocation());
        }

        String msg = data.isAuthAvailable(name) ? m._("login_msg") : m._("reg_msg");
        int time = settings.getRegistrationTimeout() * 20;
        int msgInterval = settings.getWarnMessageInterval();
        BukkitScheduler sched = plugin.getServer().getScheduler();
        if (time != 0) {
            int id = sched.scheduleSyncDelayedTask(plugin, new TimeoutTask(plugin, name), time);
            LimboCache.getInstance().getLimboPlayer(name).setTimeoutTaskId(id);
        }
        sched.scheduleSyncDelayedTask(plugin, new MessageTask(plugin, name, msg, msgInterval), 15); //Wait 0.75 seconds before starting AuthMe Login Message Task
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        String name = player.getName().toLowerCase();
        if (LimboCache.getInstance().hasLimboPlayer(name)) {
            LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
            player.getInventory().setArmorContents(limbo.getArmour());
            player.getInventory().setContents(limbo.getInventory());
            player.teleport(limbo.getLoc());
            plugin.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
            LimboCache.getInstance().deleteLimboPlayer(name);
        }
        PlayerCache.getInstance().removePlayer(name);
    }

    @Override
    public void onPlayerKick(PlayerKickEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        String name = player.getName().toLowerCase();
        if (LimboCache.getInstance().hasLimboPlayer(name)) {
            LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
            player.getInventory().setArmorContents(limbo.getArmour());
            player.getInventory().setContents(limbo.getInventory());
            player.teleport(limbo.getLoc());
            plugin.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
            LimboCache.getInstance().deleteLimboPlayer(name);
        }
        PlayerCache.getInstance().removePlayer(name);
    }

    @Override
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!settings.isForcedRegistrationEnabled()) {
                return;
            }
        }

        event.setCancelled(true);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!settings.isForcedRegistrationEnabled()) {
                return;
            }
        }

        event.setCancelled(true);
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!settings.isForcedRegistrationEnabled()) {
                return;
            }
        }
        event.setCancelled(true);
    }

    @Override
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!settings.isForcedRegistrationEnabled()) {
                return;
            }
        }
        event.setCancelled(true);
    }

    @Override
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!settings.isForcedRegistrationEnabled()) {
                return;
            }
        }
        event.setCancelled(true);
    }
}
