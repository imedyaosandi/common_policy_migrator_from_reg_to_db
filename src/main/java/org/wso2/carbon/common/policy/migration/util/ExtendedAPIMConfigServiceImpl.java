package org.wso2.carbon.common.policy.migration.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.caching.CacheProvider;
import org.wso2.carbon.apimgt.impl.config.APIMConfigServiceImpl;
import org.wso2.carbon.common.policy.migration.client.internal.ServiceHolder;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.session.UserRegistry;

import javax.cache.Cache;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

public class ExtendedAPIMConfigServiceImpl extends APIMConfigServiceImpl {

    private static final Log log = LogFactory.getLog(ExtendedAPIMConfigServiceImpl.class);
    @Override
    public void addTenantConfig(String organization, String tenantConfig) throws APIManagementException {

        if (APIUtil.isDisabledExtendedAPIMConfigService()) {
            log.info("WSO2 API-M Migration Task : ExtendedAPIMConfigService is disabled. Hence, executing super "
                    + "method logic.");
            super.addTenantConfig(organization,tenantConfig);
        } else {
            log.info("WSO2 API-M Migration Task : ExtendedAPIMConfigService is enabled. Hence, executing overridden "
                    + "implementation");
            if (organization == null) {
                organization = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
            }
            try {
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(organization, true);
                int tenantId = APIUtil.getTenantIdFromTenantDomain(organization);
                if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(organization)) {
                    APIUtil.loadTenantRegistry(tenantId);
                }
                RegistryService registryService = ServiceHolder.getRegistryService();
                UserRegistry registry = registryService.getConfigSystemRegistry(tenantId);
                if (!registry.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                    Resource resource = registry.newResource();
                    resource.setContent(IOUtils.toByteArray(new StringReader(tenantConfig)));
                    resource.setMediaType(APIConstants.API_TENANT_CONF_MEDIA_TYPE);
                    registry.put(APIConstants.API_TENANT_CONF_LOCATION, resource);
                }

            } catch (RegistryException | IOException e) {
                throw new APIManagementException("WSO2 API-M Migration Task : Error while adding tenant config to "
                        + "registry for organization: " + organization, e);
            } finally {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

    @Override
    public String getTenantConfig(String organization) throws APIManagementException {

        if (APIUtil.isDisabledExtendedAPIMConfigService()) {
            log.info("WSO2 API-M Migration Task : ExtendedAPIMConfigService is disabled. Hence, executing super "
                    + "method logic.");
            return super.getTenantConfig(organization);
        } else {
            log.info("WSO2 API-M Migration Task : ExtendedAPIMConfigService is enabled. Hence, executing overridden "
                    + "implementation");
            if (organization == null) {
                organization = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
            }
            try {
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(organization, true);
                int tenantId = APIUtil.getTenantIdFromTenantDomain(organization);
                if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(organization)) {
                    APIUtil.loadTenantRegistry(tenantId);
                }
                RegistryService registryService = ServiceHolder.getRegistryService();
                UserRegistry registry = registryService.getConfigSystemRegistry(tenantId);
                if (registry.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                    Resource resource = registry.get(APIConstants.API_TENANT_CONF_LOCATION);
                    return new String((byte[]) resource.getContent(), Charset.defaultCharset());
                } else {
                    return null;
                }

            } catch (RegistryException e) {
                throw new APIManagementException("WSO2 API-M Migration Task : Error while getting tenant config from "
                        + "registry for organization: "
                        + organization, e);
            } finally {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

    @Override
    public void updateTenantConfig(String organization, String tenantConfig) throws APIManagementException {

        if (APIUtil.isDisabledExtendedAPIMConfigService()) {
            log.info("WSO2 API-M Migration Task : ExtendedAPIMConfigService is disabled. Hence, executing super "
                    + "method logic.");
            super.updateTenantConfig(organization,tenantConfig);
        } else {
            log.info("WSO2 API-M Migration Task : ExtendedAPIMConfigService is enabled. Hence, executing overridden"
                    + " implementation");
            if (organization == null) {
                organization = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
            }
            Cache tenantConfigCache = CacheProvider.getTenantConfigCache();
            String cacheName = organization + "_" + APIConstants.TENANT_CONFIG_CACHE_NAME;
            tenantConfigCache.remove(cacheName);
            try {
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(organization, true);
                int tenantId = APIUtil.getTenantIdFromTenantDomain(organization);
                if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(organization)) {
                    APIUtil.loadTenantRegistry(tenantId);
                }
                RegistryService registryService = ServiceHolder.getRegistryService();
                UserRegistry registry = registryService.getConfigSystemRegistry(tenantId);
                if (registry.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                    Resource resource = registry.get(APIConstants.API_TENANT_CONF_LOCATION);
                    resource.setContent(IOUtils.toByteArray(new StringReader(tenantConfig)));
                    resource.setMediaType(APIConstants.API_TENANT_CONF_MEDIA_TYPE);
                    registry.put(APIConstants.API_TENANT_CONF_LOCATION, resource);
                }
            } catch (RegistryException | IOException e) {
                throw new APIManagementException("WSO2 API-M Migration Task : Error while updating tenant config to "
                        + "registry for organization: "
                        + organization, e);
            } finally {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
    }

}
