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

package uk.org.whoami.authme.task;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.settings.Messages;

public class TimeoutTask implements Runnable {

    private final JavaPlugin plugin;
    private final String uuid;
    private final Messages m = Messages.getInstance();

    public TimeoutTask(JavaPlugin plugin, String uuid) {
        this.plugin = plugin;
        this.uuid = uuid;
    }

    @Override
    public void run() {
        if (PlayerCache.getInstance().isAuthenticated(uuid)) {
            return;
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getUniqueId().toString().equals(uuid)) {
                if (LimboCache.getInstance().hasLimboPlayer(uuid)) {
                    LimboPlayer inv = LimboCache.getInstance().getLimboPlayer(uuid);
                    player.getInventory().setArmorContents(inv.getArmour());
                    player.getInventory().setContents(inv.getInventory());
                    player.teleport(inv.getLoc());
                    LimboCache.getInstance().deleteLimboPlayer(uuid);
                }
                player.kickPlayer(m._("timeout"));
                break;
            }
        }
    }
}
