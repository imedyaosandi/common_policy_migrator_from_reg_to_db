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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.wso2.carbon.apimgt.api.OrganizationResolver;
import org.wso2.carbon.apimgt.impl.APIManagerConfigurationService;
import org.wso2.carbon.apimgt.impl.config.APIMConfigService;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.ArtifactSaver;
import org.wso2.carbon.apimgt.impl.importexport.ImportExportAPI;
import org.wso2.carbon.common.policy.migration.APIMCommonPolicyMigrationClient;
import org.wso2.carbon.common.policy.migration.util.APIUtil;
import org.wso2.carbon.common.policy.migration.util.ExtendedAPIMConfigServiceImpl;
import org.wso2.carbon.core.ServerStartupObserver;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.service.TenantRegistryLoader;
import org.wso2.carbon.registry.indexing.service.TenantIndexingLoader;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.ConfigurationContextService;

@Component(name = "org.wso2.carbon.common.policy.migration.client", immediate = true)
public class APIMMigrationServiceComponent {
    private static final Log log = LogFactory.getLog(APIMMigrationServiceComponent.class);

    /**
     * Method to activate bundle.
     *
     * @param context OSGi component context.
     */
    @Activate
    protected void activate(ComponentContext context) {
        context.getBundleContext().registerService(ServerStartupObserver.class.getName(), new APIMCommonPolicyMigrationClient(), null);
        APIUtil.init();
        log.info("Activating APIMMigrationClient.");
        ExtendedAPIMConfigServiceImpl extendedAPIMConfigService = new ExtendedAPIMConfigServiceImpl();
        context.getBundleContext().registerService(APIMConfigService.class.getName(), extendedAPIMConfigService, null);
        log.info("Activated ExtendedAPIMConfigService.");
    }

    /**
     * Method to deactivate bundle.
     *
     * @param context OSGi component context.
     */
    @Deactivate
    protected void deactivate(ComponentContext context) {
        log.info("WSO2 API Manager migration bundle is deactivated");
    }

