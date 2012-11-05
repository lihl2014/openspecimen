package edu.wustl.catissuecore.action;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.actions.DispatchAction;

import edu.wustl.catissuecore.bizlogic.CollectionProtocolBizLogic;
import edu.wustl.catissuecore.bizlogic.ComboDataBizLogic;
import edu.wustl.catissuecore.bizlogic.SiteBizLogic;
import edu.wustl.catissuecore.bizlogic.StorageContainerForSpArrayBizLogic;
import edu.wustl.catissuecore.bizlogic.StorageContainerForSpecimenBizLogic;
import edu.wustl.catissuecore.bizlogic.UserBizLogic;
import edu.wustl.catissuecore.cpSync.SyncCPThreadExecuterImpl;
import edu.wustl.catissuecore.domain.Site;
import edu.wustl.catissuecore.domain.Specimen;
import edu.wustl.catissuecore.util.CollectionProtocolUtil;
import edu.wustl.catissuecore.util.global.AppUtility;
import edu.wustl.catissuecore.util.global.Constants;
import edu.wustl.common.beans.NameValueBean;
import edu.wustl.common.beans.SessionDataBean;
import edu.wustl.common.cde.CDEManager;
import edu.wustl.common.exception.ApplicationException;
import edu.wustl.common.exception.BizLogicException;
import edu.wustl.common.util.global.Status;
import edu.wustl.dao.DAO;
import edu.wustl.dao.JDBCDAO;
import edu.wustl.dao.QueryWhereClause;
import edu.wustl.dao.condition.EqualClause;
import edu.wustl.dao.query.generator.ColumnValueBean;

public class CatissueCommonAjaxAction extends DispatchAction{
	
	/**
	 * Returns list of all the User of this application.
	 *
	 * @param mapping the mapping
	 * @param form the form
	 * @param request the request
	 * @param response the response
	 * @return the action forward
	 * @throws ApplicationException the application exception
	 * @throws IOException 
	 */
	public ActionForward allUserList(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		StringBuffer responseString = new StringBuffer(Constants.XML_START);
		List<NameValueBean> userList = userList(request);
		responseString.append(Constants.XML_ROWS);
		for (NameValueBean nvb : userList)
		{
			responseString.append(this.addRowToResponseXML(Long.valueOf(nvb.getValue()),null, nvb.getName()));
		}
		responseString.append(Constants.XML_ROWS_END);
		response.setContentType(Constants.CONTENT_TYPE_XML);
		response.getWriter().write(responseString.toString());
		return null;
	}

	/**
	 * This function returns result in xml fromat to populate DHTMLX DropDown combo box.
	 * @param identifier if list is of (String,Long) type pass long value as row id
	 * @param stringValue if list is of (String,String) type pass String value as row id
	 * @param name - this is a display name in DropDown
	 * @return
	 */
	private String addRowToResponseXML(Long identifier,String stringValue, String name)
	{
		StringBuffer responseString = new StringBuffer(Constants.XML_ROW_ID_START);
		responseString.append((identifier==null?stringValue:identifier)).append(Constants.XML_TAG_END).append(Constants.XML_CELL_START).append(
						Constants.XML_CDATA_START).append(name).append(
						Constants.XML_CDATA_END).append(Constants.XML_CELL_END).append(Constants.XML_ROW_END);
		return responseString.toString();
	}
	
	public ActionForward getUserListAsJSon(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		AppUtility.writeListAsJSon(userList(request), request, response);
		return null;
	}
	
	private List<NameValueBean> userList(HttpServletRequest request) throws BizLogicException
	{
		String operation = request.getParameter(Constants.OPERATION);
		if (operation == null)
		{
			operation = (String) request.getAttribute(Constants.OPERATION);
		}
		UserBizLogic userBizLogic = new UserBizLogic();
		final List<NameValueBean> userCollection = userBizLogic.getUsersNameValueList(operation);
		return userCollection;
	}
	
	/**
	 * This returns list of Clinical Diagnosis which contains specified string as an query.  
	 */
	public ActionForward getClinicalDiagnosisValues(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		String query = request.getParameter("query");
		ComboDataBizLogic comboDataBizObj = new ComboDataBizLogic();
		List<NameValueBean> diagnosisList = comboDataBizObj.getClinicalDiagnosisList(query,false);
		AppUtility.writeListAsJSon(diagnosisList, request, response);
		return null;
	}
	
