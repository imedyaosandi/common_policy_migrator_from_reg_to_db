/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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


package org.wso2.carbon.common.policy.migration.util;

import com.google.gson.Gson;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.util.SecurityManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.OrganizationResolver;
import org.wso2.carbon.apimgt.api.model.*;
import org.wso2.carbon.apimgt.api.model.policy.*;
import org.wso2.carbon.apimgt.common.gateway.dto.ClaimMappingDto;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerConfiguration;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dao.ScopesDAO;
import org.wso2.carbon.apimgt.impl.dto.ThrottleProperties;
import org.wso2.carbon.apimgt.impl.internal.APIManagerComponent;
import org.wso2.carbon.apimgt.impl.resolver.OnPremResolver;
import org.wso2.carbon.apimgt.impl.utils.APIDescriptionGenUtil;
import org.wso2.carbon.common.policy.migration.client.internal.ServiceHolder;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.common.dataobjects.GovernanceArtifact;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.Tag;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.service.TenantRegistryLoader;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import javax.cache.Cache;
import javax.cache.Caching;
import javax.security.cert.X509Certificate;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public final  class APIUtil {
    private static final Log log = LogFactory.getLog(APIUtil.class);
    private static final int DEFAULT_TENANT_IDLE_MINS = 30;
    private static final String DESCRIPTION = "Allows [1] request(s) per minute.";
    private static long tenantIdleTimeMillis;
    private static Set<String> currentLoadingTenants = new HashSet<String>();
    private static boolean isPublisherRoleCacheEnabled = true;
    private static final int ENTITY_EXPANSION_LIMIT = 0;

    //Need tenantIdleTime to check whether the tenant is in idle state in loadTenantConfig method
    static {
        tenantIdleTimeMillis =
                Long.parseLong(System.getProperty(
                        org.wso2.carbon.utils.multitenancy.MultitenantConstants.TENANT_IDLE_TIME,
                        String.valueOf(DEFAULT_TENANT_IDLE_MINS)))
                        * 60 * 1000;
    }

    private static String hostAddress = null;
    private static final int timeoutInSeconds = 15;
    private static final int retries = 2;
    private static boolean disabledExtendedAPIMConfigService = false;

    public static boolean isDisabledExtendedAPIMConfigService() {
        return disabledExtendedAPIMConfigService;
    }

    public static void setDisabledExtendedAPIMConfigService(boolean isDisabledExtendedAPIMConfigService) {
        disabledExtendedAPIMConfigService = isDisabledExtendedAPIMConfigService;
    }

    /**
     * To initialize the publisherRoleCache configurations, based on configurations.
     */
    public static void init() {

        APIManagerConfiguration apiManagerConfiguration = ServiceHolder
                .getAPIManagerConfigurationService().getAPIManagerConfiguration();
        String isPublisherRoleCacheEnabledConfiguration = apiManagerConfiguration
                .getFirstProperty(APIConstants.PUBLISHER_ROLE_CACHE_ENABLED);
        isPublisherRoleCacheEnabled = isPublisherRoleCacheEnabledConfiguration == null || Boolean
                .parseBoolean(isPublisherRoleCacheEnabledConfiguration);
    }

    /**
     * This method used to get API from governance artifact
     *
     * @param artifact API artifact
     * @return API
     * @throws APIManagementException if failed to get API from artifact
     */
//    public static API getAPI(GovernanceArtifact artifact, Registry registry)
//            throws APIManagementException {
//
//        API api;
//        try {
//            String providerName = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
//            String apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
//            String apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
//            APIIdentifier apiIdentifier = new APIIdentifier(providerName, apiName, apiVersion);
//            int apiId = org.wso2.carbon.apimgt.migration.migrator.v410.dao.ApiMgtDAO.getInstance()
//                    .getAPIID(apiIdentifier);
//
//            if (apiId == -1) {
//                return null;
//            }
//            api = new API(apiIdentifier);
//            // set rating
//            String artifactPath = GovernanceUtils.getArtifactPath(registry, artifact.getId());
//            api = setResourceProperties(api, registry, artifactPath);
//            api.setRating(getAverageRating(apiId));
//            //set description
//            api.setDescription(artifact.getAttribute(APIConstants.API_OVERVIEW_DESCRIPTION));
//            //set last access time
//            api.setLastUpdated(registry.get(artifactPath).getLastModified());
//            //set uuid
//            api.setUuid(artifact.getId());
//            //setting api ID for scope retrieval
//            api.getId().setApplicationId(Integer.toString(apiId));
//            // set url
//            api.setStatus(getLcStateFromArtifact(artifact));
//            api.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
//            api.setThumbnailUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
//            api.setWsdlUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_WSDL));
//            api.setWadlUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_WADL));
//            api.setTechnicalOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER));
//            api.setTechnicalOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER_EMAIL));
//            api.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
//            api.setBusinessOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER_EMAIL));
//            api.setVisibility(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY));
//            api.setVisibleRoles(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES));
//            api.setVisibleTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_TENANTS));
//            api.setEndpointSecured(Boolean.parseBoolean(artifact.getAttribute(
//                    APIConstants.API_OVERVIEW_ENDPOINT_SECURED)));
//            api.setEndpointAuthDigest(Boolean.parseBoolean(artifact.getAttribute(
//                    APIConstants.API_OVERVIEW_ENDPOINT_AUTH_DIGEST)));
//            api.setEndpointUTUsername(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_USERNAME));
//            if (!((APIConstants.DEFAULT_MODIFIED_ENDPOINT_PASSWORD)
//                    .equals(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD)))) {
//                api.setEndpointUTPassword(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_PASSWORD));
//            } else { //If APIEndpointPasswordRegistryHandler is enabled take password from the registry hidden property
//                api.setEndpointUTPassword(getActualEpPswdFromHiddenProperty(api, registry));
//            }
//            api.setTransports(artifact.getAttribute(APIConstants.API_OVERVIEW_TRANSPORTS));
//            api.setInSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_INSEQUENCE));
//            api.setOutSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_OUTSEQUENCE));
//            api.setFaultSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_FAULTSEQUENCE));
//            api.setResponseCache(artifact.getAttribute(APIConstants.API_OVERVIEW_RESPONSE_CACHING));
//            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
//            api.setProductionMaxTps(artifact.getAttribute(APIConstants.API_PRODUCTION_THROTTLE_MAXTPS));
//
//            int cacheTimeout = APIConstants.API_RESPONSE_CACHE_TIMEOUT;
//            try {
//                cacheTimeout = Integer.parseInt(artifact.getAttribute(APIConstants.API_OVERVIEW_CACHE_TIMEOUT));
//            } catch (NumberFormatException e) {
//                //ignore
//            }
//
//            api.setCacheTimeout(cacheTimeout);
//
//            api.setEndpointConfig(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG));
//
//            api.setRedirectURL(artifact.getAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL));
//            api.setApiOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_OWNER));
//            api.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY)));
//
//            api.setSubscriptionAvailability(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY));
//            api.setSubscriptionAvailableTenants(artifact.getAttribute(
//                    APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABLE_TENANTS));
//
//            String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));
//            int tenantId = ServiceHolder.getRealmService().getTenantManager()
//                    .getTenantId(tenantDomainName);
//
//            String apiLevelTier = ApiMgtDAO.getInstance().getAPILevelTier(apiId);
//            api.setApiLevelPolicy(apiLevelTier);
//
//            String tiers = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
//            Map<String, Tier> definedTiers = getTiers(tenantId);
//            Set<Tier> availableTier = getAvailableTiers(definedTiers, tiers, apiName);
//            api.addAvailableTiers(availableTier);
//            api.setMonetizationCategory(getAPIMonetizationCategory(availableTier, tenantDomainName));
//
//            api.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT));
//            // We set the context template here
//            api.setContextTemplate(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT_TEMPLATE));
//            api.setLatest(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_IS_LATEST)));
//            api.setEnableSchemaValidation(Boolean.parseBoolean(
//                    artifact.getAttribute(APIConstants.API_OVERVIEW_ENABLE_JSON_SCHEMA)));
//
//            Map<String, Scope> scopeToKeyMapping = getAPIScopes(artifact.getId(), tenantDomainName);
//            api.setScopes(new LinkedHashSet<>(scopeToKeyMapping.values()));
//
//            Set<URITemplate> uriTemplates = ApiMgtDAO.getInstance().getURITemplatesOfAPI(artifact.getId());
//
//            for (URITemplate uriTemplate : uriTemplates) {
//                List<Scope> oldTemplateScopes = uriTemplate.retrieveAllScopes();
//                List<Scope> newTemplateScopes = new ArrayList<>();
//                if (!oldTemplateScopes.isEmpty()) {
//                    for (Scope templateScope : oldTemplateScopes) {
//                        Scope scope = scopeToKeyMapping.get(templateScope.getKey());
//                        newTemplateScopes.add(scope);
//                    }
//                }
//                uriTemplate.addAllScopes(newTemplateScopes);
//                uriTemplate.setResourceURI(api.getUrl());
//                uriTemplate.setResourceSandboxURI(api.getSandboxUrl());
//            }
//            api.setUriTemplates(uriTemplates);
//            api.setAsDefaultVersion(Boolean.parseBoolean(artifact.getAttribute(
//                    APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION)));
//            Set<String> tags = new HashSet<String>();
//            Tag[] tag = registry.getTags(artifactPath);
//            for (Tag tag1 : tag) {
//                tags.add(tag1.getTagName());
//            }
//            api.addTags(tags);
//            api.setLastUpdated(registry.get(artifactPath).getLastModified());
//            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
//            String environments = artifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
//            api.setEnvironments(extractEnvironmentsForAPI(environments));
//            api.setCorsConfiguration(getCorsConfigurationFromArtifact(artifact));
//            api.setAuthorizationHeader(artifact.getAttribute(APIConstants.API_OVERVIEW_AUTHORIZATION_HEADER));
//            api.setApiSecurity(artifact.getAttribute(APIConstants.API_OVERVIEW_API_SECURITY));
//            api.setApiCategories(getAPICategoriesFromAPIGovernanceArtifact(artifact, tenantId));
//            api.setVersionTimestamp(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION_COMPARABLE));
//
//        } catch (GovernanceException e) {
//            String msg = "Failed to get API for artifact ";
//            throw new APIManagementException(msg, e);
//        } catch (RegistryException e) {
//            String msg = "Failed to get LastAccess time or Rating";
//            throw new APIManagementException(msg, e);
//        } catch (UserStoreException e) {
//            String msg = "Failed to get User Realm of API Provider";
//            throw new APIManagementException(msg, e);
//        }
//        return api;
//    }

    /**
     * This method is used to get the categories in a given tenant space
     *
     * @param tenantDomain tenant domain name
     * @return categories in a given tenant space
     * @throws APIManagementException if failed to fetch categories
     */
    public static List<APICategory> getAllAPICategoriesOfTenant(String tenantDomain) throws APIManagementException {

        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        int tenantId = getTenantIdFromTenantDomain(tenantDomain);
        return apiMgtDAO.getAllCategories(String.valueOf(tenantId));
    }

    /**
     * Helper method to get tenantId from tenantDomain
     *
     * @param tenantDomain tenant Domain
     * @return tenantId
     */
    public static int getTenantIdFromTenantDomain(String tenantDomain) {

        RealmService realmService = ServiceHolder.getRealmService();

        if (realmService == null || tenantDomain == null) {
            return MultitenantConstants.SUPER_TENANT_ID;
        }

        try {
            return realmService.getTenantManager().getTenantId(tenantDomain);
        } catch (UserStoreException e) {
            log.error(e.getMessage(), e);
        }

        return -1;
    }

    /**
     * Utility method to get OpenAPI registry path for API product
     *
     * @param identifier product identifier
     * @return path path to the
     */
    public static String getAPIProductOpenAPIDefinitionFilePath(APIProductIdentifier identifier) {

        return APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + identifier.getProviderName() +
                RegistryConstants.PATH_SEPARATOR + identifier.getName() + RegistryConstants.PATH_SEPARATOR +
                identifier.getVersion() + RegistryConstants.PATH_SEPARATOR;
    }

    /**
     * When an input is having '-AT-',replace it with @ [This is required to persist API data between registry and database]
     *
     * @param input inputString
     * @return String modifiedString
     */
    public static String replaceEmailDomainBack(String input) {

        if (input != null && input.contains(APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT)) {
            input = input.replace(APIConstants.EMAIL_DOMAIN_SEPARATOR_REPLACEMENT,
                    APIConstants.EMAIL_DOMAIN_SEPARATOR);
        }
        return input;
    }

    /**
     * Get the API Product Identifier from UUID.
     *
     * @param uuid UUID of the API
     * @return API Product Identifier
     * @throws APIManagementException
     */
    public static APIProductIdentifier getAPIProductIdentifierFromUUID(String uuid) throws APIManagementException{
        return ApiMgtDAO.getInstance().getAPIProductIdentifierFromUUID(uuid);
    }

    /**
     * Get the API Identifier from UUID.
     *
     * @param uuid UUID of the API
     * @return API Identifier
     * @throws APIManagementException
     */
    public static APIIdentifier getAPIIdentifierFromUUID(String uuid) throws APIManagementException{
        return ApiMgtDAO.getInstance().getAPIIdentifierFromUUID(uuid);
    }


    public static API getAPI(GovernanceArtifact artifact)
            throws APIManagementException {

        API api;
        try {
            String providerName = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
            String apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
            String apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
            APIIdentifier apiIdentifier = new APIIdentifier(providerName, apiName, apiVersion);
            api = new API(apiIdentifier);
            int apiId = ApiMgtDAO.getInstance().getAPIID(artifact.getId());
            if (apiId == -1) {
                return null;
            }
            //set uuid
            api.setUuid(artifact.getId());
            api.setRating(getAverageRating(apiId));
            api.setThumbnailUrl(artifact.getAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL));
            api.setStatus(getLcStateFromArtifact(artifact));
            api.setContext(artifact.getAttribute(APIConstants.API_OVERVIEW_CONTEXT));
            api.setVisibility(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBILITY));
            api.setVisibleRoles(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_ROLES));
            api.setVisibleTenants(artifact.getAttribute(APIConstants.API_OVERVIEW_VISIBLE_TENANTS));
            api.setTransports(artifact.getAttribute(APIConstants.API_OVERVIEW_TRANSPORTS));
            api.setInSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_INSEQUENCE));
            api.setOutSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_OUTSEQUENCE));
            api.setFaultSequence(artifact.getAttribute(APIConstants.API_OVERVIEW_FAULTSEQUENCE));
            api.setDescription(artifact.getAttribute(APIConstants.API_OVERVIEW_DESCRIPTION));
            api.setResponseCache(artifact.getAttribute(APIConstants.API_OVERVIEW_RESPONSE_CACHING));
            api.setType(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE));
            api.setEnableStore(Boolean.parseBoolean(
                    artifact.getAttribute(APIConstants.API_OVERVIEW_ENABLE_STORE)));
            api.setTestKey(artifact.getAttribute(APIConstants.API_OVERVIEW_TESTKEY));
            int cacheTimeout = APIConstants.API_RESPONSE_CACHE_TIMEOUT;
            try {
                cacheTimeout = Integer.parseInt(artifact.getAttribute(APIConstants.API_OVERVIEW_CACHE_TIMEOUT));
            } catch (NumberFormatException e) {
                //ignore
            }
            api.setCacheTimeout(cacheTimeout);

            String apiLevelTier = ApiMgtDAO.getInstance().getAPILevelTier(apiId);
            api.setApiLevelPolicy(apiLevelTier);

            Set<Tier> availablePolicy = new HashSet<Tier>();
            String[] subscriptionPolicy = ApiMgtDAO.getInstance().getPolicyNames(PolicyConstants.POLICY_LEVEL_SUB, replaceEmailDomainBack(providerName));
            List<String> definedPolicyNames = Arrays.asList(subscriptionPolicy);
            String policies = artifact.getAttribute(APIConstants.API_OVERVIEW_TIER);
            if (policies != null && !"".equals(policies)) {
                String[] policyNames = policies.split("\\|\\|");
                for (String policyName : policyNames) {
                    if (definedPolicyNames.contains(policyName) || APIConstants.UNLIMITED_TIER.equals(policyName)) {
                        Tier p = new Tier(policyName);
                        availablePolicy.add(p);
                    } else {
                        log.warn("Unknown policy: " + policyName + " found on API: " + apiName);
                    }
                }
            }

            api.addAvailableTiers(availablePolicy);
            String tenantDomainName = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(providerName));
            api.setMonetizationCategory(getAPIMonetizationCategory(availablePolicy, tenantDomainName));

            api.setRedirectURL(artifact.getAttribute(APIConstants.API_OVERVIEW_REDIRECT_URL));
            api.setApiOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_OWNER));
            api.setAdvertiseOnly(Boolean.parseBoolean(artifact.getAttribute(APIConstants.API_OVERVIEW_ADVERTISE_ONLY)));

            api.setEndpointConfig(artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG));

            api.setSubscriptionAvailability(artifact.getAttribute(APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABILITY));
            api.setSubscriptionAvailableTenants(artifact.getAttribute(
                    APIConstants.API_OVERVIEW_SUBSCRIPTION_AVAILABLE_TENANTS));

            api.setAsDefaultVersion(Boolean.parseBoolean(artifact.getAttribute(
                    APIConstants.API_OVERVIEW_IS_DEFAULT_VERSION)));
            api.setImplementation(artifact.getAttribute(APIConstants.PROTOTYPE_OVERVIEW_IMPLEMENTATION));
            api.setTechnicalOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER));
            api.setTechnicalOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_TEC_OWNER_EMAIL));
            api.setBusinessOwner(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER));
            api.setBusinessOwnerEmail(artifact.getAttribute(APIConstants.API_OVERVIEW_BUSS_OWNER_EMAIL));

            ArrayList<URITemplate> urlPatternsList;
            urlPatternsList = ApiMgtDAO.getInstance().getAllURITemplates(api.getContext(), api.getId().getVersion());
            Set<URITemplate> uriTemplates = new HashSet<URITemplate>(urlPatternsList);

            for (URITemplate uriTemplate : uriTemplates) {
                uriTemplate.setResourceURI(api.getUrl());
                uriTemplate.setResourceSandboxURI(api.getSandboxUrl());

            }
            api.setUriTemplates(uriTemplates);
            String environments = artifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
            api.setEnvironments(extractEnvironmentsForAPI(environments));
            api.setCorsConfiguration(getCorsConfigurationFromArtifact(artifact));
            api.setAuthorizationHeader(artifact.getAttribute(APIConstants.API_OVERVIEW_AUTHORIZATION_HEADER));
            api.setApiSecurity(artifact.getAttribute(APIConstants.API_OVERVIEW_API_SECURITY));
            api.setVersionTimestamp(artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION_COMPARABLE));
            //get endpoint config string from artifact, parse it as a json and set the environment list configured with
            //non empty URLs to API object
            try {
                api.setEnvironmentList(extractEnvironmentListForAPI(
                        artifact.getAttribute(APIConstants.API_OVERVIEW_ENDPOINT_CONFIG)));
            } catch (ParseException e) {
                String msg = "Failed to parse endpoint config JSON of API: " + apiName + " " + apiVersion;
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            } catch (ClassCastException e) {
                String msg = "Invalid endpoint config JSON found in API: " + apiName + " " + apiVersion;
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            }
        } catch (GovernanceException e) {
            String msg = "Failed to get API from artifact ";
            throw new APIManagementException(msg, e);
        }
        return api;
    }

    /**
     * Returns a secured DocumentBuilderFactory instance
     *
     * @return DocumentBuilderFactory
     */
    public static DocumentBuilderFactory getSecuredDocumentBuilder() {

        org.apache.xerces.impl.Constants Constants = null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        try {
            dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
            dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
            dbf.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
        } catch (ParserConfigurationException e) {
            log.error(
                    "Failed to load XML Processor Feature " + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE + " or " +
                            Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE + " or " + Constants.LOAD_EXTERNAL_DTD_FEATURE);
        }

        SecurityManager securityManager = new SecurityManager();
        securityManager.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
        dbf.setAttribute(Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY, securityManager);

        return dbf;
    }

    /**
     * This method used to extract environment list configured with non empty URLs.
     *
     * @param endpointConfigs (Eg: {"production_endpoints":{"url":"http://www.test.com/v1/xxx","config":null,
     *                        "template_not_supported":false},"endpoint_type":"http"})
     * @return Set<String>
     */
    public static Set<String> extractEnvironmentListForAPI(String endpointConfigs)
            throws ParseException, ClassCastException {

        Set<String> environmentList = new HashSet<String>();
        if (StringUtils.isNotBlank(endpointConfigs) && !"null".equals(endpointConfigs)) {
            JSONParser parser = new JSONParser();
            JSONObject endpointConfigJson = (JSONObject) parser.parse(endpointConfigs);
            if (endpointConfigJson.containsKey(APIConstants.API_DATA_PRODUCTION_ENDPOINTS) &&
                    isEndpointURLNonEmpty(endpointConfigJson.get(APIConstants.API_DATA_PRODUCTION_ENDPOINTS))) {
                environmentList.add(APIConstants.API_KEY_TYPE_PRODUCTION);
            }
            if (endpointConfigJson.containsKey(APIConstants.API_DATA_SANDBOX_ENDPOINTS) &&
                    isEndpointURLNonEmpty(endpointConfigJson.get(APIConstants.API_DATA_SANDBOX_ENDPOINTS))) {
                environmentList.add(APIConstants.API_KEY_TYPE_SANDBOX);
            }
        }
        return environmentList;
    }

