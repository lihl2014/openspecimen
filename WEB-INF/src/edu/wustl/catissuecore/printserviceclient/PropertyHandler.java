package edu.wustl.catissuecore.printserviceclient;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import edu.wustl.common.util.global.CommonServiceLocator;
import edu.wustl.common.util.logger.Logger;

/**
 * This class has functions to read PrintServiceImplementor Properties file.
 * @author falguni sachde
 *
 */
public class PropertyHandler {

	private static Logger logger = Logger.getCommonLogger(PropertyHandler.class);
	/**
	 * 
	 */
	private static Properties printimplClassProperties = null;


	/**
	 * @param path
	 * @throws Exception
	 */
	public static void init(String path) throws Exception
	{
		try
		{
			String absolutePath=CommonServiceLocator.getInstance().getPropDirPath() +File.separator+path;
			InputStream inpurStream= new FileInputStream(new File(absolutePath));
			printimplClassProperties = new Properties();
			printimplClassProperties.load(inpurStream);
			
			/*printimplClassProperties = new Properties();
			
			printimplClassProperties.load(PropertyHandler.class.getClassLoader().getResourceAsStream(path));*/					
				
		}
		catch(Exception e)
		{
			logger.debug(e.getMessage(), e);
			e.printStackTrace();
		}
		
			
	}
	
	/**
	 * Description:This method takes the property name as String argument and
	 * returns the properties value as String. 
	 * @param propertyName  name of property Key  
	 * @return String	property value
	 * @throws Exception
	 */
	public static String getValue(String propertyName) throws Exception
	{
		
		if (printimplClassProperties == null)
		{
			init("PrintServiceImplementor.properties");
		}
		return (String)printimplClassProperties.get(propertyName);

	}
}
