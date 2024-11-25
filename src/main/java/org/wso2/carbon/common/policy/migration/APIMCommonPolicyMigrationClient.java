package org.wso2.carbon.common.policy.migration;

import org.apache.commons.codec.digest.DigestUtils;
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
import java.util.*;
import java.util.regex.*;

public class APIMCommonPolicyMigrationClient implements ServerStartupObserver {
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

    public void executePolicyMigration() {
        tenantManager = ServiceHolder.getRealmService().getTenantManager();
        String content;
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
        try {
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                try {
                    log.info("WSO2 API-M Migration Task : Starting policy migration for tenant domain: " + tenant.getDomain());
                    int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                    APIUtil.loadTenantRegistry(apiTenantId);
                    Utility.startTenantFlow(tenant.getDomain(), apiTenantId, MultitenantUtils
                            .getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                    this.registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                    //Retrieve policy names and content from the registry
                    log.info("WSO2 API-M Migration Task : Reading IN policy list for tenant domain: " + tenant.getDomain());
                    Map<String, String> inPolicyList = createPolicyList(inSequencePath, tenant);
                    log.info("WSO2 API-M Migration Task : Reading OUT policy list for tenant domain: " + tenant.getDomain());
                    Map<String, String> outPolicyList = createPolicyList(outSequencePath, tenant);
                    log.info("WSO2 API-M Migration Task : Reading FAULT policy list for tenant domain: " + tenant.getDomain());
                    Map<String, String> faultPolicyList = createPolicyList(faultSequencePath, tenant);

                    Set<String> inPolicyNames = new HashSet<>(inPolicyList.keySet());
                    Set<String> outPolicyNames = new HashSet<>(outPolicyList.keySet());
                    Set<String> faultPolicyNames = new HashSet<>(faultPolicyList.keySet());

                    Set<String> commonInOutPolicies = new HashSet<>(inPolicyNames);
                    Set<String> commonInFaultPolicies = new HashSet<>(inPolicyNames);
                    Set<String> commonOutFaultPolicies = new HashSet<>(outPolicyNames);

                    commonInOutPolicies.retainAll(outPolicyNames);
                    commonInFaultPolicies.retainAll(faultPolicyNames);
                    commonOutFaultPolicies.retainAll(faultPolicyNames);
                    Set<String> commonInOutFaultPolicies = new HashSet<>(commonInOutPolicies);
                    commonInOutFaultPolicies.retainAll(faultPolicyNames);
                    commonInOutPolicies.removeAll(commonInOutFaultPolicies);
                    commonOutFaultPolicies.removeAll(commonInOutFaultPolicies);
                    commonInFaultPolicies.removeAll(commonInOutFaultPolicies);

                    inPolicyNames.removeAll(commonInOutPolicies);
                    inPolicyNames.removeAll(commonInFaultPolicies);
                    inPolicyNames.removeAll(commonInOutFaultPolicies);
                    log.info("IN only policies : " + inPolicyNames);

                    outPolicyNames.removeAll(commonInOutPolicies);
                    outPolicyNames.removeAll(commonOutFaultPolicies);
                    outPolicyNames.removeAll(commonInOutFaultPolicies);
                    log.info("OUT only policies : " + outPolicyNames);

                    faultPolicyNames.removeAll(commonInFaultPolicies);
                    faultPolicyNames.removeAll(commonOutFaultPolicies);
                    faultPolicyNames.removeAll(commonInOutFaultPolicies);
                    log.info("FAULT only policies : " + faultPolicyNames);
                    log.info("IN and OUT policies: " + commonInOutPolicies);
                    log.info("FAULT and OUT policies: " + commonOutFaultPolicies);
                    log.info("FAULT and IN policies: " + commonInFaultPolicies);
                    log.info("IN,FAULT and OUT policies: " + commonInOutFaultPolicies);

                    //handling in only policies
                    if (!inPolicyNames.isEmpty()) {
                        log.info("IN only policies : " + inPolicyNames + " for tenant: " + tenant.getDomain() + ". Migrating them only as REQUEST mediation policies");
                        for (String policy : inPolicyNames) {
                            log.info("Migration for Common Policy: "+ policy +" started.");
                            content = inPolicyList.get(policy);
                            log.info("Migrating policy : " + policy + " for tenant: " + tenant.getDomain());
                            commonPolicyMigrator(content, policy, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN);
                        }
                        log.info("*************************** Completed IN flow common policy migration for tenant domain: " + tenant.getDomain() + "***************************");
                    } else {
                        log.info("IN only policies not available.");
                    }

                    //handling out only policies
                    if (!outPolicyNames.isEmpty()) {
                        log.info("OUT only policies : " + outPolicyNames + " for tenant: " + tenant.getDomain() + ". Migrating them only as RESPONSE mediation policies");
                        for (String policy : outPolicyNames) {
                            log.info("Migration for Common Policy: "+ policy +" started.");
                            content = outPolicyList.get(policy);
                            log.info("Migrating policy : " + policy + " for tenant: " + tenant.getDomain());
                            commonPolicyMigrator(content, policy, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT);
                        }
                        log.info("*************************** Completed OUT flow common policy migration for tenant domain: " + tenant.getDomain() + "***************************");
                    } else {
                        log.info("OUT only policies not available.");
                    }
                    //handling Fault only policies
                    if (!faultPolicyNames.isEmpty()) {
                        log.info("FAULT only policies : " + faultPolicyNames + " for tenant: " + tenant.getDomain() + ". Migrating them only as FAULT mediation policies");
                        for (String policy : faultPolicyNames) {
                            log.info("Migration for Common Policy: "+ policy +" started.");
                            content = faultPolicyList.get(policy);
                            log.info("Migrating policy : " + policy + " for tenant: " + tenant.getDomain());
                            commonPolicyMigrator(content, policy, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT);
                        }
                        log.info("*************************** Completed FAULT flow common policy migration for tenant domain: " + tenant.getDomain() + "***************************");
                    } else {
                        log.info("FAULT only policies not available.");
                    }
                    //handling IN-OUT only policies
                    if (!commonInOutPolicies.isEmpty()) {
                        log.info("Common policies for IN and OUT : " + commonInOutPolicies + " for tenant: " + tenant.getDomain());
                        for (String policy : commonInOutPolicies) {
                            log.info("Migration for Common Policy: "+ policy +" started.");
                            String inPolicyMD5 = getMd5OfPolicyContent(inPolicyList.get(policy));
                            String outPolicyMD5 = getMd5OfPolicyContent(outPolicyList.get(policy));
                            if (inPolicyMD5.equals(outPolicyMD5)) {
                                log.info("Content is same, hence migrating the policy to both REQUEST and RESPONSE");
                                String flow = "IN-OUT";
                                commonPolicyMigrator(inPolicyList.get(policy), policy, tenant, flow);
                            } else {
                                log.info("Policy exists with same name but content is different.");
                                commonPolicyMigrator(inPolicyList.get(policy), policy, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN);
                                String policyName = policy + "_out";
                                commonPolicyMigrator(outPolicyList.get(policy), policyName, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT);
                            }
                        }
                        log.info("*************************** Completed IN-OUT flow common policy migration for tenant domain: " + tenant.getDomain() + "***************************");
                    } else {
                        log.info("no same name policy in IN and OUT");
                    }
                    //handling IN-FAULT only policies
                    if (!commonInFaultPolicies.isEmpty()) {
                        log.info("Common policies for IN and FAULT : " + commonInFaultPolicies + " for tenant: " + tenant.getDomain());
                        for (String policy : commonInFaultPolicies) {
                            log.info("Migration for Common Policy: "+ policy +" started.");
                            String inPolicyMD5 = getMd5OfPolicyContent(inPolicyList.get(policy));
                            String faultPolicyMD5 = getMd5OfPolicyContent(faultPolicyList.get(policy));
                            if (inPolicyMD5.equals(faultPolicyMD5)) {
                                log.info("Content is same, hence migrating the policy to both IN and FAULT");
                                String flow = "IN-FAULT";
                                commonPolicyMigrator(inPolicyList.get(policy), policy, tenant, flow);
                            } else {
                                log.info("Policy exists with same name but content is different.");
                                commonPolicyMigrator(inPolicyList.get(policy), policy, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN);
                                String policyName = policy + "_fault";
                                commonPolicyMigrator(faultPolicyList.get(policy), policyName, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT);
                            }
                        }
                        log.info("*************************** Completed IN-FAULT flow common policy migration for tenant domain: " + tenant.getDomain() + "***************************");
                    } else {
                        log.info("no same name policy in IN and FAULT");
                    }
                    //handling OUT-FAULT only policies
                    if (!commonOutFaultPolicies.isEmpty()) {
                        log.info("Common policies for FAULT and OUT : " + commonOutFaultPolicies + " for tenant: " + tenant.getDomain());
                        for (String policy : commonOutFaultPolicies) {
                            log.info("Migration for Common Policy: "+ policy +" started.");
                            String outPolicyMD5 = getMd5OfPolicyContent(outPolicyList.get(policy));
                            String faultPolicyMD5 = getMd5OfPolicyContent(faultPolicyList.get(policy));
                            if (faultPolicyMD5.equals(outPolicyMD5)) {
                                log.info("Content is same, hence migrating the policy to both FAULT and OUT");
                                String flow = "OUT-FAULT";
                                commonPolicyMigrator(outPolicyList.get(policy), policy, tenant, flow);
                            } else {
                                log.info("Policy exists with same name but content is different.");
                                commonPolicyMigrator(outPolicyList.get(policy), policy, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT);
                                String policyName = policy + "_fault";
                                commonPolicyMigrator(faultPolicyList.get(policy), policyName, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT);
                            }
                        }
                        log.info("*************************** Completed OUT-FAULT flow common policy migration for tenant domain: " + tenant.getDomain() + "***************************");
                    } else {
                        log.info("no same name policy in in and out");
                    }
                    //handling IN-OUT-FAULT only policies
                    if (!commonInOutFaultPolicies.isEmpty()) {
                        log.info("Common policies for IN, OUT and FAULT : " + commonInOutFaultPolicies + " for tenant: " + tenant.getDomain());
                        for (String policy : commonInOutFaultPolicies) {
                            log.info("Migration for Common Policy: "+ policy +" started.");
                            String inPolicyMD5 = getMd5OfPolicyContent(inPolicyList.get(policy));
                            String outPolicyMD5 = getMd5OfPolicyContent(outPolicyList.get(policy));
                            String faultPolicyMD5 = getMd5OfPolicyContent(faultPolicyList.get(policy));
                            if (inPolicyMD5.equals(outPolicyMD5) && inPolicyMD5.equals(faultPolicyMD5)) {
                                log.info("Content is same, hence migrating the policy to IN, OUT and FAULT");
                                String flow = "IN-OUT-FAULT";
                                commonPolicyMigrator(outPolicyList.get(policy), policy, tenant, flow);
                            } else if (inPolicyMD5.equals(outPolicyMD5)) {
                                log.info("policy exists with same name in IN,OUT FAULT but content same only for for IN and OUT");
                                String flow = "IN-OUT";
                                commonPolicyMigrator(outPolicyList.get(policy), policy, tenant, flow);
                                String policyName = policy + "_fault";
                                commonPolicyMigrator(faultPolicyList.get(policy), policyName, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT);
                            } else if (inPolicyMD5.equals(faultPolicyMD5)) {
                                log.info("policy exists with same name in IN,OUT FAULT but content same only for for IN and FAULT");
                                String flow = "IN-FAULT";
                                commonPolicyMigrator(inPolicyList.get(policy), policy, tenant, flow);
                                String policyName = policy + "_out";
                                commonPolicyMigrator(outPolicyList.get(policy), policyName, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT);
                            } else if (outPolicyMD5.equals(faultPolicyMD5)) {
                                log.info("policy exists with same name in IN,OUT FAULT but content same only for for OUT and FAULT");
                                String flow = "OUT-FAULT";
                                commonPolicyMigrator(outPolicyList.get(policy), policy, tenant, flow);
                                String policyName = policy + "_in";
                                commonPolicyMigrator(inPolicyList.get(policy), policyName, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN);
                            } else {
                                log.info("policy exists with same name in IN,OUT and FAULT but content is different");
                                commonPolicyMigrator(inPolicyList.get(policy), policy, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN);
                                String policyName = policy + "_out";
                                commonPolicyMigrator(outPolicyList.get(policy), policyName, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT);
                                policyName = policy + "_fault";
                                commonPolicyMigrator(faultPolicyList.get(policy), policyName, tenant, APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT);
                            }
                        }
                        log.info("*************************** Completed IN, OUT and FAULT flow common policy migration for tenant domain: " + tenant.getDomain() + "***************************");
                    } else {
                        log.info("no same name policy in IN, OUT and FAULT");
                    }
                    log.info("*************************** Successfully Completed common policy migration for tenant domain: " + tenant.getDomain() + "***************************");
                } catch (RegistryException e) {
                    log.error("WSO2 API-M Migration Task : Error while initializing the registry, tenant domain: "
                            + tenant.getDomain(), e);
                    //isError = true;
                } catch (APIManagementException e) {
                    throw new RuntimeException(e);
                }

            }

        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
        }
        log.info("*************************** Successfully Completed common policy migration for all tenants ***************************");
    }

