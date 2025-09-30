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
import org.bukkit.scheduler.BukkitScheduler;

import uk.org.whoami.authme.cache.auth.PlayerCache;

public class MessageTask implements Runnable {

    private final JavaPlugin plugin;
    private final String uuid;
    private final String msg;
    private final int interval;

    public MessageTask(JavaPlugin plugin, String uuid, String msg, int interval) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.msg = msg;
        this.interval = interval;
    }

    @Override
    public void run() {
        if (PlayerCache.getInstance().isAuthenticated(uuid)) {
            return;
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getUniqueId().toString().equals(uuid)) {
                player.sendMessage(msg);

                BukkitScheduler sched = plugin.getServer().getScheduler();
                sched.scheduleSyncDelayedTask(plugin, this, interval * 20);
            }
        }
    }
}
