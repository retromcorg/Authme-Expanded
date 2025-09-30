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

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import uk.org.whoami.authme.ConsoleLogger;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.datasource.MiniConnectionPoolManager.TimeoutException;
import uk.org.whoami.authme.settings.Settings;

public class MySQLDataSource implements DataSource {

    private String host;
    private String port;
    private String username;
    private String password;
    private String database;
    private String tableName;
    private String columnUuid;
    private String columnUsername;
    private String columnPassword;
    private String columnIp;
    private String columnLastLogin;
    private MiniConnectionPoolManager conPool;

    public MySQLDataSource() throws ClassNotFoundException, SQLException {
        Settings s = Settings.getInstance();
        this.host = s.getMySQLHost();
        this.port = s.getMySQLPort();
        this.username = s.getMySQLUsername();
        this.password = s.getMySQLPassword();

        this.database = s.getMySQLDatabase();
        this.tableName = s.getMySQLTablename();
        this.columnUuid = s.getMySQLColumnUuid();
        this.columnUsername = s.getMySQLColumnUsername();
        this.columnPassword = s.getMySQLColumnPassword();
        this.columnIp = s.getMySQLColumnIp();
        this.columnLastLogin = s.getMySQLColumnLastLogin();

        connect();
        setup();
    }

    private synchronized void connect() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.jdbc.Driver");
        ConsoleLogger.info("MySQL driver loaded");
        MysqlConnectionPoolDataSource dataSource = new MysqlConnectionPoolDataSource();
        dataSource.setDatabaseName(database);
        dataSource.setServerName(host);
        dataSource.setPort(Integer.parseInt(port));
        dataSource.setUser(username);
        dataSource.setPassword(password);