    public void commonPolicyMigrator(String content, String policyName, Tenant tenant, String flow) {

        try {
            ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
            Set<String> existingPolicies = apiMgtDAO.getCommonOperationPolicyNames(tenant.getDomain());

            // remove xml declaration if present
            String regex = "^\\s*<\\?xml.*?\\?>";
            String extractedFileContent = content.replaceAll(regex, "").trim();
            // only the beginning and end sequence tags should get removed
            String seq_regex = "^<sequence[^>]*>(.*?)</sequence>$";
            Pattern pattern = Pattern.compile(seq_regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(extractedFileContent);
            String FileContent = extractedFileContent;
            if (matcher.find()) {
                // Return the content inside the outer <sequence>...</sequence>
                FileContent = matcher.group(1).trim();
            }
            if (!existingPolicies.contains(policyName.concat("_v1"))) {
                log.info("----------------- This is the policy: " + policyName.concat("_v1") + " content:   " + FileContent);
                OperationPolicyData policyData = generateOperationPolicyDataObject(tenant.getDomain(), policyName, FileContent, flow);
                apiMgtDAO.addCommonOperationPolicy(policyData);
                log.info("*************************** Policy : " + policyName + " Added to tenant domain " + tenant.getDomain() + "***************************");
            } else {
                log.info("A common policy with name " + policyName.concat("_v1") + " exists.");
            }

        } catch (APIManagementException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static OperationPolicyData generateOperationPolicyDataObject(String organization, String policyName,
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
        //supportedAPIList.add(APIConstants.OPERATION_POLICY_SUPPORTED_API_TYPE_GRAPHQL); This is not supported now
        policySpecification.setSupportedApiTypes(supportedAPIList);

        ArrayList<String> applicableFlows = new ArrayList<>();
        if (flow.equals("in")) {
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST);
        }
        if (flow.equals("out")) {
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE);
        }
        if (flow.equals("fault")) {
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_FAULT);
        }
        if (flow.equals("IN-OUT")) {
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST);
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE);
        }
        if (flow.equals("IN-FAULT")) {
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST);
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_FAULT);
        }
        if (flow.equals("OUT-FAULT")) {
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE);
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_FAULT);
        }
        if (flow.equals("IN-OUT-FAULT")) {
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST);
            applicableFlows.add(APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE);
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

    public Map<String, String> createPolicyList(String path, Tenant tenant) {
        org.wso2.carbon.registry.api.Collection seqCollection = null;
        //Set<String> policyList=new HashSet<>();
        Map<String, String> policyList = new HashMap<>();
        //String policyName = null;
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
                    String fileName = childPath.substring(childPath.lastIndexOf("/") + 1);
                    String[] policiesToSkip = {"json_to_xml_in_message.xml", "json_validator.xml", "preserve_accept_header.xml", "debug_in_flow.xml", "disable_chunking.xml", "log_in_message.xml", "regex_policy.xml", "xml_to_json_in_message.xml", "xml_validator.xml", "debug_json_fault.xml", "json_fault.xml", "apply_accept_header.xml", "debug_out_flow.xml", "disable_chunking.xml", "json_to_xml_out_message.xml", "log_out_message.xml", "xml_to_json_out_message.xml"};
                    if (!Arrays.asList(policiesToSkip).contains(fileName)) {
                        Resource sequence = registry.get(childPath);
                        DocumentBuilderFactory factory = APIUtil.getSecuredDocumentBuilder();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        String content = new String((byte[]) sequence.getContent(), Charset.defaultCharset());
                        Document doc = builder.parse(new InputSource(new StringReader(content)));
                        Element sequenceElement = (Element) doc.getElementsByTagNameNS("http://ws.apache.org/ns/synapse", "sequence").item(0);
                        String policyName = sequenceElement.getAttribute("name");
                        log.info("Adding policy: " + policyName + " in path " + path + " to policy list for tenant domain:" + tenant.getDomain());
                        policyList.put(policyName, content);
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return policyList;
    }

    public static String getMd5OfPolicyContent(String content) {

        String md5Hash = "";

        if (content != null) {
            md5Hash = DigestUtils.md5Hex(content);
        }
        return md5Hash;
    }
}