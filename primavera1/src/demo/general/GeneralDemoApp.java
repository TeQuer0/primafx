package demo.general;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import com.primavera.PrimaveraException;
import com.primavera.common.value.Duration;
import com.primavera.common.value.ObjectId;
import com.primavera.integration.client.GlobalObjectManager;
import com.primavera.integration.client.RMIURL;
import com.primavera.integration.client.Session;
import com.primavera.integration.client.bo.BOIterator;
import com.primavera.integration.client.bo.BusinessObjectException;
import com.primavera.integration.client.bo.enm.UDFDataType;
import com.primavera.integration.client.bo.enm.UDFSubjectArea;
import com.primavera.integration.client.bo.object.Activity;
import com.primavera.integration.client.bo.object.ActivityCode;
import com.primavera.integration.client.bo.object.ActivityCodeAssignment;
import com.primavera.integration.client.bo.object.ActivityCodeType;
import com.primavera.integration.client.bo.object.ActivityExpense;
import com.primavera.integration.client.bo.object.CostAccount;
import com.primavera.integration.client.bo.object.EPS;
import com.primavera.integration.client.bo.object.Project;
import com.primavera.integration.client.bo.object.UDFType;
import com.primavera.integration.client.bo.object.UDFValue;
import com.primavera.integration.common.DatabaseInstance;
import com.primavera.integration.util.BODeleteHelper;
import com.primavera.integration.util.BOHierarchyMap;
import com.primavera.integration.util.KeyNotFoundException;