    /**
     * Method to set registry service.
     *
     * @param registryService service to get tenant data.
     */
    @Reference(
            name = "registry.service",
            service = RegistryService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRegistryService")
    protected void setRegistryService(RegistryService registryService) {
        if (log.isDebugEnabled()) {
            log.debug("Setting RegistryService for WSO2 API Manager migration");
        }
        ServiceHolder.setRegistryService(registryService);
    }

    /**
     * Method to unset registry service.
     *
     * @param registryService service to get registry data.
     */
    protected void unsetRegistryService(RegistryService registryService) {
        if (log.isDebugEnabled()) {
            log.debug("Unset Registry service");
        }
        ServiceHolder.setRegistryService(null);
    }

    /**
     * Method to set realm service.
     *
     * @param realmService service to get tenant data.
     */
    @Reference(
            name = "realm.service",
            service = RealmService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRealmService")
    protected void setRealmService(RealmService realmService) {
        log.debug("Setting RealmService for WSO2 API Manager migration");
        ServiceHolder.setRealmService(realmService);
        IdentityTenantUtil.setRealmService(realmService);
    }

    /**
     * Method to unset realm service.
     *
     * @param realmService service to get tenant data.
     */
    protected void unsetRealmService(RealmService realmService) {
        if (log.isDebugEnabled()) {
            log.debug("Unset Realm service");
        }
        ServiceHolder.setRealmService(null);
        IdentityTenantUtil.setRealmService(null);
    }

    /**
     * Method to set tenant registry loader
     *
     * @param tenantRegLoader tenant registry loader
     */
    @Reference(
            name = "tenant.registryloader",
            service = TenantRegistryLoader.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetTenantRegistryLoader")
    protected void setTenantRegistryLoader(TenantRegistryLoader tenantRegLoader) {
        log.debug("Setting TenantRegistryLoader for WSO2 API Manager migration");
        ServiceHolder.setTenantRegLoader(tenantRegLoader);
    }

    /**
     * Method to unset tenant registry loader
     *
     * @param tenantRegLoader tenant registry loader
     */
    protected void unsetTenantRegistryLoader(TenantRegistryLoader tenantRegLoader) {
        log.debug("Unset Tenant Registry Loader");
        ServiceHolder.setTenantRegLoader(null);
    }

    /**
     * Method to set API Manager configuration
     *
     * @param apiManagerConfig api manager configuration
     */
    @Reference(
            name = "apim.configuration",
            service = APIManagerConfigurationService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetApiManagerConfig")
    protected void setApiManagerConfig(APIManagerConfigurationService apiManagerConfig) {
        log.info("Setting APIManager configuration");
        ServiceHolder.setAPIManagerConfigurationService(apiManagerConfig);
    }

    /**
     * Method to unset API manager configuration
     *
     * @param apiManagerConfig api manager configuration
     */
    protected void unsetApiManagerConfig(APIManagerConfigurationService apiManagerConfig) {
        log.info("Un-setting APIManager configuration");
        ServiceHolder.setAPIManagerConfigurationService(null);
    }

    /**
     * Method to set ApplicationManagementService
     *
     * @param applicationManagementService
     */
    @Reference(
            name = "applicationManagement.service",
            service = ApplicationManagementService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetApplicationManagementService")
    protected void setApplicationManagementService(ApplicationManagementService applicationManagementService) {
        if (log.isDebugEnabled()) {
            log.debug("Setting setApplicationManagementService");
        }
        ServiceHolder.setApplicationManagementService(applicationManagementService);
    }

    /**
     * Method to unset ApplicationManagementService
     *
     * @param applicationManagementService
     */
    protected void unsetApplicationManagementService(ApplicationManagementService applicationManagementService) {
        if (log.isDebugEnabled()) {
            log.debug("Un-setting setApplicationManagementService");
        }
        ServiceHolder.setApplicationManagementService(null);
    }

    /**
     * Method to set ConfigurationContextService
     *
     * @param contextService
     */
    @Reference(
            name = "configuration.context.service",
            service = ConfigurationContextService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationContextService")
    protected void setConfigurationContextService(ConfigurationContextService contextService) {
        if (log.isDebugEnabled()) {
            log.debug("Setting setConfigurationContextService");
        }
        ServiceHolder.setContextService(contextService);
    }

    /**
     * Method to unset ConfigurationContextService
     *
     * @param contextService
     */
    protected void unsetConfigurationContextService(ConfigurationContextService contextService) {
        if (log.isDebugEnabled()) {
            log.debug("Un-Setting unsetConfigurationContextService");
        }
        ServiceHolder.setContextService(null);
    }

    protected void addArtifactSaver(ArtifactSaver artifactSaver) {
        ServiceHolder.setArtifactSaver(artifactSaver);
        try {
            ServiceHolder.getArtifactSaver().init();
        } catch (Exception e) {
            log.error("Error connecting with the Artifact Saver");
            removeArtifactSaver(null);
        }
    }

    protected void removeArtifactSaver(ArtifactSaver artifactSaver) {
        ServiceHolder.getArtifactSaver().disconnect();
        ServiceHolder.setArtifactSaver(null);
    }

    @Reference(
            name = "import.export.implementation",
            service = ImportExportAPI.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetImportExportService")
    protected void setImportExportService (ImportExportAPI importExportService) {

        ServiceHolder.setImportExportAPI(importExportService);
    }

    protected void unsetImportExportService(ImportExportAPI importExportAPI) {
        ServiceHolder.setImportExportAPI(null);
    }

    @Reference(
            name = "tenant.indexloader",
            service = TenantIndexingLoader.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetIndexLoader")
    protected void setIndexLoader(TenantIndexingLoader indexLoader) {
        if (indexLoader != null && log.isDebugEnabled()) {
            log.debug("IndexLoader service initialized");
        }
        ServiceHolder.setIndexLoaderService(indexLoader);
    }

    protected void unsetIndexLoader(TenantIndexingLoader registryService) {
        ServiceHolder.setIndexLoaderService(null);
    }

    protected void addOrganizationResolver(OrganizationResolver resolver) {
        ServiceHolder.setOrganizationResolver(resolver);
    }

    protected void removeOrganizationResolver(OrganizationResolver resolver) {
        ServiceHolder.setOrganizationResolver(null);
    }
}