//    public static void loadTenantAPIPolicy(String tenant, int tenantID) throws APIManagementException {
//
//        String tierBasePath = CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator + "resources"
//                + File.separator + "default-tiers" + File.separator;
//
//        String apiTierFilePath = tierBasePath + Constants.DEFAULT_API_TIER_FILE_NAME;
//        String appTierFilePath = tierBasePath + Constants.DEFAULT_APP_TIER_FILE_NAME;
//        String resTierFilePath = tierBasePath + Constants.DEFAULT_RES_TIER_FILE_NAME;
//
//        loadTenantAPIPolicy(tenantID, Constants.API_TIER_LOCATION, apiTierFilePath);
//        loadTenantAPIPolicy(tenantID, Constants.APP_TIER_LOCATION, appTierFilePath);
//        loadTenantAPIPolicy(tenantID, Constants.RES_TIER_LOCATION, resTierFilePath);
//    }

    /**
     * Get UUID by the API Identifier.
     *
     * @param identifier
     * @return String uuid string
     * @throws APIManagementException
     */
//    public static String getUUIDFromIdentifier(APIIdentifier identifier) throws APIManagementException{
//        return org.wso2.carbon.apimgt.migration.
//                migrator.v400.dao.ApiMgtDAO.getInstance().getUUIDFromIdentifier(identifier);
//    }

    /**
     * Get UUID by the API Identifier.
     *
     * @param identifier
     * @return String uuid string
     * @throws APIManagementException
     */