        conPool = new MiniConnectionPoolManager(dataSource, 10);
        ConsoleLogger.info("Connection pool ready");
    }

    private synchronized void setup() throws SQLException {
        Connection con = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            con = conPool.getValidConnection();
            st = con.createStatement();
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "id INTEGER AUTO_INCREMENT,"
                    + columnUuid + " VARCHAR(36) NOT NULL,"
                    + columnUsername + " VARCHAR(255) NOT NULL,"
                    + columnPassword + " VARCHAR(255) NOT NULL,"
                    + columnIp + " VARCHAR(40) NOT NULL,"
                    + columnLastLogin + " BIGINT,"
                    + "CONSTRAINT table_const_prim PRIMARY KEY (id),"
                    + "UNIQUE (" + columnUuid + "));");

            rs = con.getMetaData().getColumns(null, null, tableName, columnUuid);
            if (!rs.next()) {
                st.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN "
                        + columnUuid + " VARCHAR(36) NOT NULL;");
            }
            close(rs);

            rs = con.getMetaData().getColumns(null, null, tableName, columnUsername);
            if (!rs.next()) {
                st.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN "
                        + columnUsername + " VARCHAR(255) NOT NULL;");
            }
            close(rs);

            rs = con.getMetaData().getColumns(null, null, tableName, columnIp);
            if (!rs.next()) {
                st.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN "
                        + columnIp + " VARCHAR(40) NOT NULL;");
            }
            rs.close();
            rs = con.getMetaData().getColumns(null, null, tableName, columnLastLogin);
            if (!rs.next()) {
                st.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN "
                        + columnLastLogin + " BIGINT;");
            }
            try {
                st.executeUpdate("ALTER TABLE " + tableName + " ADD UNIQUE (" + columnUuid + ");");
            } catch (SQLException ignore) {
            }
        } finally {
            close(rs);
            close(st);
            close(con);
        }
        ConsoleLogger.info("MySQL Setup finished");
    }

    @Override
    public synchronized boolean isAuthAvailable(String uuid) {
        Connection con = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            con = conPool.getValidConnection();
            pst = con.prepareStatement("SELECT * FROM " + tableName + " WHERE "
                    + columnUuid + "=?;");
            pst.setString(1, uuid);
            rs = pst.executeQuery();
            return rs.next();
        } catch (SQLException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } catch (TimeoutException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            close(rs);
            close(pst);
            close(con);
        }
    }

    @Override
    public synchronized PlayerAuth getAuth(String uuid) {
        Connection con = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        try {
            con = conPool.getValidConnection();
            pst = con.prepareStatement("SELECT * FROM " + tableName + " WHERE "
                    + columnUuid + "=?;");
            pst.setString(1, uuid);
            rs = pst.executeQuery();
            if (rs.next()) {
                if (rs.getString(columnIp).isEmpty()) {
                    return new PlayerAuth(rs.getString(columnUuid), rs.getString(columnUsername), rs.getString(columnPassword), "198.18.0.1", rs.getLong(columnLastLogin));
                } else {
                    return new PlayerAuth(rs.getString(columnUuid), rs.getString(columnUsername), rs.getString(columnPassword), rs.getString(columnIp), rs.getLong(columnLastLogin));
                }
            } else {
                return null;
            }
        } catch (SQLException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return null;
        } catch (TimeoutException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return null;
        } finally {
            close(rs);
            close(pst);
            close(con);
        }
    }

    @Override
    public synchronized boolean saveAuth(PlayerAuth auth) {
        Connection con = null;
        PreparedStatement pst = null;
        try {
            con = conPool.getValidConnection();
            pst = con.prepareStatement("INSERT INTO " + tableName + "(" + columnUuid + "," + columnUsername + "," + columnPassword + "," + columnIp + "," + columnLastLogin + ") VALUES (?,?,?,?,?);");
            pst.setString(1, auth.getUuid());
            pst.setString(2, auth.getUsername());
            pst.setString(3, auth.getHash());
            pst.setString(4, auth.getIp());
            pst.setLong(5, auth.getLastLogin());
            pst.executeUpdate();
        } catch (SQLException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } catch (TimeoutException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            close(pst);
            close(con);
        }
        return true;
    }

    @Override
    public synchronized boolean updatePassword(PlayerAuth auth) {
        Connection con = null;
        PreparedStatement pst = null;
        try {
            con = conPool.getValidConnection();
            pst = con.prepareStatement("UPDATE " + tableName + " SET " + columnPassword + "=? WHERE " + columnUuid + "=?;");
            pst.setString(1, auth.getHash());
            pst.setString(2, auth.getUuid());
            pst.executeUpdate();
        } catch (SQLException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } catch (TimeoutException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            close(pst);
            close(con);
        }
        return true;
    }

    @Override
    public boolean updateSession(PlayerAuth auth) {
        Connection con = null;
        PreparedStatement pst = null;
        try {
            con = conPool.getValidConnection();
            pst = con.prepareStatement("UPDATE " + tableName + " SET " + columnUsername + "=?, " + columnIp + "=?, " + columnLastLogin + "=? WHERE " + columnUuid + "=?;");
            pst.setString(1, auth.getUsername());
            pst.setString(2, auth.getIp());
            pst.setLong(3, auth.getLastLogin());
            pst.setString(4, auth.getUuid());
            pst.executeUpdate();
        } catch (SQLException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } catch (TimeoutException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            close(pst);
            close(con);
        }
        return true;
    }

    @Override
    public int purgeDatabase(long until) {
        Connection con = null;
        PreparedStatement pst = null;
        try {
            con = conPool.getValidConnection();
            pst = con.prepareStatement("DELETE FROM " + tableName + " WHERE " + columnLastLogin + "<?;");
            pst.setLong(1, until);
            return pst.executeUpdate();
        } catch (SQLException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return 0;
        } catch (TimeoutException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return 0;
        } finally {
            close(pst);
            close(con);
        }
    }

    @Override
    public synchronized boolean removeAuth(String uuid) {
        Connection con = null;
        PreparedStatement pst = null;
        try {
            con = conPool.getValidConnection();
            pst = con.prepareStatement("DELETE FROM " + tableName + " WHERE " + columnUuid + "=?;");
            pst.setString(1, uuid);
            pst.executeUpdate();
        } catch (SQLException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } catch (TimeoutException ex) {
            ConsoleLogger.showError(ex.getMessage());
            return false;
        } finally {
            close(pst);
            close(con);
        }
        return true;
    }

    @Override
    public synchronized void close() {
        try {
            conPool.dispose();
        } catch (SQLException ex) {
            ConsoleLogger.showError(ex.getMessage());
        }
    }

    @Override
    public void reload() {
    }

    private void close(Statement st) {
        if (st != null) {
            try {
                st.close();
            } catch (SQLException ex) {
                ConsoleLogger.showError(ex.getMessage());
            }
        }
    }

    private void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException ex) {
                ConsoleLogger.showError(ex.getMessage());
            }
        }
    }

    private void close(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException ex) {
                ConsoleLogger.showError(ex.getMessage());
            }
        }
    }
}
