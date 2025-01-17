/*******************************************************************************
 * Copyright 2018 T Mobile, Inc. or its affiliates. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.tmobile.pacman.api.admin.repository.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tmobile.pacman.api.admin.common.AdminConstants;
import com.tmobile.pacman.api.admin.domain.*;
import com.tmobile.pacman.api.admin.exceptions.PacManException;
import com.tmobile.pacman.api.admin.repository.AssetGroupCriteriaDetailsRepository;
import com.tmobile.pacman.api.admin.repository.AssetGroupRepository;
import com.tmobile.pacman.api.admin.repository.AssetGroupTargetDetailsRepository;
import com.tmobile.pacman.api.admin.repository.TargetTypesRepository;
import com.tmobile.pacman.api.admin.repository.model.AssetGroupCriteriaDetails;
import com.tmobile.pacman.api.admin.repository.model.AssetGroupDetails;
import com.tmobile.pacman.api.admin.repository.model.AssetGroupTargetDetails;
import com.tmobile.pacman.api.admin.service.CommonService;
import com.tmobile.pacman.api.admin.util.AdminUtils;
import com.tmobile.pacman.api.commons.Constants;
import com.tmobile.pacman.api.commons.exception.DataException;
import com.tmobile.pacman.api.commons.exception.ServiceException;
import com.tmobile.pacman.api.commons.repo.ElasticSearchRepository;
import com.tmobile.pacman.api.commons.repo.PacmanRdsRepository;
import com.tmobile.pacman.api.commons.utils.CommonUtils;
import lombok.var;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.tmobile.pacman.api.admin.common.AdminConstants.ASSET_GROUP_ALIAS_DELETION_FAILED;
import static com.tmobile.pacman.api.admin.common.AdminConstants.ASSET_GROUP_CREATION_SUCCESS;
import static com.tmobile.pacman.api.admin.common.AdminConstants.ASSET_GROUP_DELETE_FAILED;
import static com.tmobile.pacman.api.admin.common.AdminConstants.ASSET_GROUP_DELETE_SUCCESS;
import static com.tmobile.pacman.api.admin.common.AdminConstants.ASSET_GROUP_NOT_EXITS;
import static com.tmobile.pacman.api.admin.common.AdminConstants.ASSET_GROUP_UPDATION_SUCCESS;
import static com.tmobile.pacman.api.admin.common.AdminConstants.DATE_FORMAT;
import static com.tmobile.pacman.api.admin.common.AdminConstants.ES_ALIASES_PATH;
import static com.tmobile.pacman.api.admin.common.AdminConstants.UNEXPECTED_ERROR_OCCURRED;
import static com.tmobile.pacman.api.commons.Constants.GROUP_NAME_FOR_ALL_SOURCES_ASSET_GROUP;

/**
 * AssetGroup Service Implementations
 */
@Service
public class AssetGroupServiceImpl implements AssetGroupService {
	private static final Logger log = LoggerFactory.getLogger(AssetGroupServiceImpl.class);

	private static final String ALIASES = "/_aliases";
	private static  final  String BUCKETS="buckets";
	private  static  final  String KEY="key";
	private static final String ALIASES_ACTION_TEMPLATE = "{\"actions\":[%s]}";
	private static final String ACTION_TEMPLATE = "{\"%s\":{\"index\":\"%s\",\"alias\":\"%s\"}}";

	@Autowired
	private AssetGroupRepository assetGroupRepository;

	@Autowired
	private TargetTypesRepository targetTypesRepository;

	@Autowired
	private CommonService commonService;

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private AssetGroupTargetDetailsRepository assetGroupTargetDetailsRepository;

	@Autowired
	private AssetGroupTargetDetailsService assetGroupTargetDetailsService;

	@Autowired
	ElasticSearchRepository esRepository;
	@Autowired
	private AssetGroupCriteriaDetailsRepository assetGroupCriteriaDetailsRepository;

	@Autowired
	private CreateAssetGroupService createAssetGroupService;

	@Autowired
	private EsCommonService esCommonService;
	@Autowired
	PacmanRdsRepository rdsRepository;

	@Value("${tagging.mandatoryTags}")
	private String mandatoryTags;

	@Value("${cloud-provider}")
	private String cloudProvider;

	@Override
	public Collection<String> getAllAssetGroupNames() {
		return assetGroupRepository.getAllAssetGroupNames();
	}

	@Override
	public Page<AssetGroupView> getAllAssetGroupDetails(PluginRequestBody requestBody, final String searchTerm, int page, int size) {
		StringBuilder query = new StringBuilder("SELECT * FROM cf_AssetGroupDetails ag WHERE LOWER(ag.groupType) <> 'user' and isVisible='1'");
		StringBuilder whereQuery = new StringBuilder("");
		if (requestBody != null && !requestBody.getFilter().isEmpty()) {
			for (var entry : requestBody.getFilter().entrySet()) {
				if (!entry.getKey().toString().equalsIgnoreCase("assetCount")) {
					List<String> inpValues = (List<String>) entry.getValue();
					whereQuery = whereQuery.append(" and ag." + entry.getKey().toString() + " in (" + CommonUtils.joinListElement(inpValues) + ")");
				}
			}
		}
		String sortElement = requestBody.getSortFilter().get("fieldName") != null && !requestBody.getSortFilter().get("fieldName").equalsIgnoreCase("assetCount")
				? requestBody.getSortFilter().get("fieldName") : "groupId";
		String sortOrder = requestBody.getSortFilter() != null && requestBody.getSortFilter().get("order") != null ? requestBody.getSortFilter().get("order") : "ASC";
		query = query.append(whereQuery);
		if (size > 0) {
			query = query.append(" order by LOWER(" + sortElement + ") " + sortOrder + " limit " + size + " offset " + page * size);
		}
		List<Map<String, Object>> response = rdsRepository.getDataFromPacman(query.toString());
		List<AssetGroupDetails> assetGroupDetailsList = new ArrayList<>();
		response.stream().forEach(map -> {
			AssetGroupDetails assetGroupDetails = mapper.convertValue(map, AssetGroupDetails.class);
			assetGroupDetailsList.add(assetGroupDetails);
		});
		return buildAssetGroupView(assetGroupDetailsList, (List<Map<String, Object>>) requestBody.getFilter().get("assetCount"), requestBody.getSortFilter());
	}

