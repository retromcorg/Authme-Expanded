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

package uk.org.whoami.authme.datasource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import uk.org.whoami.authme.ConsoleLogger;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.settings.Settings;

public class FileDataSource implements DataSource {

    /* file layout:
     *
     * UUID:USERNAME:HASHSUM:IP:LOGININMILLIESECONDS
     *
     */
    private File source;

    public FileDataSource() throws IOException {
        source = new File(Settings.AUTH_FILE);
        source.createNewFile();
    }

    @Override
    public synchronized boolean isAuthAvailable(String uuid) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(source));
            String line;
            while ((line = br.readLine()) != null) {
                String[] args = line.split(":");
                if (args.length > 0 && args[0].equals(uuid)) {
                    return true;
                }
            }
        } catch (FileNotFoundException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } catch (IOException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                }
            }
        }
        return false;
    }

    @Override
    public synchronized boolean saveAuth(PlayerAuth auth) {
        if (isAuthAvailable(auth.getUuid())) {
            return false;
        }

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(source, true));
            bw.write(formatAuth(auth) + "\n");
        } catch (IOException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException ex) {
                }
            }
        }
        return true;
    }

    @Override
    public synchronized boolean updatePassword(PlayerAuth auth) {
        if (!isAuthAvailable(auth.getUuid())) {
            return false;
        }

        PlayerAuth currentAuth = null;

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(source));
            String line = "";
            while ((line = br.readLine()) != null) {
                String[] args = line.split(":");
                if (args.length > 0 && args[0].equals(auth.getUuid())) {
                    currentAuth = parseAuth(args);
                    break;
                }
            }
        } catch (FileNotFoundException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } catch (IOException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                }
            }
        }

        if (currentAuth == null) {
            return false;
        }

        PlayerAuth updated = new PlayerAuth(currentAuth.getUuid(), currentAuth.getUsername(), auth.getHash(), currentAuth.getIp(), currentAuth.getLastLogin());
        removeAuth(auth.getUuid());
        saveAuth(updated);
        return true;
    }

    @Override
    public boolean updateSession(PlayerAuth auth) {
        if (!isAuthAvailable(auth.getUuid())) {
            return false;
        }

        PlayerAuth currentAuth = null;

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(source));
            String line = "";
            while ((line = br.readLine()) != null) {
                String[] args = line.split(":");
                if (args.length > 0 && args[0].equals(auth.getUuid())) {
                    currentAuth = parseAuth(args);
                    break;
                }
            }
        } catch (FileNotFoundException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } catch (IOException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                }
            }
        }

        if (currentAuth == null) {
            return false;
        }

        PlayerAuth updated = new PlayerAuth(currentAuth.getUuid(), auth.getUsername(), currentAuth.getHash(), auth.getIp(), auth.getLastLogin());
        removeAuth(auth.getUuid());
        saveAuth(updated);
        return true;
    }

    @Override
    public int purgeDatabase(long until) {
        BufferedReader br = null;
        BufferedWriter bw = null;
        ArrayList<String> lines = new ArrayList<String>();
        int cleared = 0;

        try {
            br = new BufferedReader(new FileReader(source));
            String line;
            while ((line = br.readLine()) != null) {
                String[] args = line.split(":");
                PlayerAuth auth = parseAuth(args);
                if (auth != null && auth.getLastLogin() >= until) {
                    lines.add(formatAuth(auth));
                    continue;
                }
                cleared++;
            }

            bw = new BufferedWriter(new FileWriter(source));
            for (String l : lines) {
                bw.write(l + "\n");
            }
        } catch (FileNotFoundException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return cleared;
        } catch (IOException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return cleared;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                }
            }
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException ex) {
                }
            }
        }
        return cleared;
    }

    @Override
    public synchronized boolean removeAuth(String uuid) {
        if (!isAuthAvailable(uuid)) {
            return false;
        }

        BufferedReader br = null;
        BufferedWriter bw = null;
        ArrayList<String> lines = new ArrayList<String>();
        try {
            br = new BufferedReader(new FileReader(source));
            String line;
            while ((line = br.readLine()) != null) {
                String[] args = line.split(":");
                PlayerAuth auth = parseAuth(args);
                if (auth != null && !auth.getUuid().equals(uuid)) {
                    lines.add(formatAuth(auth));
                }
            }

            bw = new BufferedWriter(new FileWriter(source));
            for (String l : lines) {
                bw.write(l + "\n");
            }
        } catch (FileNotFoundException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } catch (IOException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                }
            }
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException ex) {
                }
            }
        }
        return true;
    }

    @Override
    public synchronized PlayerAuth getAuth(String uuid) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(source));
            String line;
            while ((line = br.readLine()) != null) {
                String[] args = line.split(":");
                if (args.length > 0 && args[0].equals(uuid)) {
                    PlayerAuth auth = parseAuth(args);
                    if (auth != null) {
                        return auth;
                    }
                }
            }
        } catch (FileNotFoundException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return null;
        } catch (IOException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return null;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                }
            }
        }
        return null;
    }

    @Override
    public synchronized void close() {
    }

    @Override
    public void reload() {
    }

    private PlayerAuth parseAuth(String[] args) {
        if (args.length < 3) {
            return null;
        }

        String uuid = args[0];
        String username = args[1];
        String hash = args[2];
        String ip = "198.18.0.1";
        if (args.length > 3 && !args[3].isEmpty()) {
            ip = args[3];
        }

        long lastLogin = 0;
        if (args.length > 4) {
            try {
                lastLogin = Long.parseLong(args[4]);
            } catch (NumberFormatException ex) {
                lastLogin = 0;
            }
        }

        return new PlayerAuth(uuid, username, hash, ip, lastLogin);
    }

    private String formatAuth(PlayerAuth auth) {
        return auth.getUuid() + ":" + auth.getUsername() + ":" + auth.getHash() + ":" + auth.getIp() + ":" + auth.getLastLogin();
    }
}
