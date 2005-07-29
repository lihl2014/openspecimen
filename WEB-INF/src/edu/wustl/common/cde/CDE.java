/**
 * <p>Title: CDE Interface
 * <p>Description: This interface corresponds to the CDE.</p> 
 *<p>The methods in this interface can be used to obtain the inforamtion about the CDE.</p> 
 * Copyright:    Copyright (c) year
 * Company: Washington University, School of Medicine, St. Louis.
 * @author Mandar Deshmukh
 * @version 1.00
 * Created on May 26, 2005
 */

package edu.wustl.common.cde;

/**
 * @author Mandar_Deshmukh
 * <p>This interface corresponds to the CDE.</p> 
 *<p>The methods in this interface can be used to obtain the inforamtion about the CDE.</p> 
 *
 */
public interface CDE
{

    /**
     * This method is used to get the publicid of the CDE 
     * @return returns a String object that contains the PUBLICID of the CDE 
     */
    String getPublicId();

    /**
     * This method is used to get the longname of the CDE 
     * @return returns a String object that contains the LongName of the CDE 
     */
    String getLongName();

    /**
     * This method is used to get the defination of the CDE 
     * @return returns a String object that contains the Defination of the CDE 
     */
    String getDefination();

} // cde interface