	public Map<String, Object> getAllAssetGroupDetailsFilterValues(PluginRequestBody requestBody) {
		Page<AssetGroupView> dataResponse = getAllAssetGroupDetails(requestBody, "", 0, 0);
		Map<String, Object> responseMap = new HashMap<>();
		if(!requestBody.getAttributeName().equalsIgnoreCase("assetCount")){
			Set<String> filterValues = dataResponse.getContent().stream().map(obj -> mapper.convertValue(obj, Map.class))
					.filter(obj -> obj != null && obj.containsKey(requestBody.getAttributeName()))
					.map(obj -> obj.get(requestBody.getAttributeName()).toString()).collect(Collectors.toSet());
			List<Map<String, Object>> filterResMap = filterValues.stream().map(obj -> {
				HashMap<String, Object> map = new HashMap<>();
				map.put("name", obj);
				map.put("id", obj);
				return map;
			}).collect(Collectors.toList());
			responseMap.put("response", filterResMap);
		}
		else{
			Optional<Long> max = dataResponse.getContent().stream().map(obj -> obj.getAssetCount()).reduce(Long::max);
			Optional<Long> min = dataResponse.getContent().stream().map(obj -> obj.getAssetCount()).reduce(Long::min);
			HashMap<String, Object> rangeMap = new HashMap<>();
			rangeMap.put("max", max.isPresent() ? max.get() : 0);
			rangeMap.put("min", min.isPresent() ? min.get() : 0);
			HashMap<String, Object> optionRange = new HashMap<>();
			optionRange.put("optionRange", rangeMap);
			responseMap.put("response", optionRange);
		}

		return responseMap;
	}

	private Page<AssetGroupView> buildAssetGroupView(List<AssetGroupDetails> allAssetGroups, List<Map<String, Object>> filterMap, Map<String, String> sortFilter) {
		List<AssetGroupView> allAssetGroupList = Lists.newArrayList();
		List<AssetGroupDetails> assetGrpsList = allAssetGroups.stream().filter(assetGroup -> (!StringUtils.isEmpty(assetGroup.getGroupType()) &&
				!assetGroup.getGroupType().equalsIgnoreCase("stakeholder"))).collect(Collectors.toList());
		assetGrpsList.forEach(assetGroup -> {
			AssetGroupView assetGroupView = new AssetGroupView();
			List<Map<String, Object>> countMap = getAssetCountByAssetGroup(assetGroup.getGroupName(), "all", null, null, null);
			Long sum = countMap.stream().map(x -> x.get(AdminConstants.COUNT)).collect(Collectors.toList())
					.stream().map(x -> Long.valueOf(x.toString())).collect(Collectors.summingLong(Long::longValue));
			if(filterMap != null && !filterMap.stream().anyMatch(obj -> ((Integer) obj.get("min")).longValue() <= sum && sum <= ((Integer) obj.get("max")).longValue())){
				return;
			}
			assetGroupView.setAssetCount(sum);
			assetGroupView.setGroupId(assetGroup.getGroupId());
			assetGroupView.setCreatedBy(assetGroup.getCreatedBy());
			assetGroupView.setDisplayName(assetGroup.getDisplayName());
			assetGroupView.setCriteriaDetails(assetGroup.getCriteriaDetails());
			assetGroupView.setType(assetGroup.getGroupType());
			assetGroupView.setGroupName(assetGroup.getGroupName());
			assetGroupView.setType(assetGroup.getGroupType());
			assetGroupView.setGroupType(assetGroup.getGroupType());
			assetGroupView.setDescription(assetGroup.getDescription());
			assetGroupView.setUpdatedDate(assetGroup.getModifiedDate());
			assetGroupView.setCreatedDate(assetGroup.getCreatedDate());
			allAssetGroupList.add(assetGroupView);
		});
		if(!sortFilter.isEmpty() && sortFilter.get("fieldName").equalsIgnoreCase("assetCount")){
			Collections.sort(allAssetGroupList, (a, b) -> {
				if(sortFilter.get("order").equalsIgnoreCase("asc")){
					return Long.compare(a.getAssetCount(), b.getAssetCount());
				}
				return Long.compare(b.getAssetCount(), a.getAssetCount());
			});
		}
		return new PageImpl<>(allAssetGroupList);
	}

	private Page<AssetGroupView> buildAssetGroupView(final Page<AssetGroupDetails> allAssetGroups, Map<String, String> filterMap) {
		List<AssetGroupView> allAssetGroupListTemp = Lists.newArrayList();
		List<AssetGroupDetails> assetGrpsList = allAssetGroups.getContent().stream().filter(assetGroup -> (!StringUtils.isEmpty(assetGroup.getGroupType()) &&
				!assetGroup.getGroupType().equalsIgnoreCase("stakeholder"))).collect(Collectors.toList());
		assetGrpsList.forEach(assetGroup -> {
			AssetGroupView assetGroupView = new AssetGroupView();
			List<Map<String, Object>> countMap = getAssetCountByAssetGroup(assetGroup.getGroupName(), "all", null, null, null);
			Long sum = countMap.stream().map(x -> x.get(AdminConstants.COUNT)).collect(Collectors.toList())
					.stream().map(x -> Long.valueOf(x.toString())).collect(Collectors.summingLong(Long::longValue));
			assetGroupView.setAssetCount(sum);
			assetGroupView.setGroupId(assetGroup.getGroupId());
			assetGroupView.setCreatedBy(assetGroup.getCreatedBy());
			assetGroupView.setDisplayName(assetGroup.getDisplayName());
			assetGroupView.setCriteriaDetails(assetGroup.getCriteriaDetails());
			assetGroupView.setType(assetGroup.getGroupType());
			assetGroupView.setGroupName(assetGroup.getGroupName());
			assetGroupView.setCreatedBy(assetGroup.getCreatedBy());
			assetGroupView.setType(assetGroup.getGroupType());
			allAssetGroupListTemp.add(assetGroupView);
		});

		List<AssetGroupView> allAssetGroupList = allAssetGroupListTemp;

		if(filterMap != null){
			if(filterMap.containsKey(Constants.GROUP_NAME) && filterMap.get(Constants.GROUP_NAME) != null){
				allAssetGroupList =  allAssetGroupList.stream().filter(x -> x.getGroupName().equalsIgnoreCase(filterMap.get(Constants.GROUP_NAME))).collect(Collectors.toList());
			}
			if(filterMap.containsKey(Constants.TYPE) && filterMap.get(Constants.TYPE) != null){
				allAssetGroupList =  allAssetGroupList.stream().filter(x -> StringUtils.isNotEmpty(x.getType())).filter(x -> x.getType().equalsIgnoreCase(filterMap.get(Constants.TYPE))).collect(Collectors.toList());
			}
			if(filterMap.containsKey(Constants.CREATED_BY) && filterMap.get(Constants.CREATED_BY) != null){
				allAssetGroupList =  allAssetGroupList.stream().filter(x -> StringUtils.isNotEmpty(x.getCreatedBy())).filter(x -> x.getCreatedBy().equalsIgnoreCase(filterMap.get(Constants.CREATED_BY))).collect(Collectors.toList());
			}
			if(filterMap.containsKey(Constants.ASSET_COUNT) && filterMap.get(Constants.ASSET_COUNT) != null){
				allAssetGroupList =  allAssetGroupList.stream().filter(x -> Long.compare(x.getAssetCount(), Long.valueOf(filterMap.get(Constants.ASSET_COUNT))) == 0).collect(Collectors.toList());
			}
		}
		return new PageImpl<>(allAssetGroupList, PageRequest.of(allAssetGroups.getNumber(), allAssetGroups.getSize()),allAssetGroups.getTotalElements());
	}


