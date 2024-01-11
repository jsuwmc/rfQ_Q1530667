package com.tmobile.pacman.api.admin.repository.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.tmobile.pacman.api.admin.common.AdminConstants;
import com.tmobile.pacman.api.admin.domain.AssetGroupExceptionDetailsRequest;
import com.tmobile.pacman.api.admin.domain.PluginParameters;
import com.tmobile.pacman.api.admin.dto.PluginNotificationDto;
import com.tmobile.pacman.api.admin.dto.PolicyExemptionNotificationDto;
import com.tmobile.pacman.api.admin.dto.StickyExNotificationRequest;
import com.tmobile.pacman.api.admin.repository.model.AssetGroupException;
import com.tmobile.pacman.api.admin.repository.model.Policy;
import com.tmobile.pacman.api.admin.repository.model.PolicyExemption;
import com.tmobile.pacman.api.admin.util.AdminUtils;
import com.tmobile.pacman.api.commons.dto.NotificationBaseRequest;
import com.tmobile.pacman.api.commons.repo.PacmanRdsRepository;
import com.tmobile.pacman.api.commons.utils.PacHttpUtils;
import com.tmobile.pacman.api.commons.utils.ThreadLocalUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.tmobile.pacman.api.admin.common.AdminConstants.CREATE_EXCEPTION_EVENT_NAME;
import static com.tmobile.pacman.api.admin.common.AdminConstants.CREATE_STICKY_EXCEPTION_SUBJECT;
import static com.tmobile.pacman.api.admin.common.AdminConstants.DELETE_EXCEPTION_EVENT_NAME;
import static com.tmobile.pacman.api.admin.common.AdminConstants.DELETE_STICKY_EXCEPTION_SUBJECT;
import static com.tmobile.pacman.api.admin.common.AdminConstants.DISABLED_CAPS;
import static com.tmobile.pacman.api.admin.common.AdminConstants.ENABLED_CAPS;
import static com.tmobile.pacman.api.admin.common.AdminConstants.POLICY_ACTION_EVENT_NAME;
import static com.tmobile.pacman.api.admin.common.AdminConstants.POLICY_ACTION_SUBJECT;
import static com.tmobile.pacman.api.admin.common.AdminConstants.UPDATE_EXCEPTION_EVENT_NAME;
import static com.tmobile.pacman.api.admin.common.AdminConstants.UPDATE_STICKY_EXCEPTION_SUBJECT;
import static com.tmobile.pacman.api.admin.util.AdminUtils.addDays;
import static com.tmobile.pacman.api.commons.Constants.Actions;
import static com.tmobile.pacman.api.commons.Constants.NotificationTypes;
import static com.tmobile.pacman.api.commons.Constants.OPEN;

@Component
public class NotificationServiceImpl implements NotificationService {

    private static final String DATE_FORMAT = "MMM dd, YYYY";
    private static final String POLICY_ACTION_ERROR_MSG = "Error triggering lambda function url, notification " +
            "request not sent for policy action. Error - {}";
    private static final String CREATE_PLUGIN_TEMPLATE = "%s Plugin with ID %s was created by %s";;
    private static final String DELETE_PLUGIN_TEMPLATE = "%s Plugin with ID %s was deleted by %s";;

    @Value("${notification.lambda.function.url}")
    private String notificationUrl;

    /** The ui host. */
    @Value("${pacman.host}")
    private String hostName;

