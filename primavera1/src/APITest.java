


import com.primavera.integration.client.Session;
import com.primavera.integration.client.EnterpriseLoadManager;
import com.primavera.integration.client.RMIURL;
import com.primavera.integration.common.DatabaseInstance;
import com.primavera.integration.client.bo.BOHelper;
import com.primavera.integration.client.bo.BOIterator;
import com.primavera.integration.client.bo.BusinessObject;
import com.primavera.integration.client.bo.helper.BOHelperMap;
import com.primavera.integration.client.bo.helper.ProjectHelper;
import com.primavera.integration.client.bo.object.Project;

	public class APITest
	{
	    public static void main( String[] args )
	    {
	        Session session = null;
	        try
	        {
	                       		DatabaseInstance[] dbInstances = Session.getDatabaseInstances(null);

	                        // Assume only one database instance for now, and hardcode the username and
	                        // password for this sample code.  Assume the server is local for this sample code.
	                        session = Session.login( null,
	                            dbInstances[0].getDatabaseId(), "admin", "admin" );

	                        EnterpriseLoadManager elm = session.getEnterpriseLoadManager();
	             
	              String[] fields = Project.getMainFields();
	              for(int i=0;i<fields.length;i++)System.out.print(" :: " + fields[i]);
	              System.out.println();
	              
	            BOIterator<Project> boi = elm.loadProjects( fields, null, fields[0] );

	            while ( boi.hasNext() )
	            {
	                Project proj = boi.next();
	                System.out.println(proj.getId()+ "  "+ proj.getName() + "  " + proj.getStatus() );
	                // и тебе привет
	              
	                
	            }
	            
	            System.out.println("OK");
	            session.logout();
	            System.exit(0);
	        }
	        catch ( Exception e )
	        {
	            // Best practices would involve catching specific exceptions.  To keep this
	            // sample code short, we catch Exception
	            e.printStackTrace();
	        }
	      
	    }
	}


