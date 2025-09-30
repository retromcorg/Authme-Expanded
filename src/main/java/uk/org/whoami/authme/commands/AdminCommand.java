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

package uk.org.whoami.authme.commands;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.projectposeidon.api.PoseidonUUID;
import com.projectposeidon.api.UUIDType;

import uk.org.whoami.authme.ConsoleLogger;
import uk.org.whoami.authme.AuthMe;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.datasource.DataSource;
import uk.org.whoami.authme.security.PasswordSecurity;
import uk.org.whoami.authme.settings.Messages;
import uk.org.whoami.authme.settings.Settings;

public class AdminCommand implements CommandExecutor {

    private static final String LEGACY_AUTH_FILE = Settings.PLUGIN_FOLDER + "/auths.db";

    private Messages m = Messages.getInstance();
    private Settings settings = Settings.getInstance();
    private DataSource database;
    private final AuthMe plugin;

    public AdminCommand(AuthMe plugin, DataSource database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmnd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /authme reload|register playername password|changepassword playername password|unregister playername|purge|betaevo|convertuuid");
            return true;
        }

        if (!sender.hasPermission("authme.admin." + args[0].toLowerCase())) {
            sender.sendMessage(m._("no_perm"));
            return true;
        }

        if (args[0].equalsIgnoreCase("purge")) {
            if (args.length != 2) {
                sender.sendMessage("Usage: /authme purge <DAYS>");
                return true;
            }

            try {
                long days = Long.parseLong(args[1]) * 86400000;
                long until = new Date().getTime() - days;

                sender.sendMessage("Deleted " + database.purgeDatabase(until) + " user accounts");

            } catch (NumberFormatException e) {
                sender.sendMessage("Usage: /authme purge <DAYS>");
                return true;
            }
        } else if (args[0].equalsIgnoreCase("reload")) {
            database.reload();
            settings.reload();
            m.reload();
            sender.sendMessage(m._("reload"));
        } else if (args[0].equalsIgnoreCase("betaevo")) {
            boolean betaEVO = settings.isKickNonAuthenticatedEnabled();

            if (betaEVO) {
                settings.setKickNonAuthenticatedEnabled(false);
                sender.sendMessage("BetaEVO mode disabled");
            } else {
                settings.setKickNonAuthenticatedEnabled(true);
                sender.sendMessage("BetaEVO mode enabled");
            }
            ConsoleLogger.info("BetaEVO mode " + (betaEVO ? "disabled" : "enabled") + " by " + sender.getName());
        } else if (args[0].equalsIgnoreCase("convertuuid")) {
            convertLegacyDatabase(sender);
        } else if (args[0].equalsIgnoreCase("register")) {
            if (args.length != 3) {
                sender.sendMessage("Usage: /authme register playername password");
                return true;
            }

            try {
                String name = args[1].toLowerCase();
                String uuid = getOfflineUuid(args[1]);
                String hash = PasswordSecurity.getHash(settings.getPasswordHash(), args[2]);

                if (database.isAuthAvailable(uuid)) {
                    sender.sendMessage(m._("user_regged"));
                    return true;
                }

                PlayerAuth auth = new PlayerAuth(uuid, name, hash, "198.18.0.1", 0);
                if (!database.saveAuth(auth)) {
                    sender.sendMessage(m._("error"));
                    return true;
                }
                sender.sendMessage(m._("registered"));
                ConsoleLogger.info(args[1] + " registered");
            } catch (NoSuchAlgorithmException ex) {
                ConsoleLogger.showError(ex.getMessage());
                sender.sendMessage(m._("error"));
            }
        } else if (args[0].equalsIgnoreCase("changepassword")) {
            if (args.length != 3) {
                sender.sendMessage("Usage: /authme changepassword playername newpassword");
                return true;
            }

            try {
                String name = args[1].toLowerCase();
                String uuid = getOfflineUuid(args[1]);
                String hash = PasswordSecurity.getHash(settings.getPasswordHash(), args[2]);

                PlayerAuth auth = null;
                if (PlayerCache.getInstance().isAuthenticated(uuid)) {
                    auth = PlayerCache.getInstance().getAuth(uuid);
                } else if (database.isAuthAvailable(uuid)) {
                    auth = database.getAuth(uuid);
                } else {
                    sender.sendMessage(m._("unknown_user"));
                    return true;
                }
                auth.setHash(hash);
                auth.setUsername(name);

                if (!database.updatePassword(auth)) {
                    sender.sendMessage(m._("error"));
                    return true;
                }

                sender.sendMessage("pwd_changed");
                ConsoleLogger.info(args[0] + "'s password changed");
            } catch (NoSuchAlgorithmException ex) {
                ConsoleLogger.showError(ex.getMessage());
                sender.sendMessage(m._("error"));
            }
        } else if (args[0].equalsIgnoreCase("unregister")) {
            if (args.length != 2) {
                sender.sendMessage("Usage: /authme unregister playername");
                return true;
            }

            String name = args[1].toLowerCase();
            String uuid = getOfflineUuid(args[1]);

            if (!database.removeAuth(uuid)) {
                sender.sendMessage(m._("error"));
                return true;
            }

            PlayerCache.getInstance().removePlayer(uuid);
            sender.sendMessage("unregistered");

            ConsoleLogger.info(args[1] + " unregistered");
        } else {
            sender.sendMessage("Usage: /authme reload|register playername password|changepassword playername password|unregister playername|purge|betaevo|convertuuid");
        }
        return true;
    }

    private void convertLegacyDatabase(CommandSender sender) {
        File legacyFile = new File(LEGACY_AUTH_FILE);
        if (!legacyFile.exists() || !legacyFile.isFile()) {
            sender.sendMessage("Legacy auths.db not found. No conversion performed.");
            return;
        }

        File targetFile = new File(Settings.AUTH_FILE);
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            sender.sendMessage("Unable to create AuthMe data directory for conversion.");
            return;
        }

        File backupFile = parent != null
                ? new File(parent, targetFile.getName() + ".bak")
                : new File(targetFile.getName() + ".bak");
        boolean backedUp = false;
        if (targetFile.exists() && targetFile.length() > 0) {
            if (backupFile.exists() && !backupFile.delete()) {
                sender.sendMessage("Unable to remove existing auths-uuid.db.bak backup. Aborting.");
                return;
            }
            if (!targetFile.renameTo(backupFile)) {
                sender.sendMessage("Unable to back up existing auths-uuid.db. Aborting.");
                return;
            }
            backedUp = true;
        }

        int converted = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(legacyFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile, false))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] values = line.split(":");
                if (values.length < 2) {
                    skipped++;
                    continue;
                }

                String originalName = values[0].trim();
                if (originalName.isEmpty()) {
                    skipped++;
                    continue;
                }

                String username = originalName.toLowerCase();
                String hash = values[1].trim();
                String ip = "198.18.0.1";
                if (values.length > 2 && !values[2].isEmpty()) {
                    ip = values[2].trim();
                }

                long lastLogin = 0;
                if (values.length > 3 && !values[3].isEmpty()) {
                    try {
                        lastLogin = Long.parseLong(values[3].trim());
                    } catch (NumberFormatException ex) {
                        lastLogin = 0;
                    }
                }

                UUID uuid = resolveUuid(originalName);
                if (uuid == null) {
                    ConsoleLogger.info("Skipping legacy record for '" + originalName + "' because no UUID could be resolved.");
                    skipped++;
                    continue;
                }

                writer.write(uuid.toString() + ":" + username + ":" + hash + ":" + ip + ":" + lastLogin);
                writer.newLine();
                converted++;
            }
        } catch (IOException ex) {
            ConsoleLogger.showError("Failed to convert legacy database: " + ex.getMessage());
            sender.sendMessage(m._("error"));
            return;
        }

        if (backedUp) {
            sender.sendMessage("Existing auths-uuid.db backed up to " + backupFile.getName());
        }

        sender.sendMessage("Converted " + converted + " accounts to auths-uuid.db" + (skipped > 0 ? " (" + skipped + " skipped)" : ""));
        if (skipped > 0) {
            sender.sendMessage("Check console for players that were skipped due to missing UUIDs.");
        }
    }

    private UUID resolveUuid(String username) {
        if (plugin != null && plugin.isRunningPoseidon()) {
            try {
                UUIDType uuidType = PoseidonUUID.getPlayerUUIDCacheStatus(username);
                if (uuidType == UUIDType.UNKNOWN) {
                    return null;
                } else if (uuidType == UUIDType.OFFLINE) {
                    return PoseidonUUID.getPlayerOfflineUUID(username);
                } else {
                    return PoseidonUUID.getPlayerMojangUUID(username);
                }
            } catch (Exception ex) {
                ConsoleLogger.showError("Unable to resolve UUID for '" + username + "': " + ex.getMessage());
                return null;
            }
        }

        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private String getOfflineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