	public ActionForward getAllSiteList(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		final String[] siteDisplayField = {"name"};
		final String valueField = "id";

		final String[] activityStatusArray = {Status.ACTIVITY_STATUS_DISABLED.toString(),
				Status.ACTIVITY_STATUS_CLOSED.toString()};
		
		final SiteBizLogic siteBizlog = new SiteBizLogic();
		final List<NameValueBean> siteResultList = siteBizlog.getAllSiteList(Site.class.getName(),
				siteDisplayField, valueField, activityStatusArray, false);
		StringBuffer responseString = new StringBuffer(Constants.XML_START);
		NameValueBean selectBean = new NameValueBean("-- Select --",Long.valueOf(-1));
		siteResultList.remove(siteResultList.indexOf(selectBean));
		responseString.append(Constants.XML_ROWS);
		for (NameValueBean nvb : siteResultList)
		{
			responseString.append(this.addRowToResponseXML(Long.valueOf(nvb.getValue()),null, nvb.getName()));
		}
		responseString.append(Constants.XML_ROWS_END);
		response.setContentType(Constants.CONTENT_TYPE_XML);
		response.getWriter().write(responseString.toString());
		return null;
	}
	
	public ActionForward getStorageContainerList(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		List <NameValueBean>containerList=new ArrayList<NameValueBean>();
		String contName=request.getParameter(Constants.CONTAINER_NAME);
			final SessionDataBean sessionData = (SessionDataBean) request.getSession()
					.getAttribute(Constants.SESSION_DATA);
			DAO dao = AppUtility.openDAOSession(sessionData);
			long cpId=0;
			if(!"".equals(request.getParameter(Constants.CAN_HOLD_COLLECTION_PROTOCOL)) && !"null".equals(request.getParameter(Constants.CAN_HOLD_COLLECTION_PROTOCOL)))
			{
				cpId=Long.parseLong(request.getParameter(Constants.CAN_HOLD_COLLECTION_PROTOCOL));
			}
			String spType=request.getParameter("specimenType");
			String spClass=request.getParameter(Constants.CAN_HOLD_SPECIMEN_CLASS);
			StorageContainerForSpecimenBizLogic bizLogic=new StorageContainerForSpecimenBizLogic();
			TreeMap treeMap=bizLogic.getAutoAllocatedContainerListForSpecimen(AppUtility.setparameterList(cpId,spClass,0,spType), sessionData, dao, contName);
			if(treeMap!=null)
			{
				containerList=AppUtility.convertMapToList(treeMap);
			}
			AppUtility.closeDAOSession(dao);
		StringBuffer responseString = new StringBuffer(Constants.XML_START);
		NameValueBean virtualBean = new NameValueBean("Virtual",Long.valueOf(-1));
		responseString.append(Constants.XML_ROWS);
		String tranferEventId=(String)request.getParameter("transferEventParametersId");
		if(tranferEventId==null || "0".equals(tranferEventId))
		{
			for (NameValueBean nvb : containerList)
				{
					responseString.append(this.addRowToResponseXML(Long.valueOf(nvb.getValue()),null, nvb.getName()));
				}
			responseString.append(this.addRowToResponseXML(Long.valueOf(virtualBean.getValue()),null, virtualBean.getName()));
		}
		responseString.append(Constants.XML_ROWS_END);
		response.setContentType(Constants.CONTENT_TYPE_XML);
		response.getWriter().write(responseString.toString());
		return null;
	}
	
