package com.krishagni.catissueplus.core.biospecimen.services.impl;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;

import com.krishagni.catissueplus.core.administrative.domain.User;
import com.krishagni.catissueplus.core.biospecimen.ConfigParams;
import com.krishagni.catissueplus.core.biospecimen.domain.Specimen;
import com.krishagni.catissueplus.core.biospecimen.domain.SpecimenList;
import com.krishagni.catissueplus.core.biospecimen.domain.SpecimenListItem;
import com.krishagni.catissueplus.core.biospecimen.domain.factory.SpecimenListErrorCode;
import com.krishagni.catissueplus.core.biospecimen.domain.factory.SpecimenListFactory;
import com.krishagni.catissueplus.core.biospecimen.events.ShareSpecimenListOp;
import com.krishagni.catissueplus.core.biospecimen.events.SpecimenInfo;
import com.krishagni.catissueplus.core.biospecimen.events.SpecimenListDetail;
import com.krishagni.catissueplus.core.biospecimen.events.SpecimenListSummary;
import com.krishagni.catissueplus.core.biospecimen.events.UpdateListSpecimensOp;
import com.krishagni.catissueplus.core.biospecimen.repository.DaoFactory;
import com.krishagni.catissueplus.core.biospecimen.repository.SpecimenListCriteria;
import com.krishagni.catissueplus.core.biospecimen.repository.SpecimenListsCriteria;
import com.krishagni.catissueplus.core.biospecimen.repository.impl.BiospecimenDaoHelper;
import com.krishagni.catissueplus.core.biospecimen.services.SpecimenListService;
import com.krishagni.catissueplus.core.common.PlusTransactional;
import com.krishagni.catissueplus.core.common.access.AccessCtrlMgr;
import com.krishagni.catissueplus.core.common.access.SiteCpPair;
import com.krishagni.catissueplus.core.common.domain.Notification;
import com.krishagni.catissueplus.core.common.errors.OpenSpecimenException;
import com.krishagni.catissueplus.core.common.events.EntityQueryCriteria;
import com.krishagni.catissueplus.core.common.events.RequestEvent;
import com.krishagni.catissueplus.core.common.events.ResponseEvent;
import com.krishagni.catissueplus.core.common.events.UserSummary;
import com.krishagni.catissueplus.core.common.service.ObjectAccessor;
import com.krishagni.catissueplus.core.common.util.AuthUtil;
import com.krishagni.catissueplus.core.common.util.ConfigUtil;
import com.krishagni.catissueplus.core.common.util.EmailUtil;
import com.krishagni.catissueplus.core.common.util.MessageUtil;
import com.krishagni.catissueplus.core.common.util.NotifUtil;
import com.krishagni.catissueplus.core.common.util.Utility;
import com.krishagni.catissueplus.core.de.domain.SavedQuery;
import com.krishagni.catissueplus.core.de.events.ExecuteQueryEventOp;
import com.krishagni.catissueplus.core.de.events.QueryDataExportResult;
import com.krishagni.catissueplus.core.de.services.QueryService;
import com.krishagni.catissueplus.core.de.services.SavedQueryErrorCode;
import com.krishagni.catissueplus.core.query.Column;
import com.krishagni.catissueplus.core.query.ListConfig;
import com.krishagni.catissueplus.core.query.ListService;
import com.krishagni.catissueplus.core.query.ListUtil;
import com.krishagni.rbac.common.errors.RbacErrorCode;

import edu.common.dynamicextensions.query.QueryResultData;
import edu.common.dynamicextensions.query.WideRowMode;


public class SpecimenListServiceImpl implements SpecimenListService, InitializingBean, ObjectAccessor {

	private static final Pattern DEF_LIST_NAME_PATTERN = Pattern.compile("\\$\\$\\$\\$user_\\d+");

	private SpecimenListFactory specimenListFactory;
	
	private DaoFactory daoFactory;

	private ListService listSvc;

	private com.krishagni.catissueplus.core.de.repository.DaoFactory deDaoFactory;

	private QueryService querySvc;

	public SpecimenListFactory getSpecimenListFactory() {
		return specimenListFactory;
	}