    @Autowired
    PacmanRdsRepository pacmanRdsRepository;
    @Autowired
    private ObjectMapper mapper;

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Async
    public void triggerNotificationForCreateStickyEx(AssetGroupExceptionDetailsRequest assetGroupExceptionDetails, String userId, String subject, List<String> policyIds, Actions action) {
        try {
            Gson gson = new Gson();
            if (!policyIds.isEmpty()) {
                String combinedPolicyIdStr = String.join(",", policyIds.stream().map(str -> "'" + str + "'").collect(Collectors.toList()));
                List<Map<String, Object>> policyIdPolicyNameList = pacmanRdsRepository.getDataFromPacman("SELECT policyId, policyDisplayName FROM cf_PolicyTable WHERE policyId in (" + combinedPolicyIdStr + ")");
                String combinedPolicyNameStr = policyIdPolicyNameList.stream().map(obj -> (String) obj.get("policyDisplayName")).collect(Collectors.joining(","));

                List<NotificationBaseRequest> notificationBaseRequestList = new ArrayList<>();
                NotificationBaseRequest notificationBaseRequest = action==Actions.CREATE?getNotificationBaseRequestObj( String.format(CREATE_EXCEPTION_EVENT_NAME,assetGroupExceptionDetails.getExceptionName().trim()), CREATE_STICKY_EXCEPTION_SUBJECT):getNotificationBaseRequestObj( String.format(UPDATE_EXCEPTION_EVENT_NAME,assetGroupExceptionDetails.getExceptionName().trim()), UPDATE_STICKY_EXCEPTION_SUBJECT);
                StickyExNotificationRequest stickyExNotificationRequest = new StickyExNotificationRequest();
                stickyExNotificationRequest.setExceptionName(assetGroupExceptionDetails.getExceptionName().trim());
                stickyExNotificationRequest.setAssetGroup(assetGroupExceptionDetails.getAssetGroup().trim());
                stickyExNotificationRequest.setExceptionReason(assetGroupExceptionDetails.getExceptionReason().trim());
                stickyExNotificationRequest.setExpiringOn(assetGroupExceptionDetails.getExpiryDate());
                stickyExNotificationRequest.setUserId(assetGroupExceptionDetails.getCreatedBy());
                stickyExNotificationRequest.setPolicyNames(combinedPolicyNameStr);
                stickyExNotificationRequest.setType("sticky");
                stickyExNotificationRequest.setAction(action);
                stickyExNotificationRequest.setDocid(combinedPolicyIdStr);
                notificationBaseRequest.setPayload(stickyExNotificationRequest);
                notificationBaseRequestList.add(notificationBaseRequest);
                String notificationDetailsStr = gson.toJson(notificationBaseRequestList);
                invokeNotificationUrl(notificationDetailsStr);
                if(log.isInfoEnabled()){
                    log.info("Notification request sent for create/update of sticky exception - {}",assetGroupExceptionDetails.getExceptionName().trim());
                }
            }
            else{
                log.info("Notification request not sent for create/update of sticky exception as no policy ids are selected.");
            }
        }

        catch (Exception e) {
                log.error("Error triggering lambda function url, notification request not sent for create/update sticky exemption. Error - {}",e.getMessage());
        }
    }


    @Async
    public void triggerNotificationForDelStickyException(AssetGroupException assetGroupException, String userId, String subject, String deletedBy){
        try {
            Gson gson = new Gson();
            List<NotificationBaseRequest> notificationBaseRequestList = new ArrayList<>();
            NotificationBaseRequest notificationBaseRequest = getNotificationBaseRequestObj( String.format(DELETE_EXCEPTION_EVENT_NAME,assetGroupException.getExceptionName().trim()), DELETE_STICKY_EXCEPTION_SUBJECT);
            StickyExNotificationRequest stickyExNotificationRequest = new StickyExNotificationRequest();
            stickyExNotificationRequest.setExceptionName(assetGroupException.getExceptionName().trim());
            stickyExNotificationRequest.setUserId(deletedBy);
            stickyExNotificationRequest.setType("sticky");
            stickyExNotificationRequest.setAction(Actions.DELETE);
            stickyExNotificationRequest.setDocid(String.valueOf(assetGroupException.getId()));
            notificationBaseRequest.setPayload(stickyExNotificationRequest);
            notificationBaseRequestList.add(notificationBaseRequest);
            String notificationDetailsStr = gson.toJson(notificationBaseRequestList);
            invokeNotificationUrl(notificationDetailsStr);
            if(log.isInfoEnabled()){
                log.info("Notification request sent for delete of sticky exception - {}",assetGroupException.getExceptionName().trim());
            }
        }
        catch (Exception e) {
            log.error("Error triggering lambda function url, notification request not sent for delete sticky exemption. Error - {}",e.getMessage());
        }
    }