	public ActionForward getStorageContainerListForRequestShipment(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
			{
		List <NameValueBean>containerList=new ArrayList<NameValueBean>();
		Map containerMap = new TreeMap();
		final StorageContainerForSpecimenBizLogic bizLogic = new StorageContainerForSpecimenBizLogic();
		final SessionDataBean sessionDataBean = (SessionDataBean) request.getSession().getAttribute(Constants.SESSION_DATA);
		final String contName=request.getParameter(Constants.CONTAINER_NAME);
		if (sessionDataBean != null)
		{
			DAO dao = null;
			try
			{
				dao = AppUtility.openDAOSession(sessionDataBean);
				String spClass = null;
				String spType = null;
				String specimenId = request.getParameter("specimenId");
				if (specimenId != null)
				{
					final String sourceObjectName = Specimen.class.getName();
					final String[] selectColumnName = {"specimenClass", "specimenType"};
					final QueryWhereClause queryWhereClause = new QueryWhereClause(sourceObjectName);
					queryWhereClause.addCondition(new EqualClause("id", Long.valueOf(specimenId)));
					final List list = dao.retrieve(sourceObjectName, selectColumnName, queryWhereClause);
					if (list.size() != 0)
					{
						final Object[] valArr = (Object[]) list.get(0);
						if (valArr != null)
						{
							spClass = ((String) valArr[0]);
							spType = ((String) valArr[1]);
						}
					}
					String collectionProtocolId = CollectionProtocolUtil.getCPIdFromSpecimen(specimenId, dao);
					request.setAttribute(edu.wustl.catissuecore.util.global.Constants.COLLECTION_PROTOCOL_ID,
							collectionProtocolId);

					if (!collectionProtocolId.trim().equals(""))
					{
						List<Object> parameterList = AppUtility.setparameterList(Long.valueOf(collectionProtocolId).longValue(),spClass,0,spType);
						containerMap = bizLogic.getAutoAllocatedContainerListForSpecimen(parameterList, sessionDataBean, dao,contName);
						System.out.println("hello");
						if(containerMap!=null)
						{
							containerList=AppUtility.convertMapToList(containerMap);
						}

						StringBuffer responseString = new StringBuffer(Constants.XML_START);
						NameValueBean virtualBean = new NameValueBean("Virtual",Long.valueOf(-1));
						//containerList.remove(containerList.indexOf(selectBean));
						responseString.append(Constants.XML_ROWS);
						for (NameValueBean nvb : containerList)
						{
							responseString.append(this.addRowToResponseXML(Long.valueOf(nvb.getValue()),null, nvb.getName()));
						}
						responseString.append(this.addRowToResponseXML(Long.valueOf(virtualBean.getValue()),null, virtualBean.getName()));
						responseString.append(Constants.XML_ROWS_END);
						response.setContentType(Constants.CONTENT_TYPE_XML);
						response.getWriter().write(responseString.toString());
						return null;
					}
				}
			}
			finally
			{
				if (dao != null)
				{
					dao.closeSession();
				}
			}

		}
		return null;

			}
	
		
	
	public ActionForward getStorageContainerListForSpecimenArray(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		List <NameValueBean>containerList=new ArrayList<NameValueBean>();
		String contName=request.getParameter(Constants.CONTAINER_NAME);
		String id = (String) request.getParameter(Constants.STORAGE_TYPE_ID);
		if(id!=null)
		{
			final SessionDataBean sessionData = (SessionDataBean) request.getSession()
					.getAttribute(Constants.SESSION_DATA);
			DAO dao = AppUtility.openDAOSession(sessionData);
			final StorageContainerForSpArrayBizLogic spbizLogic= new StorageContainerForSpArrayBizLogic();
			TreeMap containerMap = null;/*spbizLogic
					.getAllocatedContainerMapForSpecimenArray(Long.valueOf(id), sessionData,
							dao,contName);*/
			if(containerMap!=null)
			{
				containerList=AppUtility.convertMapToList(containerMap);
			}
		}
		StringBuffer responseString = new StringBuffer(Constants.XML_START);
		responseString.append(Constants.XML_ROWS);
		for (NameValueBean nvb : containerList)
		{
			responseString.append(this.addRowToResponseXML(Long.valueOf(nvb.getValue()),null, nvb.getName()));
		}
		responseString.append(Constants.XML_ROWS_END);
		response.setContentType(Constants.CONTENT_TYPE_XML);
		response.getWriter().write(responseString.toString());
		return null;
	}

	/**
	 * This function returns list of all active collection protocols
	 * @param mapping
	 * @param form
	 * @param request
	 * @param response
	 * @return
	 * @throws ApplicationException
	 * @throws IOException 
	 */
	public ActionForward getAllCPList(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		CollectionProtocolBizLogic cpBizLogic = new CollectionProtocolBizLogic();
		List<NameValueBean> cpList = cpBizLogic.getAllCPNameValueBeanList();
		StringBuffer responseString = new StringBuffer(Constants.XML_START);
		responseString.append(Constants.XML_ROWS);
		for (NameValueBean nvb : cpList)
		{
			responseString.append(this.addRowToResponseXML(Long.valueOf(nvb.getValue()),null, nvb.getName()));
		}
		responseString.append(Constants.XML_ROWS_END);
		response.setContentType(Constants.CONTENT_TYPE_XML);
		response.getWriter().write(responseString.toString());
		return null;
	}
	
	public ActionForward getClinicalStatusList(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		List<NameValueBean> csNameValueBeanList = CDEManager.getCDEManager().getPermissibleValueList(
				Constants.CDE_NAME_CLINICAL_STATUS, null);
		NameValueBean selectBean = new NameValueBean("-- Select --",Long.valueOf(-1));
		csNameValueBeanList.remove(csNameValueBeanList.indexOf(selectBean));
		StringBuffer responseString = new StringBuffer(Constants.XML_START);
		responseString.append(Constants.XML_ROWS);
		for (NameValueBean nvb : csNameValueBeanList)
		{
			responseString.append(this.addRowToResponseXML(null,nvb.getValue(), nvb.getName()));
		}
		responseString.append(Constants.XML_ROWS_END);
		response.setContentType(Constants.CONTENT_TYPE_XML);
		response.getWriter().write(responseString.toString());
		return null;
	}
	
	public ActionForward startSyncCP(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		SyncCPThreadExecuterImpl executerImpl = SyncCPThreadExecuterImpl.getInstance();
		String jobName = request.getParameter("cpTitle");
		executerImpl.startSync(jobName,(SessionDataBean)request.getSession().getAttribute(Constants.SESSION_DATA));
		return null;
	}
	
	public ActionForward stopSyncCP(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		SyncCPThreadExecuterImpl executerImpl = SyncCPThreadExecuterImpl.getInstance();
		String jobName = request.getParameter("cpTitle");
		executerImpl.stopSync(jobName);
		return null;
	}
	
	public ActionForward deleteSpecimen(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		String specIds = request.getParameter("specId");
		String tagId = request.getParameter("tagId");
		int numberOfParameter=specIds.split(",").length;
		StringBuffer sql = new StringBuffer("delete from catissue_spec_tag_items where OBJ_ID in (" );
		for(int i=0;i<numberOfParameter;i++)
		{
			sql.append("?");
			if(i+1!=numberOfParameter)
				sql.append(",");
		}
		sql.append(") and TAG_ID = ?");
		
		List<ColumnValueBean> list = new ArrayList<ColumnValueBean>();
		
		for (String specId : specIds.split(",")) {
			ColumnValueBean specIdBean = new ColumnValueBean(specId);
			list.add(specIdBean);
		}
		
		ColumnValueBean tagIdBean = new ColumnValueBean(tagId);
		list.add(tagIdBean);
		JDBCDAO jdbcdao = null;
		try
		{
		jdbcdao = AppUtility.openJDBCSession();
		jdbcdao.executeUpdate(sql.toString(), list);
		jdbcdao.commit();
		}
		finally
		{
			AppUtility.closeJDBCSession(jdbcdao);
		}
		return null;
	}
	
	public ActionForward deleteSpecimenList(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) throws ApplicationException, IOException
	{
		String specListId = request.getParameter("specListId");
		String sql1 = "delete from catissue_spec_tag_items where tag_id=?";
		String sql = "delete from catissue_specimenlist_tags where identifier=?";
		ColumnValueBean bean = new ColumnValueBean(specListId);
		List<ColumnValueBean> list = new ArrayList<ColumnValueBean>();
		list.add(bean);
		JDBCDAO jdbcdao = null;
		try
		{
		jdbcdao = AppUtility.openJDBCSession();
		jdbcdao.executeUpdate(sql1,list);
		jdbcdao.executeUpdate(sql,list);
		jdbcdao.commit();
		}
		finally
		{
			AppUtility.closeJDBCSession(jdbcdao);
		}
		return null;
	}
}