	@Override
	public List<Map<String, Object>> getAssetCountByAssetGroup(String assetGroup, String type, String domain,
															   String application, String provider) {
		log.debug("Fetch counts from elastic search");
		// ES query may possibly return other types as well.
		Map<String, Long> countMap = esRepository.getAssetCountByAssetGroup(assetGroup, type, application);
		List<String> validTypes = Lists.newArrayList();
		if ("all".equalsIgnoreCase(type)) {
			log.debug("Remove the entries which are not valid types");
			List<Map<String, Object>> targetTypes = assetGroupTargetDetailsService.getTargetTypesByAssetGroupNameFromES(assetGroup);
			validTypes = targetTypes.stream().map(obj -> obj.get(Constants.TYPE).toString())
					.collect(Collectors.toList());
			List<String> countTypes = new ArrayList<>(countMap.keySet());
			for (String _type : validTypes) {
				if (!countMap.containsKey(_type)) {
					countMap.put(_type, 0L);
				}
			}
			for (String _type : countTypes) {
				if (!validTypes.contains(_type)) {
					countMap.remove(_type);
				}
			}
		} else {
			validTypes.add(type);
		}

		log.debug("Creating response objects ");
		List<Map<String, Object>> countList = new ArrayList<>();
		countMap.entrySet().stream().forEach(entry -> {
			if (!Integer.valueOf(entry.getValue().toString()).equals(0)) {
				Map<String, Object> typeMap = new HashMap<>();
				typeMap.put(Constants.TYPE, entry.getKey());
				typeMap.put(Constants.COUNT, entry.getValue());
				countList.add(typeMap);
			}
		});

		return countList;
	}

	@Override
	public AssetGroupDetails findByGroupName(String groupName) {
		return assetGroupRepository.findByGroupName(groupName);
	}

	@SuppressWarnings("unchecked")
	@Override
	public String updateAssetGroupDetails(final CreateAssetGroup updateAssetGroupDetails, final String userId) throws PacManException {
		AssetGroupDetails isAssetGroupExits = assetGroupRepository.findByGroupId(updateAssetGroupDetails.getGroupId());
		if (isAssetGroupExits != null) {
			updateAssetGroupDetails.setDisplayName(isAssetGroupExits.getDisplayName());
			String grpName = updateAssetGroupDetails.getGroupName().toLowerCase().trim().replace(" ","-");
			if(!isAssetGroupExits.getGroupName().equals(grpName)){
				String aliasName = updateAssetGroupDetails.getGroupName().toLowerCase().trim().replace(" ","-");
				AssetGroupDetails ifAssetPresentWithUpdatedGrpName = assetGroupRepository.findByGroupName(aliasName);
				if(ifAssetPresentWithUpdatedGrpName != null){
					throw new PacManException(AdminConstants.ASSET_GROUP_ALREADY_EXISTS);
				}
				updateAssetGroupDetails.setDisplayName(updateAssetGroupDetails.getGroupName());
				updateAssetGroupDetails.setGroupName(aliasName);
			}
			boolean isDeletedSuccess = deleteAssetGroupAliasFromUpdation(isAssetGroupExits);
			if(isDeletedSuccess) {
				try {
					CreateAssetGroup createAssetGroup = createAssetGroupService.createAliasForAssetGroup(updateAssetGroupDetails);
					Response response = commonService.invokeAPI("POST", ALIASES, mapper.writeValueAsString(createAssetGroup.getAlias()));
					if(response != null && response.getStatusLine().getStatusCode() == 200) {
						return processUpdateAssetGroupDetails(updateAssetGroupDetails, createAssetGroup.getAlias(), userId);
					} else {
						throw new PacManException(UNEXPECTED_ERROR_OCCURRED);
					}
				} catch (Exception exception) {
					throw new PacManException(UNEXPECTED_ERROR_OCCURRED.concat(": ").concat(exception.getMessage()));
				}
			} else {
				throw new PacManException(ASSET_GROUP_ALIAS_DELETION_FAILED);
			}
		} else {
			throw new PacManException(ASSET_GROUP_NOT_EXITS);
		}
	}


	@Override
	public String createAssetGroupDetails(final CreateAssetGroup createAssetGroupDetails, final String userId) throws PacManException{
		try {
			if(createAssetGroupDetails == null || org.apache.commons.lang.StringUtils.isEmpty(createAssetGroupDetails.getGroupName()) || org.apache.commons.lang.StringUtils.isEmpty(createAssetGroupDetails.getType())){
				throw new PacManException(AdminConstants.INVALID_REQUEST);
			}
			String aliasName = createAssetGroupDetails.getGroupName().toLowerCase().trim().replace(" ","-");
			log.info("Alias name to be created");
			AssetGroupDetails isAlreadyExisting = assetGroupRepository.findByGroupName(aliasName);
			if(isAlreadyExisting != null){
				log.info("Alias name to be created already exists in database");
				throw new PacManException(AdminConstants.ASSET_GROUP_ALREADY_EXISTS);
			}
			CreateAssetGroup createAssetGroup = createAssetGroupService.createAliasForAssetGroup(createAssetGroupDetails);
			Response response = commonService.invokeAPI("POST", ALIASES, mapper.writeValueAsString(createAssetGroup.getAlias()));
			if(response != null) {
				if (response.getStatusLine().getStatusCode() == 200) {
					log.info("ES Service to create alias is successful {}", response.getStatusLine().getStatusCode());
					return processCreateAssetGroupDetails(createAssetGroup, userId);
				} else {
					throw new PacManException(UNEXPECTED_ERROR_OCCURRED);
				}
			} else {
				log.error("Exception in calling create asset group api");
				throw new PacManException(UNEXPECTED_ERROR_OCCURRED);
			}
		} catch (Exception exception) {
			log.error("Exception in creating asset group ");
			throw new PacManException(UNEXPECTED_ERROR_OCCURRED.concat(": ").concat(exception.getMessage()));
		}
	}