public class GeneralDemoApp
  implements LoginCallback
{
    //~ Static fields/initializers -----------------------------------------------------------------

    private static final int NUM_ACTIVITIES_TO_ADD = 20;
    private static final int NUM_CODES_TO_ADD = 20;
    private static final int NUM_ACCOUNTS_TO_ASSIGN = 10;

    //~ Instance fields ----------------------------------------------------------------------------

    private Session session;

    //~ Methods ------------------------------------------------------------------------------------

    public static void main(String[] args)
    {
        WizardFrame lf = new WizardFrame(new GeneralDemoApp());
        lf.setVisible(true);
    }

    private String getURL(ConnectionInfo connInfo)
    {
        String sRmiUrl = null;

        if (REMOTE_MODE.equalsIgnoreCase(connInfo.sCallingMode))
        {
            sRmiUrl = RMIURL.getRmiUrl(connInfo.iRMIServiceMode, connInfo.sHost, connInfo.iPort);
        }

        return sRmiUrl;
    }

    @Override
    public boolean isRemoteModeAvailable()
    {
        return Session.isRemoteModeAvailable();
    }

    @Override
    public DatabaseInstance[] getDatabaseInstances(ConnectionInfo connInfo)
      throws PrimaveraException
    {
        // Load the available database instances
        return Session.getDatabaseInstances(getURL(connInfo));
        
    }

    @Override
    public void login(ConnectionInfo connInfo, LoginInfo loginInfo)
      throws PrimaveraException
    {
        session = Session.login(getURL(connInfo), loginInfo.sDatabaseId, loginInfo.sUserName, loginInfo.sPassword);
    }

    @Override
    public void logout()
    {
        session.logout();
    }

    @Override
    public void runDemo(DemoInfo demoInfo)
    {
        String sProjectId = demoInfo.sProjectId;
        String sProjectName = demoInfo.sProjectName;

        // Use BODeleteHelper utility class for easy clean up
        BODeleteHelper deleteHelper = new BODeleteHelper();

        try
        {
            GlobalObjectManager gom = session.getGlobalObjectManager();
            Project project = new Project(session);
            // Required for creation of a Project is ParentEPSObjectId.  You can
            // call project.getRequiredCreateFields() to get the list of required
            // fields.  This demo will first load the EPS objects from the
            // database, and then pick one to be the parent of our new Project
            BOIterator<EPS> boiEPS = gom.loadEPS(new String[]
                {
                    "ObjectId", "Name", "Id", "ParentObjectId"
                }, null, null);

            // For this demo, load all EPS objects into a BOHierarchyMap to
            // build the hierarchy on the client
            BOHierarchyMap boMap = new BOHierarchyMap("EPS");
            String sEPSName = null;
            ObjectId objIdEPS = null;
            System.out.println("Loading EPS Objects");
            System.out.println();

            while (boiEPS.hasNext())
            {
                EPS eps = boiEPS.next();

                // The first EPS returned will be used as the parent of our soon
                // to be added project
                if (objIdEPS == null)
                {
                    objIdEPS = eps.getObjectId();
                    sEPSName = eps.getName();
                }

                // Add this eps to the hierarchy map
                boMap.addKey(eps.getObjectId(), eps.getParentObjectId(), eps);
            }

            displayEPSHierarchy(boMap);
            // Create our new Project in the database, first setting the
            // ParentEPSObjectId, and the project ID and project name specified
            // by the user in the login dialog
            System.out.println();
            System.out.println("Attempting to add Project " + sProjectId + " to EPS " + sEPSName + ".");
            project.setParentEPSObjectId(objIdEPS);
            project.setId(sProjectId);
            project.setName(sProjectName);

            ObjectId objIdProject = project.create();
            System.out.println("Project was successfully created.  ObjectId of new project is " + objIdProject + ".");
            // Add the project ObjectId to the deletion helper for easy cleanup
            deleteHelper.add("Project", objIdProject);
            // Load the newly created Project
            project = Project.load(session, null, objIdProject);

            // Add some activities to this project
            Activity[] activities = new Activity[NUM_ACTIVITIES_TO_ADD];

            for (int i = 1; i <= NUM_ACTIVITIES_TO_ADD; i++)
            {
                Activity activity = new Activity(session);
                activities[i - 1] = activity;

                // WBSObjectId is the only field required for creating an
                // Activity.  However, this field can actually be null if the
                // ProjectObjectId is set, and the activity will then be added
                // at the root (project-level) WBS
                // Generate ID of new Activity
                StringBuilder sbId = new StringBuilder();
                sbId.append("AS1");

                if (i < 10)
                {
                    sbId.append("0");
                }

                sbId.append(i);
                activity.setId(sbId.toString());
                activity.setName("My newly added Activity #" + i);
                activity.setPlannedDuration(new Duration((i * 3)));
                activity.setRemainingDuration(new Duration((i * 3)));
            }

            System.out.println();
            System.out.println("Attempting to add twenty Activity objects.");

            ObjectId[] objIdCreatedActivities = project.createProjectLevelActivities(activities);
            System.out.println("Activities were successfully added.");
            // Add the activities to the deletion helper for easy cleanup later
            deleteHelper.add("Activity", objIdCreatedActivities);
            // Attempt a new load from the Project of specific activities,
            // ordering them by Id in descending order
            System.out.println();
            System.out.println("Attempting to load Activity objects that have PlannedDuration > 10 AND PlannedDuration < 30, and order the results by Id in descending order.");

            List<Activity> alActivities = new ArrayList<Activity>();
            BOIterator<Activity> boiActivities = project.loadProjectLevelActivities(new String[]
                {
                    "ObjectId", "Id", "PlannedDuration"
                }, "PlannedDuration > 10 AND PlannedDuration < 30", "Id desc");

            while (boiActivities.hasNext())
            {
                Activity activity = boiActivities.next();
                System.out.println("Activity   Id=" + activity.getId() + "   PlannedDuration=" + activity.getPlannedDuration());
                // Set the value of PlannedDuration to be 1.5 * the current
                // value for this demo
                activity.setPlannedDuration(new Duration(activity.getPlannedDuration().doubleValue() * 1.5));
                alActivities.add(activity);
            }

            // Update the activities all at once
            System.out.println();
            System.out.println("Attempting to update Activity objects.");

            Activity[] activitiesToUpdate = new Activity[alActivities.size()];
            ObjectId[] objIdActivitiesToLoad = new ObjectId[alActivities.size()];

            for (int i = 0; i < alActivities.size(); i++)
            {
                Activity act = alActivities.get(i);
                activitiesToUpdate[i] = act;
                objIdActivitiesToLoad[i] = act.getObjectId();
            }

            Activity.update(session, activitiesToUpdate);
            System.out.println("Activities were successfully updated.");
            // Load the activities from the server
            alActivities.clear();
            System.out.println();
            System.out.println("Attempting to reload the updated Activity objects.");
            boiActivities = Activity.load(session, new String[] {"ObjectId", "Id", "PlannedDuration"}, objIdActivitiesToLoad);

            while (boiActivities.hasNext())
            {
                Activity activity = boiActivities.next();
                System.out.println("Activity   Id=" + activity.getId() + "   PlannedDuration=" + activity.getPlannedDuration());
                // Set the value of PlannedDuration to be 1.5 * the current value
                activity.setPlannedDuration(new Duration(activity.getPlannedDuration().doubleValue() * 1.5));
                alActivities.add(activity);
            }

            // Add some ActivityExpenses.  First load the cost accounts if they
            // exist
            ObjectId objIdCostAccount = null;
            BOIterator<CostAccount> boiCostAccounts = gom.loadCostAccounts(new String[]
                {
                    "ObjectId", "Id", "Name"
                }, null, null);
            List<CostAccount> alAccounts = new ArrayList<CostAccount>();

            while (boiCostAccounts.hasNext() && (alAccounts.size() < NUM_ACCOUNTS_TO_ASSIGN))
            {
                CostAccount account = boiCostAccounts.next();
                objIdCostAccount = account.getObjectId();
                System.out.println("CostAccount  ObjectId=" + objIdCostAccount + "   Id=" + account.getId() + "   Name=" + account.getName());
                alAccounts.add(account);
            }

            System.out.println();

            for (int i = 0; (i < alActivities.size()) && (alAccounts.size() > 0); i++)
            {
                ActivityExpense[] expenses = new ActivityExpense[alAccounts.size()];

                for (int j = 0; j < alAccounts.size(); j++)
                {
                    System.out.println("Creating ActivityExpense object, assigning CostAccount " + alAccounts.get(j).getId());

                    ActivityExpense expense = new ActivityExpense(session);
                    expense.setExpenseItem("New Expense Item " + (j + 1));
                    expense.setCostAccountObjectId(alAccounts.get(j).getObjectId());
                    expenses[j] = expense;
                }

                Activity activity = alActivities.get(i);
                System.out.println("Adding expenses for Activity " + activity.getId() + ".");
                System.out.println();

                ObjectId[] objIdCreatedActivityExpenses = activity.createActivityExpenses(expenses);
                // Add the activity expenses to the deletion helper for easy
                // cleanup later
                deleteHelper.add("ActivityExpense", objIdCreatedActivityExpenses);
            }

            // Add two ActivityCodeTypes, one will be project-specific
            ActivityCodeType newGlobalActCodeType = new ActivityCodeType(session);
            newGlobalActCodeType.setName("New Demo GlobalActivityCodeType");
            newGlobalActCodeType.setLength(20);

            ActivityCodeType newProjectActCodeType = new ActivityCodeType(session);
            newProjectActCodeType.setName("New Demo ProjectActivityCodeType");
            newProjectActCodeType.setLength(20);
            newProjectActCodeType.setProjectObjectId(objIdProject);
            System.out.println("Attempting to add a global ActivityCodeType and a project-specific ActivityCodeType.");

            ObjectId[] objIdCreatedActivityCodeTypes = gom.createActivityCodeTypes(new ActivityCodeType[]
                {
                    newGlobalActCodeType, newProjectActCodeType
                });
            System.out.println("ActivityCodeTypes were successfully added.");

            // The order of the ObjectIds returned by a create method matches
            // the order of the objects created, so the ObjectId of the newly
            // created global ActivityCodeType will be at index 0.
            ObjectId objIdNewGlobalActCodeType = objIdCreatedActivityCodeTypes[0];
            ObjectId objIdNewProjectActCodeType = objIdCreatedActivityCodeTypes[1];
            // Add the activity code types to the deletion helper for easy
            // cleanup later
            deleteHelper.add("ActivityCodeType", objIdCreatedActivityCodeTypes);

            // Add some code values for the newly added code types.  First load
            // the newly created objects from the server to ensure data
            // integrity (this would be even more necessary for objects with
            // complex business rules, such as Activity, ResourceAssignment, etc.
            BOIterator<ActivityCodeType> boiActCodeTypes = ActivityCodeType.load(session, ActivityCodeType.getAllFields(), objIdCreatedActivityCodeTypes);
            ObjectId[] objIdCreatedGlobalActCodes = null;
            ObjectId[] objIdCreatedProjectActCodes = null;

            while (boiActCodeTypes.hasNext())
            {
                ActivityCodeType actCodeType = boiActCodeTypes.next();
                boolean bGlobal = false;

                if (actCodeType.getObjectId().equals(objIdNewGlobalActCodeType))
                {
                    bGlobal = true;
                }

                String sType = (bGlobal) ? "Global" : "Project";
                ActivityCode[] newActCodes = new ActivityCode[NUM_CODES_TO_ADD];

                for (int i = 0; i < NUM_CODES_TO_ADD; i++)
                {
                    ActivityCode actCode = new ActivityCode(session);
                    actCode.setCodeTypeObjectId(actCodeType.getObjectId());
                    actCode.setCodeValue(sType + " Act Code " + (i + 1));
                    newActCodes[i] = actCode;
                }

                System.out.println();
                System.out.println("Attempting to add " + sType + " ActivityCodes.");

                ObjectId[] objIdCreatedActivityCodes = ActivityCode.create(session, newActCodes);

                if (bGlobal)
                {
                    objIdCreatedGlobalActCodes = objIdCreatedActivityCodes;

                    for (int h = 0; h < objIdCreatedGlobalActCodes.length; h++)
                    {
                        System.out.println("Created code " + objIdCreatedGlobalActCodes[h] + " for Global ActivityCodeType " + actCodeType.getObjectId() + ".");
                    }
                }
                else
                {
                    objIdCreatedProjectActCodes = objIdCreatedActivityCodes;

                    for (int h = 0; h < objIdCreatedProjectActCodes.length; h++)
                    {
                        System.out.println("Created code " + objIdCreatedProjectActCodes[h] + " for Project ActivityCodeType " + actCodeType.getObjectId() + ".");
                    }
                }

                System.out.println(sType + " ActivityCodes were added successfully.");
                // Add the activity codes to the deletion helper for easy cleanup later
                deleteHelper.add("ActivityCode", objIdCreatedActivityCodes);
            }

            // Assign codes to activities using the ActivityCodeAssignment business object
            System.out.println();
            System.out.println("Assigning global and project-specific ActivityCodes to Activities");

            List<ActivityCodeAssignment> alCodeAssignments = new ArrayList<ActivityCodeAssignment>();

            for (int i = 0; (i < alActivities.size()) && (i < objIdCreatedGlobalActCodes.length);
                    i++)
            {
                Activity activity = alActivities.get(i);
                ActivityCodeAssignment globalAssignment = new ActivityCodeAssignment(session);
                globalAssignment.setActivityObjectId(activity.getObjectId());
                globalAssignment.setActivityCodeObjectId(objIdCreatedGlobalActCodes[i]);
                alCodeAssignments.add(globalAssignment);

                ActivityCodeAssignment projectAssignment = new ActivityCodeAssignment(session);
                projectAssignment.setActivityObjectId(activity.getObjectId());
                projectAssignment.setActivityCodeObjectId(objIdCreatedProjectActCodes[i]);
                alCodeAssignments.add(projectAssignment);
            }

            ObjectId[] codeAsgnObjIds = ActivityCodeAssignment.create(session, alCodeAssignments.toArray(new ActivityCodeAssignment[alCodeAssignments.size()]));
            System.out.println("ActivityCodeAssignments were successfully updated.");
            // Add the activity code assignments to the deletion helper for easy cleanup later
            deleteHelper.add("ActivityCodeAssignment", codeAsgnObjIds);

            // Load text UDF type with subject area of Activity
            ObjectId udfTypeObjectId = null;
            BOIterator<UDFType> boiUDFs = gom.loadUDFTypes(null, "SubjectArea = '" + UDFSubjectArea.ACTIVITY.getValue() + "' AND DataType = '" + UDFDataType.TEXT.getValue() + "'", null);

            if (boiUDFs.hasNext())
            {
                udfTypeObjectId = (boiUDFs.next()).getObjectId();
            }
            else
            {
                System.out.println("Creating a new UDFType");

                // Create a new UDFType
                UDFType newType = new UDFType(session);
                newType.setDataType(UDFDataType.TEXT);
                newType.setSubjectArea(UDFSubjectArea.ACTIVITY);
                newType.setTitle("Test Activity UDF");
                udfTypeObjectId = newType.create();
                // Add the UDFType to the deletion helper for easy cleanup later
                deleteHelper.add("UDFType", udfTypeObjectId);
            }

            // Create a text UDFValue assigned to an activity
            System.out.println("Creating a new text UDFValue assigned to an activity");

            UDFValue udfValue = new UDFValue(session);
            udfValue.setText("Test value");
            udfValue.setUDFTypeObjectId(udfTypeObjectId);
            udfValue.setForeignObjectId(alActivities.get(0).getObjectId());

            ObjectId udfValueObjId = udfValue.create();
            // Add the UDFValue to the deletion helper for easy cleanup later
            deleteHelper.add("UDFValue", udfValueObjId);
            System.out.println();
            JOptionPane.showMessageDialog(null, "Project with Activities, ActivityExpenses, and ActivityCodes was successfully added.", "Test Successful", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            // Delete the business objects that were added
            System.out.println(deleteHelper.toString());
            deleteHelper.deleteAll(session);
            session.logout();
        }

        // Shutdown completely and kill all threads
        System.exit(0);
    }

    private void displayEPSHierarchy(BOHierarchyMap boMap)
    {
        try
        {
            // Make sure the eps hierarchy is not missing any nodes
            boMap.validateIntegrity();
        }
        catch (KeyNotFoundException e)
        {
            System.out.println("EPS hierarchy is not complete!  Your database is missing at least one parent EPS record.");

            return;
        }

        ObjectId[] rootIds = boMap.getRootKeys();

        try
        {
            for (int i = 0; i < rootIds.length; i++)
            {
                displayEPSAndChildren(boMap, rootIds[i], "");
            }
        }
        catch (KeyNotFoundException e)
        {
            System.out.println("EPS hierarchy is not complete!  Your database is missing at least one parent EPS record.");

            return;
        }
        catch (BusinessObjectException e)
        {
            System.out.println("EPS object is missing needed data.");

            return;
        }
    }

    private void displayEPSAndChildren(BOHierarchyMap boMap, ObjectId origId, String sSpacing)
      throws KeyNotFoundException, BusinessObjectException
    {
        EPS eps = (EPS)boMap.getValue(origId);
        System.out.println(sSpacing + eps.getId() + " - " + eps.getName());

        ObjectId[] children = boMap.getKeyChildren(origId);

        for (int i = 0; i < children.length; i++)
        {
            displayEPSAndChildren(boMap, children[i], sSpacing + "   ");
        }
    }
}
