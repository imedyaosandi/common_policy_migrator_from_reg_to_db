package org.wso2.carbon.common.policy.migration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.OperationPolicyData;
import org.wso2.carbon.apimgt.api.model.OperationPolicyDefinition;
import org.wso2.carbon.apimgt.api.model.OperationPolicySpecification;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.core.ServerStartupObserver;
import org.wso2.carbon.common.policy.migration.util.UserDBUtil;
import org.wso2.carbon.common.policy.migration.util.RegDBUtil;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.exceptions.ResourceNotFoundException;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.common.policy.migration.util.APIUtil;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.common.policy.migration.client.internal.ServiceHolder;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;
import org.wso2.carbon.common.policy.migration.migrator.Utility;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class APIMCommonPolicyMigrationClient implements ServerStartupObserver{
    private static final Log log = LogFactory.getLog(APIMCommonPolicyMigrationClient.class);
    private TenantManager tenantManager;
    protected Registry registry;

    @Override
    public void completingServerStartup() {

    }

    @Override
    public void completedServerStartup() {
        try {
            APIMgtDBUtil.initialize();
            UserDBUtil.initialize();
            RegDBUtil.initialize();
        } catch (Exception e) {
            log.error("WSO2 API-M Migration Task : Error occurred while initializing DB Util ", e);
        }
        executePolicyMigration();
    }

    public void executePolicyMigration(){
        tenantManager = ServiceHolder.getRealmService().getTenantManager();
        String inSequencePath = org.wso2.carbon.apimgt.impl.APIConstants.API_CUSTOM_SEQUENCE_LOCATION
                + RegistryConstants.PATH_SEPARATOR
                + APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN;
        String outSequencePath = org.wso2.carbon.apimgt.impl.APIConstants.API_CUSTOM_SEQUENCE_LOCATION
                + RegistryConstants.PATH_SEPARATOR
                + APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT;
        String faultSequencePath = org.wso2.carbon.apimgt.impl.APIConstants.API_CUSTOM_SEQUENCE_LOCATION
                + RegistryConstants.PATH_SEPARATOR
                + org.wso2.carbon.apimgt.impl.APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT;
        log.info("WSO2 API-M Migration Task : Starting policy migration. This will migrate the common policies added from the registry in 3.2.0 to the DB.");
        try{
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                try{
                    log.info("WSO2 API-M Migration Task : Starting policy migration for tenant domain: "+ tenant.getDomain());
                    int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                    APIUtil.loadTenantRegistry(apiTenantId);
                    Utility.startTenantFlow(tenant.getDomain(), apiTenantId, MultitenantUtils
                            .getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                    this.registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                    commonPolicyMigrator(inSequencePath,tenant,APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN);
                    log.info("*************************** Completed IN flow common policy migration for tenant domain: " + tenant.getDomain()+"***************************");
                    commonPolicyMigrator(outSequencePath,tenant,APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT);
                    log.info("*************************** Completed OUT flow common policy migration for tenant domain: " + tenant.getDomain()+"***************************");
                    commonPolicyMigrator(faultSequencePath,tenant,APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT);
                    log.info("*************************** Completed FAULT flow common policy migration for tenant domain: " + tenant.getDomain()+"***************************");
                    log.info("*************************** Successfully Completed common policy migration for tenant domain: " + tenant.getDomain()+"***************************");
                } catch (RegistryException e) {
                log.error("WSO2 API-M Migration Task : Error while initializing the registry, tenant domain: "
                        + tenant.getDomain(), e);
                //isError = true;
            } catch (APIManagementException e) {
                    throw new RuntimeException(e);
                }

            }

        }catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
        }
        log.info("*************************** Successfully Completed common policy migration for all tenants ***************************");
    }

    public void commonPolicyMigrator(String path, Tenant tenant, String flow) {
        org.wso2.carbon.registry.api.Collection seqCollection = null;
        log.info("==================== Started "+ path + " common policy migration for tenant domain: "+tenant.getDomain()+" ===============");
        try {
            seqCollection = (org.wso2.carbon.registry.api.Collection) registry.get(path);
        } catch (ResourceNotFoundException e) {
            log.warn("WSO2 API-M Migration Task : Resource does not exist for " + path
                    + " for tenant domain:" + tenant.getDomain());
        } catch (RegistryException e) {
            throw new RuntimeException(e);
        }
        if (seqCollection != null) {
            String[] childPaths = new String[0];
            try {
                childPaths = seqCollection.getChildren();
            } catch (org.wso2.carbon.registry.api.RegistryException e) {
                throw new RuntimeException(e);
            }
            for (String childPath : childPaths) {
                try {
                    String fileName=childPath.substring(childPath.lastIndexOf("/")+1);
                    String[] policiesToSkip = {"json_to_xml_in_message.xml", "json_validator.xml", "preserve_accept_header.xml", "debug_in_flow.xml", "disable_chunking.xml", "log_in_message.xml", "regex_policy.xml", "xml_to_json_in_message.xml", "xml_validator.xml", "debug_json_fault.xml", "json_fault.xml", "apply_accept_header.xml", "debug_out_flow.xml", "disable_chunking.xml", "json_to_xml_out_message.xml", "log_out_message.xml", "xml_to_json_out_message.xml"};

                    if(!Arrays.asList(policiesToSkip).contains(fileName)){
                        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
                        Set<String> existingPolicies = apiMgtDAO.getCommonOperationPolicyNames(tenant.getDomain());
                            Resource sequence = registry.get(childPath);
                            DocumentBuilderFactory factory = APIUtil.getSecuredDocumentBuilder();
                            DocumentBuilder builder = factory.newDocumentBuilder();
                            String content = new String((byte[]) sequence.getContent(), Charset.defaultCharset());
                            ////to-do: remove sequence tag
                            String extractedFileContent = content.replaceAll("<sequence[^>]*>", "")
                                    .replaceAll("</sequence>", "");
                            Document doc = builder.parse(new InputSource(new StringReader(content)));
                            Element sequenceElement= (Element) doc.getElementsByTagNameNS("http://ws.apache.org/ns/synapse", "sequence").item(0);
                            String policyName=sequenceElement.getAttribute("name");
                        if (!existingPolicies.contains(policyName.concat("_v1"))) {
                            log.info("----------------- This is the policy content:   " + extractedFileContent);
                            OperationPolicyData policyData = generateOperationPolicyDataObject(tenant.getDomain(),policyName,extractedFileContent,flow);
                            apiMgtDAO.addCommonOperationPolicy(policyData);
                            log.info("*************************** Policy : "+ policyName +" Added to tenant domain " + tenant.getDomain()+"***************************");
                        }else{
                            log.info("A common policy with name "+ policyName.concat("_v1") + " exists." );
                        }

                    }else{
                        log.info("This common policy " + fileName +" is a default policy that already exists in 4.2.0. Hence skipping");
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static OperationPolicyData generateOperationPolicyDataObject(String organization,
                                                                        String policyName,
                                                                        String policyDefinitionString, String flow) {

        OperationPolicySpecification policySpecification = new OperationPolicySpecification();
        policySpecification.setCategory(OperationPolicySpecification.PolicyCategory.Mediation);
        policySpecification.setName(policyName);
        policySpecification.setDisplayName(policyName);
        policySpecification.setDescription("This is a mediation policy migrated to common operation policy from 3.2.0.");

        ArrayList<String> gatewayList = new ArrayList<>();
        gatewayList.add(APIConstants.OPERATION_POLICY_SUPPORTED_GATEWAY_SYNAPSE);
        policySpecification.setSupportedGateways(gatewayList);

        ArrayList<String> supportedAPIList = new ArrayList<>();
        supportedAPIList.add(APIConstants.OPERATION_POLICY_SUPPORTED_API_TYPE_HTTP);
        supportedAPIList.add(APIConstants.OPERATION_POLICY_SUPPORTED_API_TYPE_SOAP);
        supportedAPIList.add(APIConstants.OPERATION_POLICY_SUPPORTED_API_TYPE_SOAPTOREST);
        supportedAPIList.add(APIConstants.OPERATION_POLICY_SUPPORTED_API_TYPE_GRAPHQL);
        policySpecification.setSupportedApiTypes(supportedAPIList);

        ArrayList<String> applicableFlows = new ArrayList<>();
        if(flow=="in"){
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST);
        }
        if (flow.equals("out")) {
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE);
        }
        if (flow.equals("fault")){
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_FAULT);
        }
        policySpecification.setApplicableFlows(applicableFlows);

        OperationPolicyData policyData = new OperationPolicyData();
        policyData.setOrganization(organization);
        policyData.setSpecification(policySpecification);

        if (policyDefinitionString != null) {
            OperationPolicyDefinition policyDefinition = new OperationPolicyDefinition();
            policyDefinition.setContent(policyDefinitionString);
            policyDefinition.setGatewayType(OperationPolicyDefinition.GatewayType.Synapse);
            policyDefinition.setMd5Hash(org.wso2.carbon.apimgt.impl.utils.APIUtil.getMd5OfOperationPolicyDefinition(policyDefinition));
            policyData.setSynapsePolicyDefinition(policyDefinition);
        }

        policyData.setMd5Hash(org.wso2.carbon.apimgt.impl.utils.APIUtil.getMd5OfOperationPolicy(policyData));

        return policyData;
    }
}