	@Override
	public String createAssetGroupDetail(final CreateUpdateAssetGroupDetails createAssetGroupDetails, final String userId) throws PacManException {
		try {
			Map<String, Object> assetGroupAlias = createAliasForAssetGroup(createAssetGroupDetails);
			Response response = commonService.invokeAPI("POST", ALIASES, mapper.writeValueAsString(assetGroupAlias));
			if(response != null) {
				if (response.getStatusLine().getStatusCode() == 200) {
					return processCreateAssetGroupDetails(createAssetGroupDetails, assetGroupAlias, userId);
				} else {
					throw new PacManException(UNEXPECTED_ERROR_OCCURRED);
				}
			} else {
				throw new PacManException(UNEXPECTED_ERROR_OCCURRED);
			}
		} catch (Exception exception) {
			throw new PacManException(UNEXPECTED_ERROR_OCCURRED.concat(": ").concat(exception.getMessage()));
		}
	}

	@Override
	public UpdateAssetGroupDetails getAssetGroupDetailsByIdAndDataSource(final String assetGroupId, final String dataSource) throws PacManException {
		if(assetGroupRepository.existsById(assetGroupId)) {
			AssetGroupDetails existingAssetGroupDetails = assetGroupRepository.findById(assetGroupId).get();
			return buildAssetGroupDetails(existingAssetGroupDetails);
		} else {
			throw new PacManException(ASSET_GROUP_NOT_EXITS);
		}
	}

	@Override
	public String deleteAssetGroup(final DeleteAssetGroupRequest assetGroupDetails, final String userId) throws PacManException {
		return deleteAssetGroupDetails(assetGroupDetails);
	}

	private String processUpdateAssetGroupDetails(final CreateAssetGroup updateAssetGroupDetails, final Map<String, Object> assetGroupAlias, String userId) throws PacManException {
		try {
			AssetGroupDetails existingAssetGroupDetails = assetGroupRepository.findByGroupId(updateAssetGroupDetails.getGroupId());
			String aliasName = updateAssetGroupDetails.getGroupName().toLowerCase().trim().replace(" ","-");
			existingAssetGroupDetails.setDisplayName(updateAssetGroupDetails.getDisplayName());
			existingAssetGroupDetails.setGroupName(aliasName);
			if(!org.apache.commons.lang.StringUtils.isEmpty(updateAssetGroupDetails.getType()))
				existingAssetGroupDetails.setGroupType(updateAssetGroupDetails.getType());
			if(!org.apache.commons.lang.StringUtils.isEmpty(updateAssetGroupDetails.getDescription()))
				existingAssetGroupDetails.setDescription(updateAssetGroupDetails.getDescription());
			if(!org.apache.commons.lang.StringUtils.isEmpty(updateAssetGroupDetails.getCreatedBy()))
				existingAssetGroupDetails.setCreatedBy(updateAssetGroupDetails.getCreatedBy());
			existingAssetGroupDetails.setModifiedUser(userId);
			existingAssetGroupDetails.setModifiedDate(Timestamp.valueOf(LocalDateTime.now(Clock.systemUTC())));
			existingAssetGroupDetails.setAliasQuery(mapper.writeValueAsString(assetGroupAlias));
			existingAssetGroupDetails.setIsVisible(updateAssetGroupDetails.isVisible());
			if(updateAssetGroupDetails.getConfiguration() != null){
				Set<AssetGroupCriteriaDetails> criteriaDetails = buildAssetGroupCriteria(updateAssetGroupDetails.getConfiguration(), existingAssetGroupDetails.getGroupId());
				existingAssetGroupDetails.setCriteriaDetails(criteriaDetails);
			}
			assetGroupRepository.saveAndFlush(existingAssetGroupDetails);
			return ASSET_GROUP_UPDATION_SUCCESS;
		} catch (Exception exception) {
			throw new PacManException(UNEXPECTED_ERROR_OCCURRED.concat(": ").concat(exception.getMessage()));
		}
	}

	private boolean deleteAssetGroupAliasFromUpdation(AssetGroupDetails updateAssetGroupDetails) throws PacManException {
		AssetGroupDetails assetGroupDetails = assetGroupRepository.findByGroupId(updateAssetGroupDetails.getGroupId());
		boolean isDeleted = deleteAssetGroupAlias(assetGroupDetails);
		if(isDeleted) {
			Set<AssetGroupCriteriaDetails> allDeletedCriteria = assetGroupDetails.getCriteriaDetails();
			assetGroupCriteriaDetailsRepository.deleteInBatch(allDeletedCriteria);
			return true;
		}
		return false;
	}