    @Async
    public void triggerNotificationForEnableDisablePolicy(Policy policy, PolicyExemption exemption) {
        Gson gson = new Gson();
        List<NotificationBaseRequest> notificationBaseRequestList = new ArrayList<>();
        PolicyExemptionNotificationDto request = new PolicyExemptionNotificationDto();
        try {
            if (Objects.isNull(exemption.getExemptionDesc())) {
                exemption.setExemptionDesc(StringUtils.EMPTY);
            }
            exemption.setModifiedBy(Objects.isNull(exemption.getModifiedBy()) ? StringUtils.EMPTY :
                    exemption.getModifiedBy());
            BeanUtils.copyProperties(exemption, request);
            String policyStatus = exemption.getStatus().equalsIgnoreCase(OPEN) ? DISABLED_CAPS.toLowerCase() :
                    ENABLED_CAPS.toLowerCase();
            String summary = exemption.getStatus().equalsIgnoreCase(OPEN) ? String.format(AdminConstants
                    .POLICY_DISABLE_DESCRIPTION, exemption.getCreatedBy(), AdminUtils.getStringDate(DATE_FORMAT,
                    addDays(exemption.getExpireDate(), 1))) : String.format(AdminConstants.POLICY_ENABLE_DESCRIPTION,
                    exemption.getModifiedBy(), AdminUtils.getStringDate(DATE_FORMAT, exemption.getModifiedOn()));
            NotificationBaseRequest notificationBaseRequest = new NotificationBaseRequest();
            notificationBaseRequest.setEventCategory(NotificationTypes.POLICY);
            notificationBaseRequest.setEventCategoryName(NotificationTypes.POLICY.getValue());
            notificationBaseRequest.setEventName(String.format(POLICY_ACTION_EVENT_NAME, policy.getPolicyDisplayName(),
                    policyStatus));
            notificationBaseRequest.setEventDescription(String.format(POLICY_ACTION_EVENT_NAME, policy.getPolicyDisplayName(),
                    policyStatus));
            notificationBaseRequest.setSubject(POLICY_ACTION_SUBJECT);
            request.setSummary(summary);
            request.setPolicyName(policy.getPolicyDisplayName());
            request.setStatus(policyStatus);
            notificationBaseRequest.setPayload(request);
            notificationBaseRequestList.add(notificationBaseRequest);
            String notificationDetailsStr = gson.toJson(notificationBaseRequestList);
            invokeNotificationUrl(notificationDetailsStr);
        } catch (Exception ex) {
            log.error(POLICY_ACTION_ERROR_MSG, ex.getMessage());
        }
    }

    @Async
    @Override
    public void triggerPluginNotification(PluginParameters parameters, Actions action) {
        List<NotificationBaseRequest> notificationBaseRequestList = new ArrayList<>();
        PluginNotificationDto request = new PluginNotificationDto();
        try {
            String pluginName = parameters.getPluginDisplayName();
            String pluginId = parameters.getId();
            String doneBy = parameters.getCreatedBy();
            String subject = action.equals(Actions.CREATE) ? "New plugin created" : "Plugin deleted";
            String summary = action.equals(Actions.CREATE) ? String.format(CREATE_PLUGIN_TEMPLATE,
                    StringUtils.capitalize(pluginName), pluginId, doneBy) :
                    String.format(DELETE_PLUGIN_TEMPLATE, StringUtils.capitalize(pluginName), pluginId, doneBy);
            NotificationBaseRequest notificationBaseRequest = new NotificationBaseRequest();
            notificationBaseRequest.setEventCategory(NotificationTypes.PLUGIN);
            notificationBaseRequest.setEventCategoryName(NotificationTypes.PLUGIN.getValue());
            notificationBaseRequest.setEventName(summary);
            notificationBaseRequest.setEventDescription(summary);
            notificationBaseRequest.setSubject(subject);
            request.setSummary(summary);
            request.setPluginName(pluginName);
            request.setAction(StringUtils.capitalize(action.toString().toLowerCase()));
            request.setDoneBy(doneBy);
            notificationBaseRequest.setPayload(request);
            notificationBaseRequestList.add(notificationBaseRequest);
            String notificationDetailsStr = mapper.writeValueAsString(notificationBaseRequestList);
            invokeNotificationUrl(notificationDetailsStr);
            log.info("Successfully triggered notification for Plugin {}", parameters);
        } catch (Exception ex) {
            log.error("Error in triggering notification for Plugin : {}", ex.getMessage(), ex);
        }
    }

    private static NotificationBaseRequest getNotificationBaseRequestObj( String eventName, String subject){
        NotificationBaseRequest notificationBaseRequest = new NotificationBaseRequest();
        notificationBaseRequest.setEventCategory(NotificationTypes.EXEMPTION);
        notificationBaseRequest.setEventCategoryName(NotificationTypes.EXEMPTION.getValue());
        notificationBaseRequest.setEventName(eventName);
        notificationBaseRequest.setEventDescription(eventName);
        notificationBaseRequest.setSubject(subject);
        return notificationBaseRequest;
    }

    public void invokeNotificationUrl(String notificationDetailsStr) throws Exception {
        Map<String,String> headersMap = new HashMap<>();
        headersMap.put("Authorization", ThreadLocalUtil.accessToken.get());
        PacHttpUtils.doHttpPost(notificationUrl, notificationDetailsStr,headersMap);
    }
}
