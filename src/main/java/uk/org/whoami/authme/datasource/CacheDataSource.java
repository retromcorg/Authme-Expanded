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

import java.util.HashMap;

import uk.org.whoami.authme.cache.auth.PlayerAuth;

public class CacheDataSource implements DataSource {

    private DataSource source;
    private final HashMap<String, PlayerAuth> cache = new HashMap<String, PlayerAuth>();

    public CacheDataSource(DataSource source) {
        this.source = source;
    }

    @Override
    public synchronized boolean isAuthAvailable(String uuid) {
        return cache.containsKey(uuid) ? true : source.isAuthAvailable(uuid);
    }

    @Override
    public synchronized PlayerAuth getAuth(String uuid) {
        if (cache.containsKey(uuid)) {
            return cache.get(uuid);
        } else {
            PlayerAuth auth = source.getAuth(uuid);
            if (auth != null) {
                cache.put(uuid, auth);
            }
            return auth;
        }
    }

    @Override
    public synchronized boolean saveAuth(PlayerAuth auth) {
        if (source.saveAuth(auth)) {
            cache.put(auth.getUuid(), auth);
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean updatePassword(PlayerAuth auth) {
        if (source.updatePassword(auth)) {
            cache.get(auth.getUuid()).setHash(auth.getHash());
            return true;
        }
        return false;
    }

    @Override
    public boolean updateSession(PlayerAuth auth) {
        if (source.updateSession(auth)) {
            PlayerAuth cached = cache.get(auth.getUuid());
            if (cached != null) {
                cached.setUsername(auth.getUsername());
                cached.setIp(auth.getIp());
                cached.setLastLogin(auth.getLastLogin());
            }
            return true;
        }
        return false;
    }

    @Override
    public int purgeDatabase(long until) {
        int cleared = source.purgeDatabase(until);

        if (cleared > 0) {
            HashMap<String, PlayerAuth> copy = new HashMap<String, PlayerAuth>(cache);
            for (PlayerAuth auth : copy.values()) {
                if (auth.getLastLogin() < until) {
                    cache.remove(auth.getUuid());
                }
            }
        }
        return cleared;
    }

    @Override
    public synchronized boolean removeAuth(String uuid) {
        if (source.removeAuth(uuid)) {
            cache.remove(uuid);
            return true;
        }
        return false;
    }

    @Override
    public synchronized void close() {
        source.close();
    }

    @Override
    public void reload() {
        cache.clear();
    }
}