	private String processCreateAssetGroupDetails(final CreateAssetGroup createAssetGroupDetails, final String userId) throws PacManException {
		AssetGroupDetails assetGroupDetails = new AssetGroupDetails();
		try {
			log.info("Persisting asset group in database");
			String dataSource = createAssetGroupDetails.getDatasource();
			String assetGroupId = UUID.randomUUID().toString();
			assetGroupDetails.setGroupId(assetGroupId);
			assetGroupDetails.setGroupName(createAssetGroupDetails.getGroupName().toLowerCase().trim().replace(" ", "-"));
			assetGroupDetails.setDisplayName(createAssetGroupDetails.getGroupName());
			assetGroupDetails.setGroupType(createAssetGroupDetails.getType());
			assetGroupDetails.setCreatedBy(createAssetGroupDetails.getCreatedBy());
			assetGroupDetails.setDescription(createAssetGroupDetails.getDescription());
			assetGroupDetails.setCreatedDate(Timestamp.valueOf(LocalDateTime.now(Clock.systemUTC())));
			assetGroupDetails.setModifiedDate(Timestamp.valueOf(LocalDateTime.now(Clock.systemUTC())));
			assetGroupDetails.setCreatedUser(userId);
			assetGroupDetails.setDataSource(dataSource);
			assetGroupDetails.setAliasQuery(mapper.writeValueAsString(createAssetGroupDetails.getAlias()));
			assetGroupDetails.setIsVisible(createAssetGroupDetails.isVisible());
			Set<AssetGroupCriteriaDetails> criteriaDetails = buildAssetGroupCriteria(createAssetGroupDetails.getConfiguration(), assetGroupId);
			assetGroupDetails.setCriteriaDetails(criteriaDetails);
			assetGroupRepository.save(assetGroupDetails);
			log.info("Asset group saved successfully in  database");
			return ASSET_GROUP_CREATION_SUCCESS;
		} catch (Exception exception) {
			deleteAssetGroupAlias(assetGroupDetails);
			throw new PacManException(UNEXPECTED_ERROR_OCCURRED.concat(": ").concat(exception.getMessage()));
		}
	}

	private String processCreateAssetGroupDetails(final CreateUpdateAssetGroupDetails createAssetGroupDetails, final Map<String, Object> assetGroupAlias, final String userId) throws PacManException {
		AssetGroupDetails assetGroupDetails = new AssetGroupDetails();
		try {
			String dataSource = createAssetGroupDetails.getDataSourceName();
			String assetGroupId = UUID.randomUUID().toString();
			assetGroupDetails.setGroupId(assetGroupId);
			assetGroupDetails.setGroupName(createAssetGroupDetails.getGroupName().toLowerCase().trim().replace(" ", "-"));
			assetGroupDetails.setDisplayName(createAssetGroupDetails.getDisplayName());
			assetGroupDetails.setGroupType(createAssetGroupDetails.getType());
			assetGroupDetails.setCreatedBy(createAssetGroupDetails.getCreatedBy());
			assetGroupDetails.setDescription(createAssetGroupDetails.getDescription());
			assetGroupDetails.setCreatedDate(Timestamp.valueOf(LocalDateTime.now(Clock.systemUTC())));
			assetGroupDetails.setModifiedDate(Timestamp.valueOf(LocalDateTime.now(Clock.systemUTC())));
			assetGroupDetails.setCreatedUser(userId);
			assetGroupDetails.setDataSource(dataSource);
			assetGroupDetails.setAliasQuery(mapper.writeValueAsString(assetGroupAlias));
			assetGroupDetails.setIsVisible(createAssetGroupDetails.isVisible());
			List<TargetTypesDetails> targetTypesDetails = createAssetGroupDetails.getTargetTypes();
			Set<AssetGroupTargetDetails> allTargetTypesDetails = buildTargetTypes(targetTypesDetails, assetGroupId);
			assetGroupDetails.setTargetTypes(allTargetTypesDetails);
			assetGroupRepository.save(assetGroupDetails);
			return ASSET_GROUP_CREATION_SUCCESS;
		} catch (Exception exception) {
			deleteAssetGroupAlias(assetGroupDetails);
			throw new PacManException(UNEXPECTED_ERROR_OCCURRED.concat(": ").concat(exception.getMessage()));
		}
	}

