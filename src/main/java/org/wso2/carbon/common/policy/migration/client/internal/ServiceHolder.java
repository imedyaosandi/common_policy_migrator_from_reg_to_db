/*
* Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.wso2.carbon.common.policy.migration.client.internal;

import org.wso2.carbon.apimgt.api.OrganizationResolver;
import org.wso2.carbon.apimgt.api.model.KeyManagerConnectorConfiguration;
import org.wso2.carbon.apimgt.impl.APIManagerConfigurationService;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.ArtifactSaver;
import org.wso2.carbon.apimgt.impl.importexport.ImportExportAPI;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.service.TenantRegistryLoader;
import org.wso2.carbon.registry.indexing.service.TenantIndexingLoader;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.ConfigurationContextService;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class ServiceHolder {
    //Registry Service which is used to get registry data.
    private static RegistryService registryService;

    private static UserRealm userRealm;

    //Realm Service which is used to get tenant data.
    private static RealmService realmService;

    private static ConfigurationContextService contextService;

    private static TenantIndexingLoader indexLoader;

    //Tenant registry loader which is used to load tenant registry
    private static TenantRegistryLoader tenantRegLoader;
    
    //APIM Configuration service to read api-manager.xml
    private static APIManagerConfigurationService amConfigurationService;

    private static ApplicationManagementService applicationManagementService;

    private static ArtifactSaver artifactSaver;

    private static ImportExportAPI importExportService;

    private static OrganizationResolver organizationResolver;

    private static Map<String, KeyManagerConnectorConfiguration> keyManagerConnectorConfigurationMap = new HashMap<>();

    /**
     * Method to get RegistryService.
     *
     * @return registryService.
     */
    public static RegistryService getRegistryService() {
        return registryService;
    }

    /**
     * Method to set registry RegistryService.
     *
     * @param service registryService.
     */
    public static void setRegistryService(RegistryService service) {
        registryService = service;
    }

    /**
     * This method used to get RealmService.
     *
     * @return RealmService.
     */
    public static RealmService getRealmService() {
        return realmService;
    }

    /**
     * Method to set registry RealmService.
     *
     * @param service RealmService.
     */
    public static void setRealmService(RealmService service) {
        realmService = service;
    }

    /**
     * This method used to get TenantRegistryLoader
     *
     * @return tenantRegLoader  Tenant registry loader for load tenant registry
     */
    public static TenantRegistryLoader getTenantRegLoader() {
        return tenantRegLoader;
    }

    /**
     * This method used to set TenantRegistryLoader
     *
     * @param service Tenant registry loader for load tenant registry
     */
    public static void setTenantRegLoader(TenantRegistryLoader service) {
        tenantRegLoader = service;
    }
    
    /**
     * Returns APIManagerConfigurationService
     * @return
     */
    public static APIManagerConfigurationService getAPIManagerConfigurationService() {
        return amConfigurationService;
    }

    /**
     * Sets APIManagerConfigurationService
     * @param amConfigService
     */
    public static void setAPIManagerConfigurationService(APIManagerConfigurationService amConfigService) {
        amConfigurationService = amConfigService;
    }

    /**
     * This method is used to get ApplicationManagementService
     * @return
     */
    public static ApplicationManagementService getApplicationManagementService() {
        return applicationManagementService;
    }

    /**
     * This method is used to set ApplicationManagementService
     * @param service
     */
    public static void setApplicationManagementService(ApplicationManagementService service) {
        applicationManagementService = service;
    }

    public static ConfigurationContextService getContextService() {

        return contextService;
    }

    public static void setContextService(ConfigurationContextService contextService) {

        ServiceHolder.contextService = contextService;
    }

    public static UserRealm getUserRealm() {

        return userRealm;
    }

    public static void setUserRealm(UserRealm realm) {

        userRealm = realm;
    }

    public static ArtifactSaver getArtifactSaver() {

        return artifactSaver;
    }

    public static void setArtifactSaver(ArtifactSaver artifactSaver) {

        ServiceHolder.artifactSaver = artifactSaver;
    }

    public static void setImportExportAPI(ImportExportAPI importExportService) {

        ServiceHolder.importExportService = importExportService;
    }

    public static ImportExportAPI getImportExportService() {

        return importExportService;
    }

    public static TenantIndexingLoader getIndexLoaderService() {

        return indexLoader;
    }

    public static void setIndexLoaderService(TenantIndexingLoader indexLoader) {

        ServiceHolder.indexLoader = indexLoader;
    }

    public static OrganizationResolver getOrganizationResolver() {
        return organizationResolver;
    }

    public static void setOrganizationResolver(OrganizationResolver organizationResolver) {
        ServiceHolder.organizationResolver = organizationResolver;
    }


    public static void removeKeyManagerConnectorConfiguration(String type) {

        keyManagerConnectorConfigurationMap.remove(type);
    }

    public static KeyManagerConnectorConfiguration getKeyManagerConnectorConfiguration(String type) {

        return keyManagerConnectorConfigurationMap.get(type);
    }

    public static Map<String, KeyManagerConnectorConfiguration> getKeyManagerConnectorConfigurations() {

        return keyManagerConnectorConfigurationMap;
    }
}