	public void setSpecimenListFactory(SpecimenListFactory specimenListFactory) {
		this.specimenListFactory = specimenListFactory;
	}

	public DaoFactory getDaoFactory() {
		return daoFactory;
	}

	public void setDaoFactory(DaoFactory daoFactory) {
		this.daoFactory = daoFactory;
	}

	public void setListSvc(ListService listSvc) {
		this.listSvc = listSvc;
	}

	public void setDeDaoFactory(com.krishagni.catissueplus.core.de.repository.DaoFactory deDaoFactory) {
		this.deDaoFactory = deDaoFactory;
	}

	public void setQuerySvc(QueryService querySvc) {
		this.querySvc = querySvc;
	}

	@Override
	@PlusTransactional
	public ResponseEvent<List<SpecimenListSummary>> getSpecimenLists(RequestEvent<SpecimenListsCriteria> req) {
		try {
			SpecimenListsCriteria crit = addSpecimenListsCriteria(req.getPayload());
			return ResponseEvent.response(daoFactory.getSpecimenListDao().getSpecimenLists(crit));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<Long> getSpecimenListsCount(RequestEvent<SpecimenListsCriteria> req) {
		try {
			SpecimenListsCriteria crit = addSpecimenListsCriteria(req.getPayload());
			return ResponseEvent.response(daoFactory.getSpecimenListDao().getSpecimenListsCount(crit));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<SpecimenListDetail> getSpecimenList(RequestEvent<EntityQueryCriteria> req) {
		try {
			EntityQueryCriteria crit = req.getPayload();
			SpecimenList specimenList = getSpecimenList(crit.getId(), crit.getName());
			return ResponseEvent.response(SpecimenListDetail.from(specimenList));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);			
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<SpecimenListDetail> createSpecimenList(RequestEvent<SpecimenListDetail> req) {
		try {
			SpecimenListDetail listDetails = req.getPayload();
			
			List<SiteCpPair> siteCps = AccessCtrlMgr.getInstance().getReadAccessSpecimenSiteCps();
			if (siteCps != null && siteCps.isEmpty()) {
				return ResponseEvent.userError(SpecimenListErrorCode.ACCESS_NOT_ALLOWED);
			}
			
			UserSummary owner = new UserSummary();
			owner.setId(AuthUtil.getCurrentUser().getId());
			listDetails.setOwner(owner);
			
			SpecimenList specimenList = specimenListFactory.createSpecimenList(listDetails);
			ensureUniqueName(specimenList);
			ensureValidSpecimensAndUsers(listDetails, specimenList, siteCps);

			daoFactory.getSpecimenListDao().saveOrUpdate(specimenList);
			saveListItems(specimenList, listDetails.getSpecimenIds(), true);
			notifyUsersOnCreate(specimenList);
			return ResponseEvent.response(SpecimenListDetail.from(specimenList));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<SpecimenListDetail> updateSpecimenList(RequestEvent<SpecimenListDetail> req) {
		return updateSpecimenList(req, false);
	}
	
	@Override
	@PlusTransactional
	public ResponseEvent<SpecimenListDetail> patchSpecimenList(RequestEvent<SpecimenListDetail> req) {
		return updateSpecimenList(req, true);
	}
	
	@Override
	@PlusTransactional
	public ResponseEvent<SpecimenListDetail> deleteSpecimenList(RequestEvent<Long> req) {
		try {
			SpecimenList existing = getSpecimenList(req.getPayload(), null);

			//
			// copy of deleted list
			//
			SpecimenList deletedSpecimenList = new SpecimenList();
			BeanUtils.copyProperties(existing, deletedSpecimenList);

			existing.delete();
			daoFactory.getSpecimenListDao().saveOrUpdate(existing);

			notifyUsersOnDelete(deletedSpecimenList);
			return ResponseEvent.response(SpecimenListDetail.from(existing));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<List<SpecimenInfo>> getListSpecimens(RequestEvent<SpecimenListCriteria> req) {
		try {
			SpecimenListCriteria crit = getListSpecimensCriteria(req.getPayload());
			List<Specimen> specimens = daoFactory.getSpecimenDao().getSpecimens(crit);
			return ResponseEvent.response(SpecimenInfo.from(specimens));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<Integer> getListSpecimensCount(RequestEvent<SpecimenListCriteria> req) {
		try {
			SpecimenListCriteria crit = getListSpecimensCriteria(req.getPayload());
			return ResponseEvent.response(daoFactory.getSpecimenDao().getSpecimensCount(crit));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<List<SpecimenInfo>> getListSpecimensSortedByRel(RequestEvent<EntityQueryCriteria> req) {
		try {
			int maxSpmns = ConfigUtil.getInstance().getIntSetting(ConfigParams.MODULE, ConfigParams.REL_SORTING_MAX_SPMNS, 250);

			SpecimenList list = getSpecimenList(req.getPayload().getId(), req.getPayload().getName());
			int listSize = daoFactory.getSpecimenListDao().getListSpecimensCount(list.getId());
			if (listSize > maxSpmns) {
				return ResponseEvent.userError(SpecimenListErrorCode.EXCEEDS_REL_SORT_SIZE, list.getName(), maxSpmns);
			}

			List<Specimen> specimens = getReadAccessSpecimens(list.getId(), listSize);
			return ResponseEvent.response(SpecimenInfo.from(SpecimenList.groupByAncestors(specimens)));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<Integer>  updateListSpecimens(RequestEvent<UpdateListSpecimensOp> req) {
		try {
			UpdateListSpecimensOp opDetail = req.getPayload();
			if (CollectionUtils.isEmpty(opDetail.getSpecimens())) {
				return ResponseEvent.response(0);
			}

			SpecimenList specimenList = getSpecimenList(opDetail.getListId(), null);
			ensureValidSpecimens(opDetail.getSpecimens(), null);

			switch (opDetail.getOp()) {
				case ADD:
					if (specimenList.getId() == null) {
						daoFactory.getSpecimenListDao().saveOrUpdate(specimenList);
					}

					saveListItems(specimenList, opDetail.getSpecimens(), false);
					break;
				
				case REMOVE:
					if (specimenList.getId() != null) {
						deleteListItems(specimenList, opDetail.getSpecimens());
					}
					break;				
			}

			return ResponseEvent.response(opDetail.getSpecimens().size());
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<Boolean> addChildSpecimens(RequestEvent<Long> req) {
		try {
			SpecimenList list = getSpecimenList(req.getPayload(), null);
			daoFactory.getSpecimenListDao().addChildSpecimens(list.getId(), ConfigUtil.getInstance().isOracle());
			return ResponseEvent.response(Boolean.TRUE);
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<List<UserSummary>> shareSpecimenList(RequestEvent<ShareSpecimenListOp> req) {
		try {
			ShareSpecimenListOp opDetail = req.getPayload();
			SpecimenList specimenList = getSpecimenList(opDetail.getListId(), null);

			List<User> users = null;
			List<Long> userIds = opDetail.getUserIds();
			if (userIds == null || userIds.isEmpty()) {
				users = new ArrayList<User>();
			} else {
				ensureValidUsers(userIds);
				users = daoFactory.getUserDao().getUsersByIds(userIds);
			}
			
			switch (opDetail.getOp()) {
				case ADD:
					specimenList.addSharedUsers(users);
					break;
					
				case UPDATE:
					specimenList.updateSharedUsers(users);
					break;
					
				case REMOVE:
					specimenList.removeSharedUsers(users);
					break;					
			}

			daoFactory.getSpecimenListDao().saveOrUpdate(specimenList);			
			List<UserSummary> result = new ArrayList<UserSummary>();
			for (User user : specimenList.getSharedWith()) {
				result.add(UserSummary.from(user));
			}
			
			return ResponseEvent.response(result);
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<QueryDataExportResult> exportSpecimenList(RequestEvent<SpecimenListCriteria> req) {
		try {
			return ResponseEvent.response(exportSpecimenList0(req.getPayload(), null));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	public void afterPropertiesSet()
	throws Exception {
		listSvc.registerListConfigurator("cart-specimens-list-view", this::getListSpecimensConfig);
	}

	@Override
	public QueryDataExportResult exportSpecimenList(SpecimenListCriteria crit, BiConsumer<QueryResultData, OutputStream> qdConsumer) {
		return exportSpecimenList0(crit, qdConsumer);
	}

	@Override
	public String getObjectName() {
		return SpecimenList.getEntityName();
	}

	@Override
	public Map<String, Object> resolveUrl(String key, Object value) {
		return Collections.singletonMap("listId", Long.parseLong(value.toString()));
	}

	@Override
	public String getAuditTable() {
		return "CARTS_NOT_AUDITED";
	}

	@Override
	public void ensureReadAllowed(Long cartId) {
		getSpecimenList(cartId, null);
	}

	private SpecimenListsCriteria addSpecimenListsCriteria(SpecimenListsCriteria crit) {
		if (!AuthUtil.isAdmin()) {
			crit.userId(AuthUtil.getCurrentUser().getId());
		}

		return crit;
	}

	private int saveListItems(SpecimenList list, List<Long> specimenIds, boolean newList) {
		if (CollectionUtils.isEmpty(specimenIds)) {
			return 0;
		}

		if (!newList) {
			//
			// we could have obtained only those IDs not in specimen list
			// but then we will be loosing order in which the specimen labels were inputted
			//
			List<Long> idsInList = daoFactory.getSpecimenListDao().getSpecimenIdsInList(list.getId(), specimenIds);
			specimenIds.removeAll(idsInList);
			if (specimenIds.isEmpty()) {
				return 0;
			}
		}

		List<SpecimenListItem> items = specimenIds.stream()
			.map(specimenId -> {
				Specimen spmn = new Specimen();
				spmn.setId(specimenId);

				SpecimenListItem item = new SpecimenListItem();
				item.setList(list);
				item.setSpecimen(spmn);
				return item;
			}).collect(Collectors.toList());

		daoFactory.getSpecimenListDao().saveListItems(items);
		return items.size();
	}

	private int deleteListItems(SpecimenList list, List<Long> specimenIds) {
		return daoFactory.getSpecimenListDao().deleteListItems(list.getId(), specimenIds);
	}

	private ResponseEvent<SpecimenListDetail> updateSpecimenList(RequestEvent<SpecimenListDetail> req, boolean partial) {
		try {
			SpecimenListDetail listDetails = req.getPayload();
			SpecimenList existing = getSpecimenList(listDetails.getId(), null);
			UserSummary owner = new UserSummary();
			owner.setId(existing.getOwner().getId());
			listDetails.setOwner(owner);
			
			SpecimenList specimenList = null;
			if (partial) {
				specimenList = specimenListFactory.createSpecimenList(existing, listDetails);
			} else {
				specimenList = specimenListFactory.createSpecimenList(listDetails);
			}
			
			ensureUniqueName(existing, specimenList);
			ensureValidSpecimensAndUsers(listDetails, specimenList, null);

			Collection<User> addedUsers   = CollectionUtils.subtract(specimenList.getSharedWith(), existing.getSharedWith());
			Collection<User> removedUsers = CollectionUtils.subtract(existing.getSharedWith(), specimenList.getSharedWith());

			existing.update(specimenList);
			daoFactory.getSpecimenListDao().saveOrUpdate(existing);
			saveListItems(existing, listDetails.getSpecimenIds(), false);

			notifyUsersOnUpdate(existing, addedUsers, removedUsers);
			return ResponseEvent.response(SpecimenListDetail.from(existing));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	private SpecimenList getSpecimenList(Long listId, String listName) {
		SpecimenList list = null;
		Object key = null;

		if (listId != null) {
			if (listId != 0) {
				list = daoFactory.getSpecimenListDao().getSpecimenList(listId);
			} else {
				list = getDefaultList(AuthUtil.getCurrentUser());
			}
			key = listId;
		} else if (StringUtils.isNotBlank(listName)) {
			list = daoFactory.getSpecimenListDao().getSpecimenListByName(listName);
			key = listName;
		}

		if (list == null) {
			throw OpenSpecimenException.userError(SpecimenListErrorCode.NOT_FOUND, key);
		}

		Long userId = AuthUtil.getCurrentUser().getId();
		if (!AuthUtil.isAdmin() && !list.canUserAccess(userId)) {
			throw OpenSpecimenException.userError(SpecimenListErrorCode.ACCESS_NOT_ALLOWED);
		}

		return list;
	}

	private List<Long> getReadAccessSpecimenIds(List<Long> specimenIds, List<SiteCpPair> siteCps) {
		if (siteCps == null) {
			siteCps = AccessCtrlMgr.getInstance().getReadAccessSpecimenSiteCps();
		}

		if (siteCps != null && siteCps.isEmpty()) {
			return Collections.emptyList();
		}

		SpecimenListCriteria crit = new SpecimenListCriteria().ids(specimenIds).siteCps(siteCps);
		return daoFactory.getSpecimenDao().getSpecimenIds(crit);
	}

	private List<Specimen> getReadAccessSpecimens(Long listId, int size) {
		List<SiteCpPair> siteCps = AccessCtrlMgr.getInstance().getReadAccessSpecimenSiteCps();
		if (siteCps != null && siteCps.isEmpty()) {
			return Collections.emptyList();
		}

		SpecimenListCriteria crit = new SpecimenListCriteria()
			.specimenListId(listId).siteCps(siteCps)
			.maxResults(size).limitItems(true);
		return daoFactory.getSpecimenDao().getSpecimens(crit);
	}

	private SpecimenListCriteria getListSpecimensCriteria(SpecimenListCriteria crit) {
		//
		// specimen list is retrieved to ensure user has access to the list
		//
		getSpecimenList(crit.specimenListId(), null);

		List<SiteCpPair> siteCpPairs = AccessCtrlMgr.getInstance().getReadAccessSpecimenSiteCps();
		if (siteCpPairs != null && siteCpPairs.isEmpty()) {
			throw OpenSpecimenException.userError(RbacErrorCode.ACCESS_DENIED);
		}

		return crit.siteCps(siteCpPairs);
	}

	private void ensureValidSpecimensAndUsers(SpecimenListDetail details, SpecimenList specimenList, List<SiteCpPair> siteCpPairs) {
		if (details.isAttrModified("specimenIds")) {
			ensureValidSpecimens(details, siteCpPairs);
		}
		
		if (details.isAttrModified("sharedWith")){
			ensureValidUsers(specimenList, siteCpPairs);
		}
	}
	
	private void ensureValidSpecimens(SpecimenListDetail details, List<SiteCpPair> siteCpPairs) {
		if (CollectionUtils.isEmpty(details.getSpecimenIds())) {
			return;
		}

		ensureValidSpecimens(details.getSpecimenIds(), siteCpPairs);
	}
	
	private void ensureValidSpecimens(List<Long> specimenIds,  List<SiteCpPair> siteCpPairs) {
		List<Long> dbSpmnIds = getReadAccessSpecimenIds(specimenIds, siteCpPairs);
		if (dbSpmnIds.size() != specimenIds.size()) {
			throw OpenSpecimenException.userError(SpecimenListErrorCode.INVALID_SPECIMENS);
		}
	}

	private void ensureValidUsers(SpecimenList specimenList, List<SiteCpPair> siteCps) {
		if (CollectionUtils.isEmpty(specimenList.getSharedWith())) {
			return;
		}
		
		Long userId = specimenList.getOwner().getId();
		List<Long> sharedUsers = new ArrayList<Long>();
		for (User user : specimenList.getSharedWith()) {
			if (user.getId().equals(userId)) {
				continue;
			}
			sharedUsers.add(user.getId());
		}
		
		ensureValidUsers(sharedUsers);
	}
	
	private void ensureValidUsers(List<Long> userIds) {
		Long instituteId = null;
		if (!AuthUtil.isAdmin()) {
			User user = daoFactory.getUserDao().getById(AuthUtil.getCurrentUser().getId());
			instituteId = user.getInstitute().getId();
		}
		
		List<User> users = daoFactory.getUserDao().getUsersByIdsAndInstitute(userIds, instituteId);
		if (userIds.size() != users.size()) {
			throw OpenSpecimenException.userError(SpecimenListErrorCode.INVALID_USERS_LIST);
		}
	}
	
	private void ensureUniqueName(SpecimenList existingList, SpecimenList newList) {
		if (existingList != null && existingList.getName().equals(newList.getName())) {
			return;
		}
		
		ensureUniqueName(newList);
	}
	
	private void ensureUniqueName(SpecimenList newList) {
		String newListName = newList.getName();

		SpecimenList list = daoFactory.getSpecimenListDao().getSpecimenListByName(newListName);
		if  (list != null) {
			throw OpenSpecimenException.userError(SpecimenListErrorCode.DUP_NAME, newListName);
		}

		if (DEF_LIST_NAME_PATTERN.matcher(newListName).matches()) {
			throw OpenSpecimenException.userError(SpecimenListErrorCode.DUP_NAME, newListName);
		}
	}

	private SpecimenList createDefaultList(User user) {
		return specimenListFactory.createDefaultSpecimenList(user);
	}

	private SpecimenList getDefaultList(User user) {
		SpecimenList specimenList = daoFactory.getSpecimenListDao().getDefaultSpecimenList(user.getId());
		if (specimenList == null) {
			specimenList = createDefaultList(user);
		}

		return specimenList;
	}

	private QueryDataExportResult exportSpecimenList0(SpecimenListCriteria crit, BiConsumer<QueryResultData, OutputStream> qdConsumer) {
		final SpecimenList list = getSpecimenList(crit.specimenListId(), null);

		Integer queryId = ConfigUtil.getInstance().getIntSetting("common", "cart_specimens_rpt_query", -1);
		if (queryId == -1) {
			return null;
		}

		SavedQuery query = deDaoFactory.getSavedQueryDao().getQuery(queryId.longValue());
		if (query == null) {
			throw OpenSpecimenException.userError(SavedQueryErrorCode.NOT_FOUND, queryId);
		}

		String restriction = "Specimen.specimenCarts.name = \"" + list.getName() + "\"";
		if (CollectionUtils.isNotEmpty(crit.ids())) {
			restriction += " and Specimen.id in (" + StringUtils.join(crit.ids(), ",") + ")";
		} else if (CollectionUtils.isNotEmpty(crit.labels())) {
			restriction += " and Specimen.label in (" + StringUtils.join(crit.labels(), ",") + ")";
		}

		List<SiteCpPair> siteCps = AccessCtrlMgr.getInstance().getReadAccessSpecimenSiteCps();
		String siteCpRestriction = BiospecimenDaoHelper.getInstance().getSiteCpsCondAql(
			siteCps, AccessCtrlMgr.getInstance().isAccessRestrictedBasedOnMrn());
		if (StringUtils.isNotBlank(siteCpRestriction)) {
			restriction += " and " + siteCpRestriction;
		}

		ExecuteQueryEventOp op = new ExecuteQueryEventOp();
		op.setDrivingForm("Participant");
		op.setAql(query.getAql(restriction));
		op.setWideRowMode(WideRowMode.DEEP.name());
		op.setRunType("Export");
		if (qdConsumer != null) {
			return querySvc.exportQueryData(op, qdConsumer);
		}

		return querySvc.exportQueryData(op, new QueryService.ExportProcessor() {
			@Override
			public String filename() {
				return "cart_" + list.getId() + "_" + UUID.randomUUID().toString();
			}

			@Override
			public void headers(OutputStream out) {
				Map<String, String> headers = new LinkedHashMap<String, String>() {{
					put(msg(LIST_NAME), list.isDefaultList() ? msg("specimen_list_default_list", list.getOwner().formattedName()) : list.getName());
					put(msg(LIST_DESC), StringUtils.isNotBlank(list.getDescription()) ? list.getDescription() : msg("common_not_specified"));
				}};

				Utility.writeKeyValuesToCsv(out, headers);
			}
		});
	}

	private void notifyUsersOnCreate(SpecimenList specimenList) {
		notifyUsersOnListOp(specimenList, specimenList.getSharedWith(), "ADD");
	}

	private void notifyUsersOnUpdate(SpecimenList existing, Collection<User> addedUsers, Collection<User> removedUsers) {
		notifyUsersOnListOp(existing, addedUsers, "ADD");
		notifyUsersOnListOp(existing, removedUsers, "REMOVE");
	}

	private void notifyUsersOnDelete(SpecimenList specimenList) {
		notifyUsersOnListOp(specimenList, specimenList.getSharedWith(), "DELETE");
	}

	private void notifyUsersOnListOp(SpecimenList specimenList, Collection<User> notifyUsers, String op) {
		if (CollectionUtils.isEmpty(notifyUsers)) {
			return;
		}

		String notifMsg = getNotifMsg(specimenList, op);

		// Send email notification
		Map<String, Object> emailProps = new HashMap<>();
		emailProps.put("$subject", new String[] { notifMsg });
		emailProps.put("emailText", notifMsg);
		emailProps.put("specimenList", specimenList);
		emailProps.put("currentUser", AuthUtil.getCurrentUser());
		emailProps.put("ccAdmin", false);
		emailProps.put("op", op);

		Set<User> rcpts = new HashSet<>(notifyUsers);
		for (User rcpt : rcpts) {
			emailProps.put("rcpt", rcpt);
			EmailUtil.getInstance().sendEmail(SPECIMEN_LIST_SHARED_TMPL, new String[] { rcpt.getEmailAddress() }, null, emailProps);
		}

		// UI notification
		Notification notif = new Notification();
		notif.setEntityType(SpecimenList.getEntityName());
		notif.setEntityId(specimenList.getId());
		notif.setOperation("UPDATE");
		notif.setCreatedBy(AuthUtil.getCurrentUser());
		notif.setCreationTime(Calendar.getInstance().getTime());
		notif.setMessage(notifMsg);
		NotifUtil.getInstance().notify(notif, Collections.singletonMap("specimen-list", rcpts));
	}

	private String getNotifMsg(SpecimenList specimenList, String op) {
		String msgKey = "specimen_list_user_notif_" + op.toLowerCase();
		return MessageUtil.getInstance().getMessage(msgKey, new String[] { specimenList.getName() });
	}

	private ListConfig getListSpecimensConfig(Map<String, Object> listReq) {
		Number listId = (Number) listReq.get("listId");
		if (listId == null) {
			listId = (Number) listReq.get("objectId");
		}

		SpecimenList list = getSpecimenList(listId != null ? listId.longValue() : null, null);

		ListConfig cfg = ListUtil.getSpecimensListConfig("cart-specimens-list-view", true);
		ListUtil.addHiddenFieldsOfSpecimen(cfg);
		if (cfg == null) {
			return null;
		}

		List<SiteCpPair> siteCps = AccessCtrlMgr.getInstance().getReadAccessSpecimenSiteCps();
		if (siteCps != null && siteCps.isEmpty()) {
			throw OpenSpecimenException.userError(SpecimenListErrorCode.ACCESS_NOT_ALLOWED);
		}

		String restriction = "Specimen.specimenCarts.name = \"" + list.getName() + "\"";

		boolean useMrnSites = AccessCtrlMgr.getInstance().isAccessRestrictedBasedOnMrn();
		String cpSitesCond = BiospecimenDaoHelper.getInstance().getSiteCpsCondAql(siteCps, useMrnSites);
		if (StringUtils.isNotBlank(cpSitesCond)) {
			restriction += " and " + cpSitesCond;
		}

		Column orderBy = new Column();
		orderBy.setExpr("Specimen.specimenCarts.itemId");
		orderBy.setDirection("asc");

		cfg.setDrivingForm("Specimen");
		cfg.setRestriction(restriction);
		cfg.setDistinct(true);
		cfg.setOrderBy(Collections.singletonList(orderBy));
		return ListUtil.setListLimit(cfg, listReq);
	}

	private String msg(String code, Object ... params) {
		return MessageUtil.getInstance().getMessage(code, params);
	}

	private static final String LIST_NAME      = "specimen_list_name";

	private static final String LIST_DESC      = "specimen_list_description";

	private static final String SPECIMEN_LIST_SHARED_TMPL = "specimen_list_shared";
}