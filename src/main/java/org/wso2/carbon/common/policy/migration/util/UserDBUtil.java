package org.wso2.carbon.common.policy.migration.util;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.common.policy.migration.APIMigrationException;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.config.RealmConfigXMLProcessor;
import org.wso2.carbon.user.core.util.DatabaseUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class UserDBUtil {

    private static final Log log = LogFactory.getLog(UserDBUtil.class);
    private static volatile DataSource dataSource = null;

    /**
     * Initializes the data source
     *
     * @throws APIMigrationException if an error occurs while building realm configuration from file
     */
    public static void initialize() throws APIMigrationException {
        RealmConfiguration realmConfig;
        try {
            realmConfig = new RealmConfigXMLProcessor().buildRealmConfigurationFromFile();
            dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
        } catch (UserStoreException e) {
            throw new APIMigrationException("Error while building realm configuration from file", e);
        }
    }

    /**
     * Utility method to get a new database connection
     *
     * @return Connection
     * @throws APIMigrationException if failed to get Connection
     */
    public static Connection getConnection() throws APIMigrationException {
        try {
            if (dataSource != null) {
                return dataSource.getConnection();
            }
        } catch (SQLException e) {
            throw new APIMigrationException("Failed to get Connection.", e);
        }
        throw new APIMigrationException("Data source is not configured properly.");
    }
}