//    public static String getUUIDFromIdentifier(APIProductIdentifier identifier) throws APIManagementException{
//        return org.wso2.carbon.apimgt.migration.
//                migrator.v400.dao.ApiMgtDAO.getInstance().getUUIDFromIdentifier(identifier);
//    }

    /**
     * Adds the sequences defined in repository/resources/customsequences folder to tenant registry
     *
     * @param tenantID tenant Id
     * @throws APIManagementException
     */
    public static void writeDefinedSequencesToTenantRegistry(int tenantID)
            throws APIManagementException {

        try {

            RegistryService registryService = ServiceHolder.getRegistryService();
            UserRegistry govRegistry = registryService.getGovernanceSystemRegistry(tenantID);

            //Add all custom in,out and fault sequences to tenant registry
            APIUtil.addDefinedAllSequencesToRegistry(govRegistry, APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN);
            APIUtil.addDefinedAllSequencesToRegistry(govRegistry, APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT);
            APIUtil.addDefinedAllSequencesToRegistry(govRegistry, APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT);

        } catch (RegistryException e) {
            throw new APIManagementException(
                    "Error while saving defined sequences to the registry of tenant with id " + tenantID, e);
        }
    }

    /**
     * Add all the custom sequences of given type to registry
     *
     * @param registry           Registry instance
     * @param customSequenceType Custom sequence type which is in/out or fault
     * @throws APIManagementException
     */
    public static void addDefinedAllSequencesToRegistry(UserRegistry registry,
                                                        String customSequenceType)
            throws APIManagementException {

        InputStream inSeqStream = null;
        String seqFolderLocation =
                CarbonUtils.getCarbonHome() + File.separator + APIConstants.API_CUSTOM_SEQUENCES_FOLDER_LOCATION
                        + File.separator + customSequenceType;

        try {
            File inSequenceDir = new File(seqFolderLocation);
            File[] sequences;
            sequences = inSequenceDir.listFiles();

            if (sequences != null) {
                for (File sequenceFile : sequences) {
                    String sequenceFileName = sequenceFile.getName();
                    String regResourcePath =
                            APIConstants.API_CUSTOM_SEQUENCE_LOCATION + '/' +
                                    customSequenceType + '/' + sequenceFileName;
                    if (registry.resourceExists(regResourcePath)) {
                        if (log.isDebugEnabled()) {
                            log.debug("The sequence file with the name " + sequenceFileName
                                    + " already exists in the registry path " + regResourcePath);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "Adding sequence file with the name " + sequenceFileName + " to the registry path "
                                            + regResourcePath);
                        }

                        inSeqStream = new FileInputStream(sequenceFile);
                        byte[] inSeqData = IOUtils.toByteArray(inSeqStream);
                        Resource inSeqResource = registry.newResource();
                        inSeqResource.setContent(inSeqData);

                        registry.put(regResourcePath, inSeqResource);
                    }
                }
            } else {
                log.error(
                        "Custom sequence template location unavailable for custom sequence type " +
                                customSequenceType + " : " + seqFolderLocation
                );
            }

        } catch (RegistryException e) {
            throw new APIManagementException(
                    "Error while saving defined sequences to the registry ", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while reading defined sequence ", e);
        } finally {
            IOUtils.closeQuietly(inSeqStream);
        }

    }

    /**
     * Load the throttling policy  to the registry for tenants
     *
     * @param tenantID
     * @param location
     * @param fileName
     * @throws APIManagementException
     */
    private static void loadTenantAPIPolicy(int tenantID, String location, String fileName)
            throws APIManagementException {

        InputStream inputStream = null;

        try {
            RegistryService registryService = ServiceHolder.getRegistryService();

            UserRegistry govRegistry = registryService.getGovernanceSystemRegistry(tenantID);

            if (govRegistry.resourceExists(location)) {
                if (log.isDebugEnabled()) {
                    log.debug("Tier policies already uploaded to the tenant's registry space");
                }
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Adding API tier policies to the tenant's registry");
            }
            File defaultTiers = new File(fileName);
            if (!defaultTiers.exists()) {
                log.info("Default tier policies not found in : " + fileName);
                return;
            }
            inputStream = FileUtils.openInputStream(defaultTiers);
            byte[] data = IOUtils.toByteArray(inputStream);
            Resource resource = govRegistry.newResource();
            resource.setContent(data);
            govRegistry.put(location, resource);

        } catch (RegistryException e) {
            throw new APIManagementException("Error while saving policy information to the registry", e);
        } catch (IOException e) {
            throw new APIManagementException("Error while reading policy file content", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("Error when closing input stream", e);
                }
            }
        }
    }

    /**
     * This method used to check whether the endpoints JSON object has a non empty URL.
     *
     * @param endpoints (Eg: {"url":"http://www.test.com/v1/xxx","config":null,"template_not_supported":false})
     * @return boolean
     */
    public static boolean isEndpointURLNonEmpty(Object endpoints) {

        if (endpoints instanceof JSONObject) {
            JSONObject endpointJson = (JSONObject) endpoints;
            if (endpointJson.containsKey(APIConstants.API_DATA_URL) &&
                    endpointJson.get(APIConstants.API_DATA_URL) != null) {
                String url = (endpointJson.get(APIConstants.API_DATA_URL)).toString();
                if (StringUtils.isNotBlank(url)) {
                    return true;
                }
            }
        } else if (endpoints instanceof JSONArray) {
            JSONArray endpointsJson = (JSONArray) endpoints;
            for (int i = 0; i < endpointsJson.size(); i++) {
                if (isEndpointURLNonEmpty(endpointsJson.get(i))) {
                    return true;
                }
            }
        }
        return false;
    }


    public static void loadTenantRegistry(int tenantId) throws RegistryException {

        TenantRegistryLoader tenantRegistryLoader = APIManagerComponent.getTenantRegistryLoader();
        ServiceHolder.getIndexLoaderService().loadTenantIndex(tenantId);
        tenantRegistryLoader.loadTenantRegistry(tenantId);
    }

    public static String getAPIProductOpenAPIDefinitionFilePath(String apiName, String apiVersion, String apiProvider) {

        return APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + apiProvider + RegistryConstants.PATH_SEPARATOR +
                apiName + RegistryConstants.PATH_SEPARATOR + apiVersion + RegistryConstants.PATH_SEPARATOR;
    }

    /**
     * this method used to initialized the ArtifactManager
     *
     * @param registry Registry
     * @param key      , key name of the key
     * @return GenericArtifactManager
     * @throws APIManagementException if failed to initialized GenericArtifactManager
     */
    public static GenericArtifactManager getArtifactManager(Registry registry, String key) throws APIManagementException {

        GenericArtifactManager artifactManager = null;

        try {
            GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);
            if (GovernanceUtils.findGovernanceArtifactConfiguration(key, registry) != null) {
                artifactManager = new GenericArtifactManager(registry, key);
            } else {
                log.warn("Couldn't find GovernanceArtifactConfiguration of RXT: " + key +
                        ". Tenant id set in registry : " + ((UserRegistry) registry).getTenantId() +
                        ", Tenant domain set in PrivilegedCarbonContext: " +
                        PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId());
            }
        } catch (RegistryException e) {
            String msg = "Failed to initialize GenericArtifactManager";
            log.error(msg, e);
            throw new APIManagementException(msg, e);
        }
        return artifactManager;
    }

    /**
     * To set the resource properties to the API.
     *
     * @param api          API that need to set the resource properties.
     * @param registry     Registry to get the resource from.
     * @param artifactPath Path of the API artifact.
     * @return Updated API.
     * @throws RegistryException Registry Exception.
     */
    private static API setResourceProperties(API api, Registry registry, String artifactPath) throws RegistryException {

        Resource apiResource = registry.get(artifactPath);
        Properties properties = apiResource.getProperties();
        if (properties != null) {
            Enumeration propertyNames = properties.propertyNames();
            while (propertyNames.hasMoreElements()) {
                String propertyName = (String) propertyNames.nextElement();
                if (log.isDebugEnabled()) {
                    log.debug("API '" + api.getId().toString() + "' " + "has the property " + propertyName);
                }
                if (propertyName.startsWith(APIConstants.API_RELATED_CUSTOM_PROPERTIES_PREFIX)) {
                    api.addProperty(propertyName.substring(APIConstants.API_RELATED_CUSTOM_PROPERTIES_PREFIX.length()),
                            apiResource.getProperty(propertyName));
                }
            }
        }
        api.setAccessControl(apiResource.getProperty(APIConstants.ACCESS_CONTROL));

        String accessControlRoles = null;

        String displayPublisherRoles = apiResource.getProperty(APIConstants.DISPLAY_PUBLISHER_ROLES);
        if (displayPublisherRoles == null) {

            String publisherRoles = apiResource.getProperty(APIConstants.PUBLISHER_ROLES);

            if (publisherRoles != null) {
                accessControlRoles = APIConstants.NULL_USER_ROLE_LIST.equals(
                        apiResource.getProperty(APIConstants.PUBLISHER_ROLES)) ?
                        null : apiResource.getProperty(APIConstants.PUBLISHER_ROLES);
            }
        } else {
            accessControlRoles = APIConstants.NULL_USER_ROLE_LIST.equals(displayPublisherRoles) ?
                    null : displayPublisherRoles;
        }

        api.setAccessControlRoles(accessControlRoles);
        return api;
    }

    /**
     * this method used to set environments values to api object.
     *
     * @param environments environments values in json format
     * @return set of environments that Published
     */
    public static Set<String> extractEnvironmentsForAPI(String environments) throws APIManagementException {

        String tenantDomain = CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        Set<String> environmentStringSet = extractEnvironmentsForAPI(environments, tenantDomain);

        return environmentStringSet;
    }

    public static Set<String> extractEnvironmentsForAPI(String environments, String organization) throws APIManagementException {

        Set<String> environmentStringSet = null;
        if (environments == null) {
            environmentStringSet = new HashSet<>(getEnvironments(organization).keySet());
        } else {
            //handle not to publish to any of the gateways
            if (APIConstants.API_GATEWAY_NONE.equals(environments)) {
                environmentStringSet = new HashSet<String>();
            }
            //handle to set published gateways nto api object
            else if (!"".equals(environments)) {
                String[] publishEnvironmentArray = environments.split(",");
                environmentStringSet = new HashSet<String>(Arrays.asList(publishEnvironmentArray));
                environmentStringSet.remove(APIConstants.API_GATEWAY_NONE);
            }
            //handle to publish to any of the gateways when api creating stage
            else if ("".equals(environments)) {
                environmentStringSet = new HashSet<>(getEnvironments(organization).keySet());
            }
        }

        return environmentStringSet;
    }

    public static float getAverageRating(String id) throws APIManagementException {

        return ApiMgtDAO.getInstance().getAverageRating(id);
    }

    public static float getAverageRating(int apiId) throws APIManagementException {

        return ApiMgtDAO.getInstance().getAverageRating(apiId);
    }

    public static APIStatus getApiStatus(String status) throws APIManagementException {

        APIStatus apiStatus = null;
        for (APIStatus aStatus : APIStatus.values()) {
            if (aStatus.getStatus().equalsIgnoreCase(status)) {
                apiStatus = aStatus;
            }
        }
        return apiStatus;
    }

    public static String getLcStateFromArtifact(GovernanceArtifact artifact) throws GovernanceException {
        String lcState = artifact.getLifecycleState();
        String state = (lcState != null) ? lcState : artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS);
        return (state != null) ? state.toUpperCase() : null;
    }

    /**
     * Prepends the Tenant Prefix to a registry path. ex: /t/test1.com
     *
     * @param postfixUrl path to be prepended.
     * @return Path prepended with he Tenant domain prefix.
     */
    public static String prependTenantPrefix(String postfixUrl, String username) {

        String tenantDomain = MultitenantUtils.getTenantDomain(replaceEmailDomainBack(username));
        if (!(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain))) {
            String tenantPrefix = "/t/";
            postfixUrl = tenantPrefix + tenantDomain + postfixUrl;
        }

        return postfixUrl;
    }


    /**
     * Used to get Default CORS Configuration object according to configuration define in api-manager.xml
     *
     * @return CORSConfiguration object accordine to the defined values in api-manager.xml
     */
    public static CORSConfiguration getDefaultCorsConfiguration() {

        List<String> allowHeadersStringSet = Arrays.asList(getAllowedHeaders().split(","));
        List<String> allowMethodsStringSet = Arrays.asList(getAllowedMethods().split(","));
        List<String> allowOriginsStringSet = Arrays.asList(getAllowedOrigins().split(","));
        return new CORSConfiguration(false, allowOriginsStringSet, false, allowHeadersStringSet, allowMethodsStringSet);
    }

    public static WebsubSubscriptionConfiguration getDefaultWebsubSubscriptionConfiguration() {
        return new WebsubSubscriptionConfiguration(false, "",
                APIConstants.DEFAULT_WEBSUB_SIGNING_ALGO, APIConstants.DEFAULT_WEBSUB_SIGNATURE_HEADER);
    }



    /**
     * Used to get API name from synapse API Name
     *
     * @param api_version API name from synapse configuration
     * @return api name according to the tenant
     */
    public static String getAPINamefromRESTAPI(String api_version) {

        int index = api_version.indexOf("--");
        String api;
        if (index != -1) {
            api_version = api_version.substring(index + 2);
        }
        api = api_version.split(":")[0];
        index = api.indexOf("--");
        if (index != -1) {
            api = api.substring(index + 2);
        }
        return api;
    }

    // Take organization as a parameter
    public static Map<String, Environment> getEnvironments(String organization) throws APIManagementException {
        // get dynamic gateway environments read from database
        Map<String, Environment> envFromDB = ApiMgtDAO.getInstance().getAllEnvironments(organization).stream()
                .collect(Collectors.toMap(Environment::getName, env -> env));

        // clone and overwrite api-manager.xml environments with environments from DB if exists with same name
        Map<String, Environment> allEnvironments = new LinkedHashMap<>(getReadOnlyEnvironments());
        allEnvironments.putAll(envFromDB);
        return allEnvironments;
    }

    /**
     * Get gateway environments defined in the configuration: api-manager.xml
     * @return map of configured environments against environment name
     */
    public static Map<String, Environment> getReadOnlyEnvironments() {
        return ServiceHolder.getAPIManagerConfigurationService()
                .getAPIManagerConfiguration().getApiGatewayEnvironments();
    }

    /**
     * This method is used to get the actual endpoint password of an API from the hidden property
     * in the case where the handler APIEndpointPasswordRegistryHandler is enabled in registry.xml
     *
     * @param api      The API
     * @param registry The registry object
     * @return The actual password of the endpoint if exists
     * @throws RegistryException Throws if the api resource doesn't exist
     */
    private static String getActualEpPswdFromHiddenProperty(API api, Registry registry) throws RegistryException {

        String apiPath = org.wso2.carbon.apimgt.impl.utils.APIUtil.getAPIPath(api.getId());
        Resource apiResource = registry.get(apiPath);
        return apiResource.getProperty(APIConstants.REGISTRY_HIDDEN_ENDPOINT_PROPERTY);
    }

    /**
     * To check whether given role exist in the array of roles.
     *
     * @param userRoleList      Role list to check against.
     * @param accessControlRole Access Control Role.
     * @return true if the Array contains the role specified.
     */
    public static boolean compareRoleList(String[] userRoleList, String accessControlRole) {

        if (userRoleList != null) {
            for (String userRole : userRoleList) {
                if (userRole.equalsIgnoreCase(accessControlRole)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a map of API availability tiers of the tenant as defined in the underlying governance
     * registry.
     *
     * @return a Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, Tier> getTiers(int tenantId) throws APIManagementException {

        return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_SUB, tenantId);
    }

    public static Map<String, Tier> getTiersFromPolicies(String policyLevel, int tenantId) throws APIManagementException {
        Map<String, Tier> tierMap = new TreeMap<String, Tier>();
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        Policy[] policies;
        if (PolicyConstants.POLICY_LEVEL_SUB.equalsIgnoreCase(policyLevel)) {
            policies = apiMgtDAO.getSubscriptionPolicies(tenantId);
        } else if (PolicyConstants.POLICY_LEVEL_API.equalsIgnoreCase(policyLevel)) {
            policies = apiMgtDAO.getAPIPolicies(tenantId);
        } else if (PolicyConstants.POLICY_LEVEL_APP.equalsIgnoreCase(policyLevel)) {
            policies = apiMgtDAO.getApplicationPolicies(tenantId);
        } else {
            throw new APIManagementException("No such a policy type : " + policyLevel);
        }

        for (Policy policy : policies) {
            if (!APIConstants.UNLIMITED_TIER.equalsIgnoreCase(policy.getPolicyName())) {
                Tier tier = new Tier(policy.getPolicyName());
                tier.setDescription(policy.getDescription());
                tier.setDisplayName(policy.getDisplayName());
                Limit limit = policy.getDefaultQuotaPolicy().getLimit();
                tier.setTimeUnit(limit.getTimeUnit());
                tier.setUnitTime(limit.getUnitTime());
                tier.setQuotaPolicyType(policy.getDefaultQuotaPolicy().getType());

                //If the policy is a subscription policy
                if (policy instanceof SubscriptionPolicy) {
                    SubscriptionPolicy subscriptionPolicy = (SubscriptionPolicy) policy;
                    tier.setRateLimitCount(subscriptionPolicy.getRateLimitCount());
                    tier.setRateLimitTimeUnit(subscriptionPolicy.getRateLimitTimeUnit());
                    setBillingPlanAndCustomAttributesToTier(subscriptionPolicy, tier);
                    if (StringUtils.equals(subscriptionPolicy.getBillingPlan(), APIConstants.COMMERCIAL_TIER_PLAN)) {
                        tier.setMonetizationAttributes(subscriptionPolicy.getMonetizationPlanProperties());
                    }
                }

                if (limit instanceof RequestCountLimit) {

                    RequestCountLimit countLimit = (RequestCountLimit) limit;
                    tier.setRequestsPerMin(countLimit.getRequestCount());
                    tier.setRequestCount(countLimit.getRequestCount());
                } else if (limit instanceof BandwidthLimit){
                    BandwidthLimit bandwidthLimit = (BandwidthLimit) limit;
                    tier.setRequestsPerMin(bandwidthLimit.getDataAmount());
                    tier.setRequestCount(bandwidthLimit.getDataAmount());
                    tier.setBandwidthDataUnit(bandwidthLimit.getDataUnit());
                } else {
                    EventCountLimit eventCountLimit = (EventCountLimit) limit;
                    tier.setRequestCount(eventCountLimit.getEventCount());
                    tier.setRequestsPerMin(eventCountLimit.getEventCount());
                }
                if (PolicyConstants.POLICY_LEVEL_SUB.equalsIgnoreCase(policyLevel)) {
                    tier.setTierPlan(((SubscriptionPolicy) policy).getBillingPlan());
                }
                tierMap.put(policy.getPolicyName(), tier);
            } else {
                if (org.wso2.carbon.apimgt.impl.utils.APIUtil.isEnabledUnlimitedTier()) {
                    Tier tier = new Tier(policy.getPolicyName());
                    tier.setDescription(policy.getDescription());
                    tier.setDisplayName(policy.getDisplayName());
                    tier.setRequestsPerMin(Integer.MAX_VALUE);
                    tier.setRequestCount(Integer.MAX_VALUE);
                    if (isUnlimitedTierPaid(getTenantDomainFromTenantId(tenantId))) {
                        tier.setTierPlan(APIConstants.COMMERCIAL_TIER_PLAN);
                    } else {
                        tier.setTierPlan(APIConstants.BILLING_PLAN_FREE);
                    }

                    tierMap.put(policy.getPolicyName(), tier);
                }
            }
        }

        if (PolicyConstants.POLICY_LEVEL_SUB.equalsIgnoreCase(policyLevel)) {
            tierMap.remove(APIConstants.UNAUTHENTICATED_TIER);
        }
        return tierMap;
    }

    /**
     * Helper method to get tenantDomain from tenantId
     *
     * @param tenantId tenant Id
     * @return tenantId
     */
    public static String getTenantDomainFromTenantId(int tenantId) {

        RealmService realmService = ServiceHolder.getRealmService();

        if (realmService == null) {
            return MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
        }

        try {
            return realmService.getTenantManager().getDomain(tenantId);
        } catch (UserStoreException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static int getSuperTenantId() {

        return MultitenantConstants.SUPER_TENANT_ID;
    }

    public static Tier getPolicyByName(String policyLevel, String policyName, String organization)
            throws APIManagementException {

        int tenantId = org.wso2.carbon.apimgt.impl.utils.APIUtil.getInternalOrganizationId(organization);
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        Policy policy;
        if (PolicyConstants.POLICY_LEVEL_SUB.equalsIgnoreCase(policyLevel)) {
            policy = apiMgtDAO.getSubscriptionPolicy(policyName, tenantId);
        } else if (PolicyConstants.POLICY_LEVEL_API.equalsIgnoreCase(policyLevel)) {
            policy = apiMgtDAO.getAPIPolicy(policyName, tenantId);
        } else if (PolicyConstants.POLICY_LEVEL_APP.equalsIgnoreCase(policyLevel)) {
            policy = apiMgtDAO.getApplicationPolicy(policyName, tenantId);
        } else {
            throw new APIManagementException("No such a policy type : " + policyLevel);
        }
        if (policy != null) {
            if (!APIConstants.UNLIMITED_TIER.equalsIgnoreCase(policy.getPolicyName())) {
                Tier tier = new Tier(policy.getPolicyName());
                tier.setDescription(policy.getDescription());
                tier.setDisplayName(policy.getDisplayName());
                Limit limit = policy.getDefaultQuotaPolicy().getLimit();
                tier.setTimeUnit(limit.getTimeUnit());
                tier.setUnitTime(limit.getUnitTime());
                tier.setQuotaPolicyType(policy.getDefaultQuotaPolicy().getType());

                //If the policy is a subscription policy
                if (policy instanceof SubscriptionPolicy) {
                    SubscriptionPolicy subscriptionPolicy = (SubscriptionPolicy) policy;
                    tier.setRateLimitCount(subscriptionPolicy.getRateLimitCount());
                    tier.setRateLimitTimeUnit(subscriptionPolicy.getRateLimitTimeUnit());
                    setBillingPlanAndCustomAttributesToTier(subscriptionPolicy, tier);
                    if (StringUtils.equals(subscriptionPolicy.getBillingPlan(), APIConstants.COMMERCIAL_TIER_PLAN)) {
                        tier.setMonetizationAttributes(subscriptionPolicy.getMonetizationPlanProperties());
                    }
                }

                if (limit instanceof RequestCountLimit) {
                    RequestCountLimit countLimit = (RequestCountLimit) limit;
                    tier.setRequestsPerMin(countLimit.getRequestCount());
                    tier.setRequestCount(countLimit.getRequestCount());
                } else if (limit instanceof BandwidthLimit) {
                    BandwidthLimit bandwidthLimit = (BandwidthLimit) limit;
                    tier.setRequestsPerMin(bandwidthLimit.getDataAmount());
                    tier.setRequestCount(bandwidthLimit.getDataAmount());
                    tier.setBandwidthDataUnit(bandwidthLimit.getDataUnit());
                } else {
                    EventCountLimit eventCountLimit = (EventCountLimit) limit;
                    tier.setRequestCount(eventCountLimit.getEventCount());
                    tier.setRequestsPerMin(eventCountLimit.getEventCount());
                }
                if (PolicyConstants.POLICY_LEVEL_SUB.equalsIgnoreCase(policyLevel)) {
                    tier.setTierPlan(((SubscriptionPolicy) policy).getBillingPlan());
                }
                return tier;
            } else {
                if (org.wso2.carbon.apimgt.impl.utils.APIUtil.isEnabledUnlimitedTier()) {
                    Tier tier = new Tier(policy.getPolicyName());
                    tier.setDescription(policy.getDescription());
                    tier.setDisplayName(policy.getDisplayName());
                    tier.setRequestsPerMin(Integer.MAX_VALUE);
                    tier.setRequestCount(Integer.MAX_VALUE);
                    if (isUnlimitedTierPaid(getTenantDomainFromTenantId(tenantId))) {
                        tier.setTierPlan(APIConstants.COMMERCIAL_TIER_PLAN);
                    } else {
                        tier.setTierPlan(APIConstants.BILLING_PLAN_FREE);
                    }
                    return tier;
                }
            }
        }
        return null;
    }

    /**
     * Extract custom attributes and billing plan from subscription policy and set to tier.
     *
     * @param subscriptionPolicy - The SubscriptionPolicy object to extract details from
     * @param tier               - The Tier to set information into
     */
    public static void setBillingPlanAndCustomAttributesToTier(SubscriptionPolicy subscriptionPolicy, Tier tier) {

        //set the billing plan.
        tier.setTierPlan(subscriptionPolicy.getBillingPlan());

        //If the tier has custom attributes
        if (subscriptionPolicy.getCustomAttributes() != null &&
                subscriptionPolicy.getCustomAttributes().length > 0) {

            Map<String, Object> tierAttributes = new HashMap<String, Object>();
            try {
                String customAttr = new String(subscriptionPolicy.getCustomAttributes(), "UTF-8");
                JSONParser parser = new JSONParser();
                JSONArray jsonArr = (JSONArray) parser.parse(customAttr);
                Iterator jsonArrIterator = jsonArr.iterator();
                while (jsonArrIterator.hasNext()) {
                    JSONObject json = (JSONObject) jsonArrIterator.next();
                    tierAttributes.put(String.valueOf(json.get("name")), json.get("value"));
                }
                tier.setTierAttributes(tierAttributes);
            } catch (ParseException e) {
                log.error("Unable to convert String to Json", e);
                tier.setTierAttributes(null);
            } catch (UnsupportedEncodingException e) {
                log.error("Custom attribute byte array does not use UTF-8 character set", e);
                tier.setTierAttributes(null);
            }
        }
    }


    /**
     * Returns a map of API availability tiers of the tenant as defined in the underlying governance
     * registry.
     *
     * @param tierType type of the tiers
     * @param organization identifier of the organization
     * @return a Map of tier names and Tier objects - possibly empty
     * @throws APIManagementException if an error occurs when loading tiers from the registry
     */
    public static Map<String, Tier> getTiers(int tierType, String organization) throws APIManagementException {
        int tenantId = org.wso2.carbon.apimgt.impl.utils.APIUtil.getInternalOrganizationId(organization);
        if (tierType == APIConstants.TIER_API_TYPE) {
            return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_SUB, tenantId);
        } else if (tierType == APIConstants.TIER_RESOURCE_TYPE) {
            return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_API, tenantId);
        } else if (tierType == APIConstants.TIER_APPLICATION_TYPE) {
            return getTiersFromPolicies(PolicyConstants.POLICY_LEVEL_APP, tenantId);
        } else {
            throw new APIManagementException("No such a tier type : " + tierType);
        }
    }

    public static String getTenantAdminUserName(String tenantDomain) throws APIManagementException {

        try {
            int tenantId = ServiceHolder.getRealmService().getTenantManager().
                    getTenantId(tenantDomain);
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain);
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId);
            String adminUserName = ServiceHolder.getRealmService().getTenantUserRealm(tenantId)
                    .getRealmConfiguration().getAdminUserName();
            if (!tenantDomain.contentEquals(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)) {
                return adminUserName.concat("@").concat(tenantDomain);
            }
            return adminUserName;
        } catch (UserStoreException e) {
            throw new APIManagementException("Error in getting tenant admin username", e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }


    /**
     * Retrieves unfiltered list of all available tiers from registry.
     * Result will contains all the tiers including unauthenticated tier which is
     * filtered out in   getTiers}
     *
     * @param registry registry
     * @param tierLocation registry location of tiers config
     * @return Map<String, Tier> containing all available tiers
     * @throws RegistryException      when registry action fails
     * @throws XMLStreamException     when xml parsing fails
     * @throws APIManagementException when fails to retrieve tier attributes
     */
    private static Map<String, Tier> getAllTiers(Registry registry, String tierLocation, int tenantId)
            throws RegistryException, XMLStreamException, APIManagementException {
        // We use a treeMap here to keep the order
        Map<String, Tier> tiers = new TreeMap<String, Tier>();

        if (registry.resourceExists(tierLocation)) {
            Resource resource = registry.get(tierLocation);
            String content = new String((byte[]) resource.getContent(), Charset.defaultCharset());

            OMElement element = AXIOMUtil.stringToOM(content);
            OMElement assertion = element.getFirstChildWithName(APIConstants.ASSERTION_ELEMENT);
            Iterator policies = assertion.getChildrenWithName(APIConstants.POLICY_ELEMENT);

            while (policies.hasNext()) {
                OMElement policy = (OMElement) policies.next();
                OMElement id = policy.getFirstChildWithName(APIConstants.THROTTLE_ID_ELEMENT);

                String tierName = id.getText();

                // Constructing the tier object
                Tier tier = new Tier(tierName);
                tier.setPolicyContent(policy.toString().getBytes(Charset.defaultCharset()));

                if (id.getAttribute(APIConstants.THROTTLE_ID_DISPLAY_NAME_ELEMENT) != null) {
                    tier.setDisplayName(id.getAttributeValue(APIConstants.THROTTLE_ID_DISPLAY_NAME_ELEMENT));
                } else {
                    tier.setDisplayName(tierName);
                }
                String desc;
                try {
                    long requestPerMin = APIDescriptionGenUtil.getAllowedCountPerMinute(policy);
                    tier.setRequestsPerMin(requestPerMin);

                    long requestCount = APIDescriptionGenUtil.getAllowedRequestCount(policy);
                    tier.setRequestCount(requestCount);

                    long unitTime = APIDescriptionGenUtil.getTimeDuration(policy);
                    tier.setUnitTime(unitTime);

                    if (requestPerMin >= 1) {
                        desc = DESCRIPTION.replaceAll("\\[1\\]", Long.toString(requestPerMin));
                    } else {
                        desc = DESCRIPTION;
                    }
                    tier.setDescription(desc);

                } catch (APIManagementException ex) {
                    // If there is any issue in getting the request counts or the time duration, that means this tier
                    // information can not be used for throttling. Hence we log this exception and continue the flow
                    // to the next tier.
                    log.warn("Unable to get the request count/time duration information for : " + tier.getName() + ". "
                            + ex.getMessage());
                    continue;
                }

                // Get all the attributes of the tier.
                Map<String, Object> tierAttributes = APIDescriptionGenUtil.getTierAttributes(policy);
                if (!tierAttributes.isEmpty()) {
                    // The description, billing plan and the stop on quota reach properties are also stored as attributes
                    // of the tier attributes. Hence we extract them from the above attributes map.
                    Iterator<Entry<String, Object>> attributeIterator = tierAttributes.entrySet().iterator();
                    while (attributeIterator.hasNext()) {
                        Entry<String, Object> entry = attributeIterator.next();

                        if (APIConstants.THROTTLE_TIER_DESCRIPTION_ATTRIBUTE.equals(entry.getKey())
                                && entry.getValue() instanceof String) {

                            tier.setDescription((String) entry.getValue());

                            // We remove the attribute from the map
                            attributeIterator.remove();
                            continue;

                        }
                        if (APIConstants.THROTTLE_TIER_PLAN_ATTRIBUTE.equals(entry.getKey())
                                && entry.getValue() instanceof String) {

                            tier.setTierPlan((String) entry.getValue());

                            // We remove the attribute from the map
                            attributeIterator.remove();
                            continue;

                        }
                        if (APIConstants.THROTTLE_TIER_QUOTA_ACTION_ATTRIBUTE.equals(entry.getKey())
                                && entry.getValue() instanceof String) {

                            tier.setStopOnQuotaReached(Boolean.parseBoolean((String) entry.getValue()));

                            // We remove the attribute from the map
                            attributeIterator.remove();
                            // We do not need a continue since this is the last statement.

                        }
                    }
                    tier.setTierAttributes(tierAttributes);
                }
                tiers.put(tierName, tier);
            }
        }

        if (isEnabledUnlimitedTier()) {
            Tier tier = new Tier(APIConstants.UNLIMITED_TIER);
            tier.setDescription(APIConstants.UNLIMITED_TIER_DESC);
            tier.setDisplayName(APIConstants.UNLIMITED_TIER);
            tier.setRequestsPerMin(Long.MAX_VALUE);

            if (isUnlimitedTierPaid(getTenantDomainFromTenantId(tenantId))) {
                tier.setTierPlan(APIConstants.COMMERCIAL_TIER_PLAN);
            } else {
                tier.setTierPlan(APIConstants.BILLING_PLAN_FREE);
            }

            tiers.put(tier.getName(), tier);
        }

        return tiers;
    }

    /**
     * Retrieves filtered list of available tiers from registry. This method will not return Unauthenticated
     * tier in the list. Use  to retrieve all tiers without
     * any filtering.
     *
     * @param registry     registry to access tiers config
     * @param tierLocation registry location of tiers config
     * @return map containing available tiers
     * @throws APIManagementException when fails to retrieve tier attributes
     */
    private static Map<String, Tier> getTiers(Registry registry, String tierLocation, int tenantId) throws APIManagementException {

        Map<String, Tier> tiers = null;
        try {
            tiers = getAllTiers(registry, tierLocation, tenantId);
            tiers.remove(APIConstants.UNAUTHENTICATED_TIER);
        } catch (RegistryException e) {
            handleException(APIConstants.MSG_TIER_RET_ERROR, e);
        } catch (XMLStreamException e) {
            handleException(APIConstants.MSG_MALFORMED_XML_ERROR, e);
        } catch (APIManagementException e) {
            handleException("Unable to get tier attributes", e);
        } catch (Exception e) {

            // generic exception is caught to catch exceptions thrown from map remove method
            handleException("Unable to remove Unauthenticated tier from tiers list", e);
        }
        return tiers;
    }

    /**
     * Used to get unlimited throttling tier is enable
     *
     * @return condition of enable unlimited tier
     */
    public static boolean isEnabledUnlimitedTier() {

        ThrottleProperties throttleProperties = ServiceHolder
                .getAPIManagerConfigurationService().getAPIManagerConfiguration()
                .getThrottleProperties();
        return throttleProperties.isEnableUnlimitedTier();

    }

    public static Set<Tier> getAvailableTiers(Map<String, Tier> definedTiers, String tiers, String apiName) {

        Set<Tier> availableTier = new HashSet<Tier>();
        if (tiers != null && !"".equals(tiers)) {
            String[] tierNames = tiers.split("\\|\\|");
            for (String tierName : tierNames) {
                Tier definedTier = definedTiers.get(tierName);
                if (definedTier != null) {
                    availableTier.add(definedTier);
                } else {
                    log.warn("Unknown tier: " + tierName + " found on API: " + apiName);
                }
            }
        }
        return availableTier;
    }

    public static byte[] toByteArray(InputStream is) throws IOException {

        return IOUtils.toByteArray(is);
    }

    public static long ipToLong(String ipAddress) {

        long result = 0;
        String[] ipAddressInArray = ipAddress.split("\\.");
        for (int i = 3; i >= 0; i--) {
            long ip = Long.parseLong(ipAddressInArray[3 - i]);
            //left shifting 24,16,8,0 and bitwise OR
            //1. 192 << 24
            //1. 168 << 16
            //1. 1   << 8
            //1. 2   << 0
            result |= ip << (i * 8);

        }
        return result;
    }

    private static String getAPIMonetizationCategory(Set<Tier> tiers, String tenantDomain)
            throws APIManagementException {

        boolean isPaidFound = false;
        boolean isFreeFound = false;
        for (Tier tier : tiers) {
            if (isTierPaid(tier.getName(), tenantDomain)) {
                isPaidFound = true;
            } else {
                isFreeFound = true;

                if (isPaidFound) {
                    break;
                }
            }
        }

        if (!isPaidFound) {
            return APIConstants.API_CATEGORY_FREE;
        } else if (!isFreeFound) {
            return APIConstants.API_CATEGORY_PAID;
        } else {
            return APIConstants.API_CATEGORY_FREEMIUM;
        }
    }

    private static boolean isTierPaid(String tierName, String tenantDomainName) throws APIManagementException {

        String tenantDomain = tenantDomainName;
        if (tenantDomain == null) {
            tenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
        }
        if (APIConstants.UNLIMITED_TIER.equalsIgnoreCase(tierName)) {
            return isUnlimitedTierPaid(tenantDomain);
        }

        boolean isPaid = false;
        Tier tier = getPolicyByName(PolicyConstants.POLICY_LEVEL_SUB, tierName, tenantDomain);

        if (tier != null) {
            final Map<String, Object> tierAttributes = tier.getTierAttributes();

            if (tierAttributes != null) {
                String isPaidValue = tier.getTierPlan();

                if (isPaidValue != null && APIConstants.COMMERCIAL_TIER_PLAN.equals(isPaidValue)) {
                    isPaid = true;
                }
            }
        } else {
            throw new APIManagementException("Tier " + tierName + "cannot be found");
        }
        return isPaid;
    }

    private static boolean isUnlimitedTierPaid(String tenantDomain) throws APIManagementException {
        JSONObject apiTenantConfig = null;
        try {
            String content = null;

            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);

            int tenantId = ServiceHolder.getRealmService().getTenantManager()
                    .getTenantId(tenantDomain);
            Registry registry = ServiceHolder.getRegistryService()
                    .getConfigSystemRegistry(tenantId);

            if (registry.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                Resource resource = registry.get(APIConstants.API_TENANT_CONF_LOCATION);
                content = new String((byte[]) resource.getContent(), Charset.defaultCharset());
            }

            if (content != null) {
                JSONParser parser = new JSONParser();
                apiTenantConfig = (JSONObject) parser.parse(content);
            }
        } catch (UserStoreException e) {
            handleException("UserStoreException thrown when getting API tenant config from registry", e);
        } catch (RegistryException e) {
            handleException("RegistryException thrown when getting API tenant config from registry", e);
        } catch (ParseException e) {
            handleException("ParseException thrown when passing API tenant config from registry", e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }

        if (apiTenantConfig != null) {
            Object value = apiTenantConfig.get(APIConstants.API_TENANT_CONF_IS_UNLIMITED_TIER_PAID);

            if (value != null) {
                return Boolean.parseBoolean(value.toString());
            } else {
                throw new APIManagementException(APIConstants.API_TENANT_CONF_IS_UNLIMITED_TIER_PAID
                        + " config does not exist for tenant " + tenantDomain);
            }
        }

        return false;
    }

    public static Map<String, Tier> getTiers(String organization) throws APIManagementException {

        int requestedTenantId = getInternalOrganizationId(organization);

        if (requestedTenantId == 0) {
            return org.wso2.carbon.apimgt.impl.utils.APIUtil.getAdvancedSubsriptionTiers();
        } else {
            return org.wso2.carbon.apimgt.impl.utils.APIUtil.getAdvancedSubsriptionTiers(requestedTenantId);
        }
    }

    public static int getInternalOrganizationId(String organization) throws APIManagementException {
        return getOrganizationResolver().getInternalId(organization);
    }

    public static OrganizationResolver getOrganizationResolver() throws APIManagementException {

        OrganizationResolver resolver = ServiceHolder.getOrganizationResolver();
        if (resolver == null) {
            resolver = new OnPremResolver();
        }
        return resolver;
    }

    public static void clearTiersCache(String tenantDomain) {

        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);

            getTiersCache().removeAll();
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    private static Cache getTiersCache() {

        return Caching.getCacheManager(APIConstants.API_MANAGER_CACHE_MANAGER).
                getCache(APIConstants.TIERS_CACHE);
    }

    /**
     * This method returns the categories attached to the API
     *
     * @param artifact API artifact
     * @param tenantID tenant ID of API Provider
     * @return List<APICategory> list of categories
     */
    private static List<APICategory> getAPICategoriesFromAPIGovernanceArtifact(GovernanceArtifact artifact, int tenantID)
            throws GovernanceException, APIManagementException {

        String[] categoriesOfAPI = artifact.getAttributes(APIConstants.API_CATEGORIES_CATEGORY_NAME);

        List<APICategory> categoryList = new ArrayList<>();

        if (ArrayUtils.isNotEmpty(categoriesOfAPI)) {
            //category array retrieved from artifact has only the category name, therefore we need to fetch categories
            //and fill out missing attributes before attaching the list to the api
            String tenantDomain = getTenantDomainFromTenantId(tenantID);
            List<APICategory> allCategories = getAllAPICategoriesOfOrganization(tenantDomain);

            //todo-category: optimize this loop with breaks
            for (String categoryName : categoriesOfAPI) {
                for (APICategory category : allCategories) {
                    if (categoryName.equals(category.getName())) {
                        categoryList.add(category);
                        break;
                    }
                }
            }
        }
        return categoryList;
    }

    /**
     * This method is used to get the categories in a given tenant space
     *
     * @param organization organization name
     * @return categories in a given tenant space
     * @throws APIManagementException if failed to fetch categories
     */
    public static List<APICategory> getAllAPICategoriesOfOrganization(String organization)
            throws APIManagementException {

        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        return apiMgtDAO.getAllCategories(organization);
    }

    /**
     * Validates the API category names to be attached to an API
     *
     * @param categories
     * @param organization
     * @return
     */
    public static boolean validateAPICategories(List<APICategory> categories, String organization)
            throws APIManagementException {

        List<APICategory> availableCategories = getAllAPICategoriesOfOrganization(organization);
        for (APICategory category : categories) {
            if (!availableCategories.contains(category)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get scopes attached to the API.
     *
     * @param id   API identifier string
     * @param organization Organization
     * @return Scope key to Scope object mapping
     * @throws APIManagementException if an error occurs while getting scope attached to API
     */
//    public static Map<String, Scope> getAPIScopes(String id, String organization)
//            throws APIManagementException {
//        String currentApiUuid = id;
//        String migrateFromVersion = System.getProperty(Constants.ARG_MIGRATE_FROM_VERSION);
//        if (Constants.VERSION_4_0_0.equals(migrateFromVersion)) {
//            APIRevision apiRevision = ApiMgtDAO.getInstance().checkAPIUUIDIsARevisionUUID(id);
//            if (apiRevision != null && apiRevision.getApiUUID() != null) {
//                currentApiUuid = apiRevision.getApiUUID();
//            }
//        }
//
//        Set<String> scopeKeys = ApiMgtDAO.getInstance().getAPIScopeKeys(currentApiUuid);
//        return getScopes(scopeKeys, organization);
//    }

    /**
     * Get scopes for the given scope keys from authorization server.
     *
     * @param scopeKeys    Scope Keys
     * @param organization organization
     * @return Scope key to Scope object mapping
     * @throws APIManagementException if an error occurs while getting scopes using scope keys
     */
    public static Map<String, Scope> getScopes(Set<String> scopeKeys, String organization)
            throws APIManagementException {

        Map<String, Scope> scopeToKeyMap = new HashMap<>();
        for (String scopeKey : scopeKeys) {
            Scope scope = getScopeByName(scopeKey, organization);
            scopeToKeyMap.put(scopeKey, scope);
        }
        return scopeToKeyMap;
    }

    public static Scope getScopeByName(String scopeKey, String organization) throws APIManagementException {

        int tenantId = org.wso2.carbon.apimgt.impl.utils.APIUtil.getInternalIdFromTenantDomainOrOrganization(organization);
        return ScopesDAO.getInstance().getScope(scopeKey, tenantId);
    }

    public static KeyManagerConnectorConfiguration getKeyManagerConnectorConfigurationsByConnectorType(String type) {

        return ServiceHolder.getKeyManagerConnectorConfiguration(type);
    }

    public static List<ClaimMappingDto> getDefaultClaimMappings() {

        List<ClaimMappingDto> claimMappingDtoList = new ArrayList<>();
        try (InputStream resourceAsStream = org.wso2.carbon.apimgt.impl.utils.APIUtil.class.getClassLoader()
                .getResourceAsStream("claimMappings/default-claim-mapping.json")) {
            String content = IOUtils.toString(resourceAsStream);
            Map<String, String> claimMapping = new Gson().fromJson(content, Map.class);
            claimMapping.forEach((remoteClaim, localClaim) -> {
                claimMappingDtoList.add(new ClaimMappingDto(remoteClaim, localClaim));
            });
        } catch (IOException e) {
            log.error("Error while reading default-claim-mapping.json", e);
        }
        return claimMappingDtoList;
    }


    public static String getX509certificateContent(String certificate) {
        String content = certificate.replaceAll(APIConstants.BEGIN_CERTIFICATE_STRING, "")
                .replaceAll(APIConstants.END_CERTIFICATE_STRING, "");

        return content.trim();
    }

    public static X509Certificate retrieveCertificateFromContent(String base64EncodedCertificate)
            throws APIManagementException {

        if (base64EncodedCertificate != null) {
            try {
                base64EncodedCertificate = URLDecoder.decode(base64EncodedCertificate, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                String msg = "Error while URL decoding certificate";
                throw new APIManagementException(msg, e);
            }

            base64EncodedCertificate = org.wso2.carbon.apimgt.impl.utils.APIUtil.getX509certificateContent(base64EncodedCertificate);
            byte[] bytes = Base64.decodeBase64(base64EncodedCertificate);
            try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
                return X509Certificate.getInstance(inputStream);
            } catch (IOException | javax.security.cert.CertificateException e) {
                String msg = "Error while converting into X509Certificate";
                log.error(msg, e);
                throw new APIManagementException(msg, e);
            }
        }
        return null;
    }

    /**
     * Used to get access control allowed headers according to the api-manager.xml
     *
     * @return access control allowed headers string
     */
    public static String getAllowedHeaders() {

        return ServiceHolder.getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_HEADERS);
    }

    /**
     * Used to get access control allowed methods define in api-manager.xml
     *
     * @return access control allowed methods string
     */
    public static String getAllowedMethods() {

        return ServiceHolder.getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_METHODS);
    }

    /**
     * Used to get access control expose headers define in api-manager.xml
     *
     * @return access control expose headers string
     */
    public static String getAccessControlExposedHeaders() {

        return ServiceHolder.getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_EXPOSE_HEADERS);
    }

    /**
     * Used to get access control allowed credential define in api-manager.xml
     *
     * @return true if access control allow credential enabled
     */
    public static boolean isAllowCredentials() {

        String allowCredentials =
                ServiceHolder.getAPIManagerConfigurationService().getAPIManagerConfiguration().
                        getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_CREDENTIALS);
        return Boolean.parseBoolean(allowCredentials);
    }

    /**
     * Used to get CORS Configuration enabled from api-manager.xml
     *
     * @return true if CORS-Configuration is enabled in api-manager.xml
     */
    public static boolean isCORSEnabled() {

        String corsEnabled =
                ServiceHolder.getAPIManagerConfigurationService().getAPIManagerConfiguration().
                        getFirstProperty(APIConstants.CORS_CONFIGURATION_ENABLED);

        return Boolean.parseBoolean(corsEnabled);
    }

    /**
     * Used to get access control allowed origins define in api-manager.xml
     *
     * @return allow origins list defined in api-manager.xml
     */
    public static String getAllowedOrigins() {

        return ServiceHolder.getAPIManagerConfigurationService().getAPIManagerConfiguration().
                getFirstProperty(APIConstants.CORS_CONFIGURATION_ACCESS_CTL_ALLOW_ORIGIN);

    }

    /**
     * Used to get CORSConfiguration according to the API artifact
     *
     * @param artifact registry artifact for the API
     * @return CORS Configuration object extract from the artifact
     * @throws GovernanceException if attribute couldn't fetch from the artifact.
     */
    public static CORSConfiguration getCorsConfigurationFromArtifact(GovernanceArtifact artifact)
            throws GovernanceException {

        CORSConfiguration corsConfiguration = org.wso2.carbon.apimgt.impl.utils.APIUtil.getCorsConfigurationDtoFromJson(
                artifact.getAttribute(APIConstants.API_OVERVIEW_CORS_CONFIGURATION));
        if (corsConfiguration == null) {
            corsConfiguration = getDefaultCorsConfiguration();
        }
        return corsConfiguration;
    }

    public static WebsubSubscriptionConfiguration getWebsubSubscriptionConfigurationFromArtifact(
            GovernanceArtifact artifact) throws GovernanceException {
        WebsubSubscriptionConfiguration configuration = org.wso2.carbon.apimgt.impl.utils.APIUtil.getWebsubSubscriptionConfigurationDtoFromJson(
                artifact.getAttribute(APIConstants.API_OVERVIEW_WEBSUB_SUBSCRIPTION_CONFIGURATION));
        if (configuration == null) {
            configuration = getDefaultWebsubSubscriptionConfiguration();
        }
        return configuration;
    }

    public static void handleException(String msg) throws APIManagementException {

        log.error(msg);
        throw new APIManagementException(msg);
    }

    public static void handleException(String msg, Throwable t) throws APIManagementException {

        log.error(msg, t);
        throw new APIManagementException(msg, t);
    }

    public static List<Tenant> getAllTenantsWithSuperTenant() throws UserStoreException {

        Tenant[] tenants = ServiceHolder.getRealmService().getTenantManager().getAllTenants();
        ArrayList<Tenant> tenantArrayList = new ArrayList<Tenant>();
        Collections.addAll(tenantArrayList, tenants);
        Tenant superAdminTenant = new Tenant();
        superAdminTenant.setDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
        superAdminTenant.setId(org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_ID);
        superAdminTenant.setAdminName(CarbonConstants.REGISTRY_ANONNYMOUS_USERNAME);
        tenantArrayList.add(superAdminTenant);
        return tenantArrayList;
    }







}