	private Set<AssetGroupCriteriaDetails> buildAssetGroupCriteria(List<HashMap<String, Object>> criteriaList, String assetGroupId) {
		Set<AssetGroupCriteriaDetails> allCriteiraTypesDetails = Sets.newHashSet();
		for (int index = 0; index < criteriaList.size(); index++) {
			HashMap<String, Object> map =  criteriaList.get(index);
			int criteriaName = index + 1;
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				AssetGroupCriteriaDetails critera = new AssetGroupCriteriaDetails();
				critera.setId(UUID.randomUUID().toString());
				critera.setCriteriaName("criteria"+criteriaName);
				critera.setGroupId(assetGroupId);
				critera.setAttributeName(entry.getKey());
				String value = StringUtils.isEmpty(entry.getValue().toString()) ? "" : entry.getValue().toString();
				critera.setAttributeValue(value.replace("[","").replace("]",""));
				allCriteiraTypesDetails.add(critera);
			}
		}
		return allCriteiraTypesDetails;
	}

	private Set<AssetGroupTargetDetails> buildTargetTypes(List<TargetTypesDetails> targetTypesDetails, String assetGroupId) {
		Set<AssetGroupTargetDetails> allTargetTypesDetails = Sets.newHashSet();
		for (int index = 0; index < targetTypesDetails.size(); index++) {
			TargetTypesDetails targetTypes = targetTypesDetails.get(index);
			if (targetTypesDetails.get(index).isIncludeAll()) {
				AssetGroupTargetDetails assetGroupTargetDetails = new AssetGroupTargetDetails();
				assetGroupTargetDetails.setId(UUID.randomUUID().toString());
				assetGroupTargetDetails.setGroupId(assetGroupId);
				assetGroupTargetDetails.setTargetType(targetTypes.getTargetName());
				assetGroupTargetDetails.setAttributeName("all");
				assetGroupTargetDetails.setAttributeValue("all");
				allTargetTypesDetails.add(assetGroupTargetDetails);
			} else {
				List<AttributeDetails> attributes = targetTypes.getAttributes();
				for (int attributeIndex = 0; attributeIndex < attributes.size(); attributeIndex++) {
					AttributeDetails attribute = attributes.get(attributeIndex);
					AssetGroupTargetDetails assetGroupTargetDetails = new AssetGroupTargetDetails();
					assetGroupTargetDetails.setId(UUID.randomUUID().toString());
					assetGroupTargetDetails.setGroupId(assetGroupId);
					assetGroupTargetDetails.setTargetType(targetTypes.getTargetName());
					assetGroupTargetDetails.setAttributeName(attribute.getName());
					assetGroupTargetDetails.setAttributeValue(attribute.getValue());
					allTargetTypesDetails.add(assetGroupTargetDetails);
				}
			}
		}
		return allTargetTypesDetails;
	}

	private UpdateAssetGroupDetails buildAssetGroupDetails(final AssetGroupDetails assetGroup) {
		UpdateAssetGroupDetails assetGroupView = new UpdateAssetGroupDetails();
		List<Map<String, Object>> countMap = getAssetCountByAssetGroup(assetGroup.getGroupName(), "all", null, null, null);
		Long sum = countMap==null ? 0L : countMap.stream().map(x -> x.get(AdminConstants.COUNT)).collect(Collectors.toList())
				.stream().map(x -> Long.valueOf(x.toString())).collect(Collectors.summingLong(Long::longValue));
		assetGroupView.setAssetCount(sum);
		assetGroupView.setGroupId(assetGroup.getGroupId());
		assetGroupView.setCriteriaDetails(assetGroup.getCriteriaDetails());
		assetGroupView.setType(assetGroup.getGroupType());
		assetGroupView.setGroupName(assetGroup.getGroupName());
		assetGroupView.setCreatedBy(assetGroup.getCreatedBy());
		assetGroupView.setType(assetGroup.getGroupType());
		assetGroupView.setDescription(assetGroup.getDescription());
		assetGroupView.setDisplayName(assetGroup.getDisplayName());
		assetGroupView.setGroupName(assetGroup.getGroupName());
		assetGroupView.setGroupType(assetGroup.getGroupType());
		return assetGroupView;
	}

	private boolean deleteAssetGroupAlias(final AssetGroupDetails assetGroupDetails) throws PacManException {
		try {
			Map<String, Object> alias = Maps.newHashMap();
			List<Object> action = Lists.newArrayList();
			final String aliasName = assetGroupDetails.getGroupName().toLowerCase().trim().replace(" ", "-");
			if (!esCommonService.isDataStreamOrIndexOrAliasExists(aliasName)) {
				/*Alias doesn't exist or already deleted*/
				log.error(UNEXPECTED_ERROR_OCCURRED + String.format(AdminConstants.ALIAS_NOT_FOUND_ERR_MSG, aliasName));
				return true;
			}
			JSONArray jsonArray = new JSONObject(assetGroupDetails.getAliasQuery()).getJSONArray(AdminConstants.ACTIONS);
			for (int i=0; i < jsonArray.length(); i++) {
				String index = jsonArray.getJSONObject(i).getJSONObject("add").getString(AdminConstants.INDEX);
				if (!esCommonService.isDataStreamOrIndexOrAliasExists(index)) {
					/*Alias doesn't exist, we need to continue alias creation for rest of the indexes*/
					log.error(UNEXPECTED_ERROR_OCCURRED + String.format(AdminConstants.ALIAS_NOT_FOUND_ERR_MSG, aliasName));
					continue;
				}
				Map<String, Object> addObj = Maps.newHashMap();
				addObj.put(AdminConstants.INDEX, index);
				addObj.put("alias", aliasName);
				Map<String, Object> add = Maps.newHashMap();
				add.put("remove", addObj);
				action.add(add);
			}
			alias.put(AdminConstants.ACTIONS, action);
			boolean res = false;
			Response response = commonService.invokeAPI("POST", ALIASES, mapper.writeValueAsString(alias));
			if(response != null && response.getStatusLine().getStatusCode() == 200) {
				res = true;
			}
			return res;

		} catch (Exception exception) {
			throw new PacManException(UNEXPECTED_ERROR_OCCURRED.concat(": ").concat(exception.getMessage()));
		}
	}

	private Map<String, Object> createAliasForAssetGroup(final CreateUpdateAssetGroupDetails assetGroupDetailsJson) {
		try {
			Map<String, Object> alias = Maps.newHashMap();
			List<Object> action = Lists.newArrayList();
			List<TargetTypesDetails> targetTypes = assetGroupDetailsJson.getTargetTypes();
			final String aliasName = assetGroupDetailsJson.getGroupName().toLowerCase().trim().replace(" ", "-");
			for (int targetIndex = 0; targetIndex < targetTypes.size(); targetIndex++) {
				Map<String, Object> addObj = Maps.newHashMap();
				String targetType = targetTypes.get(targetIndex).getTargetName().toLowerCase().trim().replace(" ", "-");
				addObj.put(AdminConstants.INDEX, targetTypesRepository.findDataSourceByTargetType(targetType).toLowerCase().trim().replace(" ", "-") + "_" + targetType);
				addObj.put("alias", aliasName);
				List<AttributeDetails> attributes = Lists.newArrayList();
				if (!targetTypes.get(targetIndex).isIncludeAll()) {
					attributes = targetTypes.get(targetIndex).getAttributes();
				}

				Map<String, Object> parentObj = Maps.newHashMap();

				Map<String, Map<String, String>> typeDetails = Maps.newHashMap();
				Map<String, String> typeValueDetails = Maps.newHashMap();
				typeValueDetails.put("value", targetTypes.get(targetIndex).getTargetName());
				typeDetails.put("_type", typeValueDetails);
				parentObj.put("term", typeDetails);

				List<Object> mustArray = buildMustArray(attributes);
				String tempMustArray = mapper.writeValueAsString(mustArray);
				List<Object> shouldArray = Lists.newArrayList();
				Map<String, Object> hasParent = Maps.newHashMap();
				hasParent.put("parent_type", targetTypes.get(targetIndex).getTargetName());
				if (mustArray.isEmpty()) {
					Map<String, Object> matchAll = Maps.newHashMap();
					Map<String, Object> matchAllDetails = Maps.newHashMap();
					matchAll.put("match_all", matchAllDetails);
					hasParent.put("query", matchAll);
				} else {
					Map<String, Object> mustObj = Maps.newHashMap();
					mustObj.put("must", mustArray);
					Map<String, Object> boolMust = Maps.newHashMap();
					boolMust.put("bool", mustObj);
					hasParent.put("query", boolMust);
				}
				Map<String, Object> hasParentDetails = Maps.newHashMap();
				hasParentDetails.put("has_parent", hasParent);
				List<Object> mustObjects = mapper.readValue(tempMustArray,new TypeReference<List<Object>>() {});
				mustObjects.add(parentObj);
				Map<String, Object> mustDetails = Maps.newHashMap();
				Map<String, Object> mustValueDetails = Maps.newHashMap();
				mustValueDetails.put("must", mustObjects);
				mustDetails.put("bool", mustValueDetails);

				shouldArray.add(hasParentDetails);
				shouldArray.add(mustDetails);
				Map<String, Object> filterDetails = Maps.newHashMap();
				Map<String, Object> shouldDetails = Maps.newHashMap();
				shouldDetails.put("should", shouldArray);
				filterDetails.put("bool", shouldDetails);
				addObj.put("filter", filterDetails);

				Map<String, Object> add = Maps.newHashMap();
				add.put("add", addObj);
				action.add(add);
			}
			alias.put(AdminConstants.ACTIONS, action);
			return alias;
		} catch (Exception exception) {
			log.error(UNEXPECTED_ERROR_OCCURRED, exception);
			return Maps.newHashMap();
		}
	}

	private List<Object> buildMustArray(List<AttributeDetails> attributes) {
		List<Object> mustArray = Lists.newArrayList();
		for (Entry<String, String> attribute : createAttrMap(attributes).entrySet()) {
			String[] values = attribute.getValue().split(",");
			if (values.length > 1) {
				List<Object> shouldArray = Lists.newArrayList();
				for (String value : values) {
					Map<String, Object> attributeObj = Maps.newHashMap();
					attributeObj.put(attribute.getKey() + ".keyword", value);
					Map<String, Object> match = Maps.newHashMap();
					match.put("match", attributeObj);
					shouldArray.add(match);
				}
				Map<String, Object> shouldObj = Maps.newHashMap();
				shouldObj.put("should", shouldArray);
				shouldObj.put("minimum_should_match", "1");
				Map<String, Object> innerboolObj = Maps.newHashMap();
				innerboolObj.put("bool", shouldObj);
				mustArray.add(innerboolObj);
			} else {
				Map<String, Object> attributeObj = Maps.newHashMap();
				attributeObj.put(attribute.getKey() + ".keyword", attribute.getValue());
				Map<String, Object> match = Maps.newHashMap();
				match.put("match", attributeObj);
				mustArray.add(match);
			}
		}
		return mustArray;
	}

	private static Map<String, String> createAttrMap(List<AttributeDetails> attributes) {
		Map<String, String> attrMap = Maps.newHashMap();
		for (int index = 0; index < attributes.size(); index++) {
			AttributeDetails attribute = attributes.get(index);
			if (attrMap.isEmpty()) {
				attrMap.put(attribute.getName(), attribute.getValue());
			} else {
				if (attrMap.containsKey(attribute.getName())) {
					attrMap.put(attribute.getName(), attrMap.get(attribute.getName()) + "," + attribute.getValue());
				} else {
					attrMap.put(attribute.getName(), attribute.getValue());
				}
			}
		}
		return attrMap;
	}

	private String deleteAssetGroupDetails(final DeleteAssetGroupRequest deleteAssetGroupRequest) throws PacManException {
		log.info("To delete the provided asset group ");
		if(assetGroupRepository.existsById(deleteAssetGroupRequest.getGroupId())) {
			AssetGroupDetails assetGroupDetails = assetGroupRepository.findById(deleteAssetGroupRequest.getGroupId()).get();
			log.info("Provided asset group exits in database ");
			boolean isDeleted = deleteAssetGroupAlias(assetGroupDetails);
			log.info("Provided asset group deleted from es ");
			if(isDeleted) {
				try {
					assetGroupRepository.delete(assetGroupDetails);
					log.info("Provided asset group deleted from es and db ");
					return ASSET_GROUP_DELETE_SUCCESS;
				} catch(Exception exception) {
					commonService.invokeAPI("POST", ALIASES, assetGroupDetails.getAliasQuery());
					throw new PacManException(UNEXPECTED_ERROR_OCCURRED.concat(": ").concat(exception.getMessage()));
				}
			} else {
				return ASSET_GROUP_DELETE_FAILED;
			}
		} else {
			log.info("Provided asset group does not exits in database ");
			throw new PacManException(ASSET_GROUP_NOT_EXITS);
		}
	}
	public List<String> getFilterKeyValues(String key) throws  PacManException{
		log.info("Inside method to fetch filterkeyvalues for input ");
		if(StringUtils.isEmpty(key))
			return new ArrayList<>();
		if(key.toLowerCase().equalsIgnoreCase("type"))
			return assetGroupRepository.getDistinctType();
		else if(key.toLowerCase().equalsIgnoreCase("createdBy"))
			return assetGroupRepository.getDistinctCreatedBy();
		else
			return new ArrayList<>();
	}

	@Override
	public List<Map<String, Object>> getCloudTypeObject() throws Exception {
		List<String> cloudTypes = assetGroupTargetDetailsService.getAllConfiguredSources();
		List<Map<String ,Object>> cloudProviderObjList=new ArrayList<>();

		String aggsStrByTag = "\"%s\":{\"terms\":{\"field\":\"%s\",\"size\":10000}}";
		Set<String> tagsSet = new HashSet<>(Arrays.asList(mandatoryTags.split(",")));
		List<String> aggsForTagsList = new ArrayList<>();
		tagsSet.forEach(str -> aggsForTagsList.add(String.format(aggsStrByTag, "tags."+str, "tags." + str + ".keyword")));

		log.info("aggsForTagsList {}",aggsForTagsList);

		String responseDetails;
		for (String cloudType : cloudTypes) {
			Map<String, Object> cloudTypeObject = new HashMap<>();
			cloudTypeObject.put("CloudType",cloudType);
			log.info(cloudType);
			if(esCommonService.isDataStreamOrIndexOrAliasExists(cloudType)) {
				try {
					responseDetails = esRepository.getRequiredObject(cloudType, aggsForTagsList);
				} catch (DataException e) {
					throw new ServiceException(e);
				}
				JsonParser parser = new JsonParser();
				JsonObject responseJson = parser.parse(responseDetails).getAsJsonObject();
				JsonObject aggs = (JsonObject) responseJson.get("aggregations");

				cloudTypeObject.put("TargetType", targetTypesRepository.findValidTypesByDataSource(cloudType));

				List<String> regions = new ArrayList<>();
				JsonObject regionObj = (JsonObject) aggs.get("Region");
				JsonArray regionBuckets = regionObj.get(BUCKETS).getAsJsonArray();
				for (JsonElement bucket : regionBuckets) {
					regions.add(bucket.getAsJsonObject().get(KEY).getAsString());
				}
				cloudTypeObject.put("Region", regions);

				List<String> ids = new ArrayList<>();
				JsonObject idObj = (JsonObject) aggs.get("Id");
				JsonArray idBuckets = idObj.get(BUCKETS).getAsJsonArray();
				for (JsonElement bucket : idBuckets) {
					ids.add(bucket.getAsJsonObject().get(KEY).getAsString());
				}

				cloudTypeObject.put("Id", ids);

				List<String> tagsPool = new ArrayList<>();

				for (String agg : aggsForTagsList) {
					String[] parts = agg.split(":");
					String valueBeforeColon = parts[0];
					tagsPool.add(valueBeforeColon);
				}

				for (String tag : tagsPool) {
					log.info(tag);
					tag = tag.substring(1, tag.length() - 1);
					List<String> tagList = new ArrayList<>();
					JsonObject tagObj = (JsonObject) aggs.get(tag);

					JsonArray tagBuckets = tagObj.get(BUCKETS).getAsJsonArray();
					for (JsonElement bucket : tagBuckets) {
						tagList.add(bucket.getAsJsonObject().get(KEY).getAsString());
					}
					cloudTypeObject.put(tag, tagList);
				}
				boolean allValuesEmpty=true;
				for(Object value:cloudTypeObject.values()){
					if(value!= null){
						allValuesEmpty=false;
						break;
					}
				}

				if(!allValuesEmpty){
					cloudProviderObjList.add(cloudTypeObject);}
			}
		}
		return cloudProviderObjList ;
	}

	@Override
	public void createOrUpdatePluginAssetGroup(String pluginType, String displayName) {
		try {
			AssetGroupDetails alreadyExistingAssetGroup = assetGroupRepository.findByGroupName(pluginType);
			String currentDate = AdminUtils.getFormatedStringDate(DATE_FORMAT, new Date());
			if (alreadyExistingAssetGroup != null) {
				if (alreadyExistingAssetGroup.getIsVisible()) {
					return;
				}
				alreadyExistingAssetGroup.setModifiedUser("admin@paladincloud.io");
				alreadyExistingAssetGroup.setModifiedDate(Timestamp.valueOf(LocalDateTime.now(Clock.systemUTC())));
				alreadyExistingAssetGroup.setVisible(true);
				assetGroupRepository.save(alreadyExistingAssetGroup);
				return;
			}
			String pluginAliasQuery = getPluginAliasQuery(pluginType);
			AssetGroupDetails assetGroupDetails = new AssetGroupDetails();
			String assetGroupId = UUID.randomUUID().toString();
			assetGroupDetails.setGroupId(assetGroupId);
			assetGroupDetails.setGroupName(pluginType.toLowerCase());
			assetGroupDetails.setGroupType("System");
			assetGroupDetails.setCreatedBy("admin@paladincloud.io");
			assetGroupDetails.setDescription("Asset group for " + displayName);
			assetGroupDetails.setCreatedDate(Timestamp.valueOf(LocalDateTime.now(Clock.systemUTC())));
			assetGroupDetails.setCreatedUser("admin@paladincloud.io");
			assetGroupDetails.setDataSource(pluginType.toLowerCase());
			assetGroupDetails.setAliasQuery(pluginAliasQuery);
			assetGroupDetails.setIsVisible(true);
			assetGroupDetails.setDisplayName(displayName);
			log.info("Asset group created for pluginType : {}", pluginType);
			assetGroupRepository.save(assetGroupDetails);
			// Trying to re-add indices of plugin type to asset group
			addPluginTypeToAllSources(pluginType);
		} catch (Exception e) {
			log.error("Asset group creation for {} failed", pluginType, e);
		}
	}

	private String getPluginAliasQuery(String datasource) {
		Map<String, Object> alias = Maps.newHashMap();
		List<Object> action = Lists.newArrayList();
		try {
			Map<String, Object> addObj = Maps.newHashMap();
			addObj.put("index", datasource.toLowerCase().trim() + "_*");
			addObj.put("alias", datasource);
			Map<String, Object> add = Maps.newHashMap();
			add.put("add", addObj);
			action.add(add);
			alias.put("actions", action);
			return mapper.writeValueAsString(alias);
		} catch (Exception e) {
			log.error("Exception in creating alias query", e);
		}
		return "";
	}

	@Override
	public boolean deleteAssetGroupByGroupName(String groupName) {
		try {
			AssetGroupDetails assetGroupInDB = assetGroupRepository.findByGroupName(groupName);
			if (assetGroupInDB == null) {
				log.error("Asset group {} not found", groupName);
				return false;
			}
			assetGroupRepository.delete(assetGroupInDB);
			log.info("Asset group {} deleted", groupName);
			return true;
		} catch (Exception e) {
			log.error("Unable to delete asset group {}", groupName, e);
			return false;
		}
	}

	@Override
	public void addPluginTypeToAllSources(String pluginName) {
		try {
			String actionTemplate = String.format(ACTION_TEMPLATE, "add", pluginName + "_*",
					GROUP_NAME_FOR_ALL_SOURCES_ASSET_GROUP);
			String aliasQuery = String.format(ALIASES_ACTION_TEMPLATE, actionTemplate);
			Response response = commonService.invokeAPI("POST", ES_ALIASES_PATH, aliasQuery);
			if (response == null || response.getStatusLine().getStatusCode() != 200) {
				log.error("Unable to add indices for asset group {} with response {}",
						GROUP_NAME_FOR_ALL_SOURCES_ASSET_GROUP, response);
			}
			log.info("Indices added for {}", pluginName);
		} catch (Exception e) {
			log.error("Unable to add indices for {}", pluginName, e);
		}
	}

	@Override
	public void removePluginTypeFromAllSources(String pluginName) {
		try {
			String actionTemplate = String.format(ACTION_TEMPLATE, "remove", pluginName + "_*",
					GROUP_NAME_FOR_ALL_SOURCES_ASSET_GROUP);
			String aliasQuery = String.format(ALIASES_ACTION_TEMPLATE, actionTemplate);
			Response response = commonService.invokeAPI("POST", ES_ALIASES_PATH, aliasQuery);
			if (response == null || response.getStatusLine().getStatusCode() != 200) {
				log.error("Unable to remove indices for asset group {} with response {}",
						GROUP_NAME_FOR_ALL_SOURCES_ASSET_GROUP, response);
			}
			log.info("Indices removed for {}", pluginName);
		} catch (Exception e) {
			log.error("Unable to remove indices for {}", pluginName, e);
		}
	}
}
