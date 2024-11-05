package org.wso2.carbon.common.policy.migration.util;

import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.registry.core.config.DataBaseConfiguration;
import org.wso2.carbon.registry.core.config.RegistryContext;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RegDBUtil {
    private static Map<String, DataSource> dataSources = new HashMap<>();

    public static void initialize() throws APIManagementException {

        RegistryContext regContext = RegistryContext.getBaseInstance();
        Iterator<String> dbConfigNames = regContext.getDBConfigNames();
        try {
            while (dbConfigNames.hasNext()) {
                String dbConfigName = dbConfigNames.next();
                if (!"wso2registry".equals(dbConfigName)) {
                    DataBaseConfiguration dbConfig = regContext.getDBConfig(dbConfigName);
                    String dataSourceName = dbConfig.getDataSourceName();
                    Context context = new InitialContext();
                    DataSource dataSource = (DataSource) context.lookup(dataSourceName);
                    dataSources.put(dataSourceName, dataSource);
                }
            }
        } catch (NamingException e) {
            throw new APIManagementException("Error while initializing registry data-sources", e);
        }
    }

    public static Map<String, DataSource> getDataSources() {
        return dataSources;
    }
}
