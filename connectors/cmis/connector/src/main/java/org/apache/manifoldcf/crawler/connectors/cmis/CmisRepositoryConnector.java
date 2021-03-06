/* $Id: CmisRepositoryConnector.java 1549105 2013-12-08 18:54:09Z kwright $ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.crawler.connectors.cmis;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Repository;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.chemistry.opencmis.commons.exceptions.CmisConnectionException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisPermissionDeniedException;
import org.apache.chemistry.opencmis.commons.impl.Constants;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.DocumentSpecification;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;
import org.apache.manifoldcf.crawler.system.Logging;

/**
 * This is the "repository connector" for a CMIS-compliant repository.
 * 
 * @author Piergiorgio Lucidi
 */
public class CmisRepositoryConnector extends BaseRepositoryConnector {

  private static final String JOB_STARTPOINT_NODE_TYPE = "startpoint";

  protected final static String ACTIVITY_READ = "read document";
  protected static final String RELATIONSHIP_CHILD = "child";

  private static final String CMIS_FOLDER_BASE_TYPE = "cmis:folder";
  private static final String CMIS_DOCUMENT_BASE_TYPE = "cmis:document";
  private static final SimpleDateFormat ISO8601_DATE_FORMATTER = new SimpleDateFormat(
      "yyyy-MM-dd'T'HH:mm:ssZ");

  // Tab name properties
  
  private static final String CMIS_SERVER_TAB_PROPERTY = "CmisRepositoryConnector.Server";
  private static final String CMIS_QUERY_TAB_PROPERTY = "CmisRepositoryConnector.CMISQuery";

  
  // Template names

    /** Forward to the javascript to check the configuration parameters */
  private static final String EDIT_CONFIG_HEADER_FORWARD = "editConfiguration.js";

  /** Server tab template */
  private static final String EDIT_CONFIG_FORWARD_SERVER = "editConfiguration_Server.html";

  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPEC_HEADER_FORWARD = "editSpecification.js";
  
  /** Forward to the template to edit the configuration parameters for the job */
  private static final String EDIT_SPEC_FORWARD_CMISQUERY = "editSpecification_CMISQuery.html";
  
  /** Forward to the HTML template to view the configuration parameters */
  private static final String VIEW_CONFIG_FORWARD = "viewConfiguration.html";
  
  /** Forward to the template to view the specification parameters for the job */
  private static final String VIEW_SPEC_FORWARD = "viewSpecification.html";
  
  
  /**
   * CMIS Session handle
   */
  Session session = null;

  protected String username = null;
  protected String password = null;
  
  /** Endpoint protocol */
  protected String protocol = null;
  
  /** Endpoint server name */
  protected String server = null;
  
  /** Endpoint port */
  protected String port = null;
  
  /** Endpoint context path of the Alfresco webapp */
  protected String path = null;
  
  protected String repositoryId = null;
  protected String binding = null;

  protected SessionFactory factory = SessionFactoryImpl.newInstance();
  protected Map<String, String> parameters = new HashMap<String, String>();

  public final static String ACTIVITY_FETCH = "fetch";

  protected static final long timeToRelease = 300000L;
  protected long lastSessionFetch = -1L;

  /**
   * Constructor
   */
  public CmisRepositoryConnector() {
    super();
  }

  /** 
   * Return the list of activities that this connector supports (i.e. writes into the log).
   * @return the list.
   */
  @Override
  public String[] getActivitiesList() {
    return new String[] { ACTIVITY_FETCH };
  }

  /** Get the bin name strings for a document identifier.  The bin name describes the queue to which the
   * document will be assigned for throttling purposes.  Throttling controls the rate at which items in a
   * given queue are fetched; it does not say anything about the overall fetch rate, which may operate on
   * multiple queues or bins.
   * For example, if you implement a web crawler, a good choice of bin name would be the server name, since
   * that is likely to correspond to a real resource that will need real throttle protection.
   *@param documentIdentifier is the document identifier.
   *@return the set of bin names.  If an empty array is returned, it is equivalent to there being no request
   * rate throttling available for this identifier.
   */
  @Override
  public String[] getBinNames(String documentIdentifier) {
    return new String[] { server };
  }

  protected class GetSessionThread extends Thread {
    protected Throwable exception = null;

    public GetSessionThread() {
      super();
      setDaemon(true);
    }

    public void run() {
      try {
        // Create a session
        parameters.clear();

        // user credentials
        parameters.put(SessionParameter.USER, username);
        parameters.put(SessionParameter.PASSWORD, password);

        String endpoint = protocol+"://"+server+":"+port+path;
        
        // connection settings
        if(CmisConfig.BINDING_ATOM_VALUE.equals(binding)){
          //AtomPub protocol
          parameters.put(SessionParameter.ATOMPUB_URL, endpoint);
          parameters.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
        } else if(CmisConfig.BINDING_WS_VALUE.equals(binding)){
          //Web Services - SOAP - protocol
          parameters.put(SessionParameter.BINDING_TYPE, BindingType.WEBSERVICES.value());
          parameters.put(SessionParameter.WEBSERVICES_ACL_SERVICE, endpoint+"/ACLService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_DISCOVERY_SERVICE, endpoint+"/DiscoveryService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_MULTIFILING_SERVICE, endpoint+"/MultiFilingService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_NAVIGATION_SERVICE, endpoint+"/NavigationService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_OBJECT_SERVICE, endpoint+"/ObjectService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_POLICY_SERVICE, endpoint+"/PolicyService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_RELATIONSHIP_SERVICE, endpoint+"/RelationshipService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_REPOSITORY_SERVICE, endpoint+"/RepositoryService?wsdl");
          parameters.put(SessionParameter.WEBSERVICES_VERSIONING_SERVICE, endpoint+"/VersioningService?wsdl");
        }
        // create session
        if (StringUtils.isEmpty(repositoryId)) {


          // get a session from the first CMIS repository exposed by
          // the endpoint
          List<Repository> repos = null;
          try {
            repos = factory.getRepositories(parameters);
            session = repos.get(0).createSession();
          } catch (Exception e) {
            Logging.connectors.error("CMIS: Error during getting CMIS repositories. Please check the endpoint parameters: " + e.getMessage(), e);
            this.exception = e;
          }
          
        } else {

          // get a session from the repository specified in the
          // configuration with its own ID
          parameters.put(SessionParameter.REPOSITORY_ID, repositoryId);
          
          try {
            session = factory.createSession(parameters);
          } catch (Exception e) {
            Logging.connectors.error("CMIS: Error during the creation of the new session. Please check the endpoint parameters: " + e.getMessage(), e);
            this.exception = e;
          }
        
        }

      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public Throwable getException() {
      return exception;
    }
  }

  protected class CheckConnectionThread extends Thread {
    protected Throwable exception = null;

    public CheckConnectionThread() {
      super();
      setDaemon(true);
    }

    public void run() {
      try {
        session.getRepositoryInfo();
      } catch (Throwable e) {
        Logging.connectors.warn("CMIS: Error checking repository: "+e.getMessage(),e);
        this.exception = e;
      }
    }

    public Throwable getException() {
      return exception;
    }

  }

  protected class DestroySessionThread extends Thread {
    protected Throwable exception = null;

    public DestroySessionThread() {
      super();
      setDaemon(true);
    }

    public void run() {
      try {
        session = null;
      } catch (Throwable e) {
        this.exception = e;
      }
    }

    public Throwable getException() {
      return exception;
    }

  }

  /** 
   * Close the connection.  Call this before discarding the connection.
   */
  @Override
  public void disconnect() throws ManifoldCFException {
    if (session != null) {
      DestroySessionThread t = new DestroySessionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else
            throw (Error) thr;
        }
        session = null;
        lastSessionFetch = -1L;
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn(
            "CMIS: Transient remote exception closing session: "
                + e.getMessage(), e);
      }

    }

    username = null;
    password = null;
    protocol = null;
    server = null;
    port = null;
    path = null;
    binding = null;
    repositoryId = null;

  }

  /**
   * This method create a new CMIS session for a CMIS repository, if the
   * repositoryId is not provided in the configuration, the connector will
   * retrieve all the repositories exposed for this endpoint the it will start
   * to use the first one.
   * @param configParameters is the set of configuration parameters, which
   * in this case describe the target appliance, basic auth configuration, etc.  (This formerly came
   * out of the ini file.)
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
    username = params.getParameter(CmisConfig.USERNAME_PARAM);
    password = params.getParameter(CmisConfig.PASSWORD_PARAM);
    protocol = params.getParameter(CmisConfig.PROTOCOL_PARAM);
    server = params.getParameter(CmisConfig.SERVER_PARAM);
    port = params.getParameter(CmisConfig.PORT_PARAM);
    path = params.getParameter(CmisConfig.PATH_PARAM);
    
    binding = params.getParameter(CmisConfig.BINDING_PARAM);
    if (StringUtils.isNotEmpty(params.getParameter(CmisConfig.REPOSITORY_ID_PARAM)))
      repositoryId = params.getParameter(CmisConfig.REPOSITORY_ID_PARAM);
  }

  /** Test the connection.  Returns a string describing the connection integrity.
   *@return the connection's status as a displayable string.
   */
  @Override
  public String check() throws ManifoldCFException {
    try {
      checkConnection();
      return super.check();
    } catch (ServiceInterruption e) {
      return "Connection temporarily failed: " + e.getMessage();
    } catch (ManifoldCFException e) {
      return "Connection failed: " + e.getMessage();
    }
  }

  /** Set up a session */
  protected void getSession() throws ManifoldCFException, ServiceInterruption {
    if (session == null) {
      // Check for parameter validity
      
      if (StringUtils.isEmpty(binding))
        throw new ManifoldCFException("Parameter " + CmisConfig.BINDING_PARAM
            + " required but not set");
      
      if (StringUtils.isEmpty(username))
        throw new ManifoldCFException("Parameter " + CmisConfig.USERNAME_PARAM
            + " required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("CMIS: Username = '" + username + "'");

      if (StringUtils.isEmpty(password))
        throw new ManifoldCFException("Parameter " + CmisConfig.PASSWORD_PARAM
            + " required but not set");

      Logging.connectors.debug("CMIS: Password exists");

      if (StringUtils.isEmpty(protocol))
        throw new ManifoldCFException("Parameter " + CmisConfig.PROTOCOL_PARAM
            + " required but not set");
      
      if (StringUtils.isEmpty(server))
        throw new ManifoldCFException("Parameter " + CmisConfig.SERVER_PARAM
            + " required but not set");
      
      if (StringUtils.isEmpty(port))
        throw new ManifoldCFException("Parameter " + CmisConfig.PORT_PARAM
            + " required but not set");
      
      if (StringUtils.isEmpty(path))
        throw new ManifoldCFException("Parameter " + CmisConfig.PATH_PARAM
            + " required but not set");

      long currentTime;
      GetSessionThread t = new GetSessionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof java.net.MalformedURLException)
            throw (java.net.MalformedURLException) thr;
          else if (thr instanceof NotBoundException)
            throw (NotBoundException) thr;
          else if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else if (thr instanceof CmisConnectionException)
            throw new ManifoldCFException("CMIS: Error during getting a new session: " + thr.getMessage(), thr);
          else if (thr instanceof CmisPermissionDeniedException)
            throw new ManifoldCFException("CMIS: Wrong credentials during getting a new session: " + thr.getMessage(), thr);
          else
            throw (Error) thr;
        }
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (java.net.MalformedURLException e) {
        throw new ManifoldCFException(e.getMessage(), e);
      } catch (NotBoundException e) {
        // Transient problem: Server not available at the moment.
        Logging.connectors.warn(
            "CMIS: Server not up at the moment: " + e.getMessage(), e);
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(), currentTime + 60000L);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        // Treat this as a transient problem
        Logging.connectors.warn(
            "CMIS: Transient remote exception creating session: "
                + e.getMessage(), e);
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(), currentTime + 60000L);
      }

    }

    lastSessionFetch = System.currentTimeMillis();
  }

  /**
   * Release the session, if it's time.
   */
  protected void releaseCheck() throws ManifoldCFException {
    if (lastSessionFetch == -1L)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
      DestroySessionThread t = new DestroySessionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else
            throw (Error) thr;
        }
        session = null;
        lastSessionFetch = -1L;
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn(
            "CMIS: Transient remote exception closing session: "
                + e.getMessage(), e);
      }

    }
  }

  protected void checkConnection() throws ManifoldCFException,
      ServiceInterruption {
    while (true) {
      boolean noSession = (session == null);
      getSession();
      long currentTime;
      CheckConnectionThread t = new CheckConnectionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else if (thr instanceof CmisConnectionException)
            throw new ManifoldCFException("CMIS: Error during checking connection: " + thr.getMessage(), thr);
          else
            throw (Error) thr;
        }
        return;
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        if (noSession) {
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption(
              "Transient error connecting to filenet service: "
                  + e.getMessage(), currentTime + 60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  /** 
   * This method is periodically called for all connectors that are connected but not
   * in active use.
   */
  @Override
  public void poll() throws ManifoldCFException {
    if (lastSessionFetch == -1L)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
      DestroySessionThread t = new DestroySessionThread();
      try {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null) {
          if (thr instanceof RemoteException)
            throw (RemoteException) thr;
          else
            throw (Error) thr;
        }
        session = null;
        lastSessionFetch = -1L;
      } catch (InterruptedException e) {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
            ManifoldCFException.INTERRUPTED);
      } catch (RemoteException e) {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException
            || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(), e2,
              ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn(
            "CMIS: Transient remote exception closing session: "
                + e.getMessage(), e);
      }

    }
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return session != null;
  }

  /** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
   * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
   *
   * This method can choose to find repository changes that happen only during the specified time interval.
   * The seeds recorded by this method will be viewed by the framework based on what the
   * getConnectorModel() method returns.
   *
   * It is not a big problem if the connector chooses to create more seeds than are
   * strictly necessary; it is merely a question of overall work required.
   *
   * The times passed to this method may be interpreted for greatest efficiency.  The time ranges
   * any given job uses with this connector will not overlap, but will proceed starting at 0 and going
   * to the "current time", each time the job is run.  For continuous crawling jobs, this method will
   * be called once, when the job starts, and at various periodic intervals as the job executes.
   *
   * When a job's specification is changed, the framework automatically resets the seeding start time to 0.  The
   * seeding start time may also be set to 0 on each job run, depending on the connector model returned by
   * getConnectorModel().
   *
   * Note that it is always ok to send MORE documents rather than less to this method.
   *@param activities is the interface this method should use to perform whatever framework actions are desired.
   *@param spec is a document specification (that comes from the job).
   *@param startTime is the beginning of the time range to consider, inclusive.
   *@param endTime is the end of the time range to consider, exclusive.
   *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
   */
  @Override
  public void addSeedDocuments(ISeedingActivity activities,
      DocumentSpecification spec, long startTime, long endTime, int jobMode)
      throws ManifoldCFException, ServiceInterruption {

    getSession();

    String cmisQuery = StringUtils.EMPTY;
    int i = 0;
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        cmisQuery = sn.getAttributeValue(CmisConfig.CMIS_QUERY_PARAM);
        break;
      }
      i++;
    }

    if (StringUtils.isEmpty(cmisQuery)) {
      // get root Documents from the CMIS Repository
      ItemIterable<CmisObject> cmisObjects = session.getRootFolder()
          .getChildren();
      for (CmisObject cmisObject : cmisObjects) {
        activities.addSeedDocument(cmisObject.getId());
      }
    } else {
      ItemIterable<QueryResult> results = session.query(cmisQuery, false);
      for (QueryResult result : results) {
        String id = result.getPropertyValueById(PropertyIds.OBJECT_ID);
        activities.addSeedDocument(id);
      }
    }

  }

  /** 
   * Get the maximum number of documents to amalgamate together into one batch, for this connector.
   * @return the maximum number. 0 indicates "unlimited".
   */
  @Override
  public int getMaxDocumentRequest() {
    return 1;
  }

  /**
   * Return the list of relationship types that this connector recognizes.
   * 
   * @return the list.
   */
  @Override
  public String[] getRelationshipTypes() {
    return new String[] { RELATIONSHIP_CHILD };
  }

  /**
   * Read the content of a resource, replace the variable ${PARAMNAME} with the
   * value and copy it to the out.
   * 
   * @param resName
   * @param out
   * @throws ManifoldCFException
   */
  private static void outputResource(String resName, IHTTPOutput out,
      Locale locale, Map<String,String> paramMap) throws ManifoldCFException {
    Messages.outputResourceWithVelocity(out,locale,resName,paramMap,true);
  }

  /** Fill in a Server tab configuration parameter map for calling a Velocity template.
  *@param newMap is the map to fill in
  *@param parameters is the current set of configuration parameters
  */
  private static void fillInServerConfigurationMap(Map<String,String> newMap, IPasswordMapperActivity mapper, ConfigParams parameters)
  {
    String username = parameters.getParameter(CmisConfig.USERNAME_PARAM);
    String password = parameters.getParameter(CmisConfig.PASSWORD_PARAM);
    String protocol = parameters.getParameter(CmisConfig.PROTOCOL_PARAM);
    String server = parameters.getParameter(CmisConfig.SERVER_PARAM);
    String port = parameters.getParameter(CmisConfig.PORT_PARAM);
    String path = parameters.getParameter(CmisConfig.PATH_PARAM);
    String repositoryId = parameters.getParameter(CmisConfig.REPOSITORY_ID_PARAM);
    String binding = parameters.getParameter(CmisConfig.BINDING_PARAM);
      
    if(username == null)
      username = StringUtils.EMPTY;
    if(password == null)
      password = StringUtils.EMPTY;
    else
      password = mapper.mapPasswordToKey(password);
    if(protocol == null)
      protocol = CmisConfig.PROTOCOL_DEFAULT_VALUE;
    if(server == null)
      server = CmisConfig.SERVER_DEFAULT_VALUE;
    if(port == null)
      port = CmisConfig.PORT_DEFAULT_VALUE;
    if(path == null)
      path = CmisConfig.PATH_DEFAULT_VALUE;
    if(repositoryId == null)
      repositoryId = StringUtils.EMPTY;
    if(binding == null)
      binding = CmisConfig.BINDING_ATOM_VALUE;
      
    newMap.put(CmisConfig.USERNAME_PARAM, username);
    newMap.put(CmisConfig.PASSWORD_PARAM, password);
    newMap.put(CmisConfig.PROTOCOL_PARAM, protocol);
    newMap.put(CmisConfig.SERVER_PARAM, server);
    newMap.put(CmisConfig.PORT_PARAM, port);
    newMap.put(CmisConfig.PATH_PARAM, path);
    newMap.put(CmisConfig.REPOSITORY_ID_PARAM, repositoryId);
    newMap.put(CmisConfig.BINDING_PARAM, binding);
  }
  
  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML that
   * is output from this configuration will be within appropriate <html> and
   * <body> tags.
   * 
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
      Locale locale, ConfigParams parameters) throws ManifoldCFException, IOException {
    Map<String,String> paramMap = new HashMap<String,String>();
  
    // Fill in map from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);

    outputResource(VIEW_CONFIG_FORWARD, out, locale, paramMap);
  }

  /**

   * Output the configuration header section. This method is called in the head
   * section of the connector's configuration page. Its purpose is to add the
   * required tabs to the list, and to output any javascript methods that might
   * be needed by the configuration editing HTML.
   * 
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @param tabsArray
   *          is an array of tab names. Add to this array any tab names that are
   *          specific to the connector.
   */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    // Add the Server tab
    tabsArray.add(Messages.getString(locale,CMIS_SERVER_TAB_PROPERTY));
    // Map the parameters
    Map<String,String> paramMap = new HashMap<String,String>();

    // Fill in the parameters from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);

    // Output the Javascript - only one Velocity template for all tabs
    outputResource(EDIT_CONFIG_HEADER_FORWARD, out, locale, paramMap);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters, String tabName)
      throws ManifoldCFException, IOException {

    // Call the Velocity templates for each tab

    // Server tab
    Map<String,String> paramMap = new HashMap<String,String>();
    // Set the tab name
    paramMap.put("TabName", tabName);
    // Fill in the parameters
    fillInServerConfigurationMap(paramMap, out, parameters);
    outputResource(EDIT_CONFIG_FORWARD_SERVER, out, locale, paramMap);
  
  }

  /**
   * Process a configuration post. This method is called at the start of the
   * connector's configuration page, whenever there is a possibility that form
   * data for a connection has been posted. Its purpose is to gather form
   * information and modify the configuration parameters accordingly. The name
   * of the posted form is "editconnection".
   * 
   * @param threadContext
   *          is the local thread context.
   * @param variableContext
   *          is the set of variables available from the post, including binary
   *          file post information.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @return null if all is well, or a string error message if there is an error
   *         that should prevent saving of the connection (and cause a
   *         redirection to an error page).
   */
  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, ConfigParams parameters)
      throws ManifoldCFException {
    
    String binding = variableContext.getParameter(CmisConfig.BINDING_PARAM);
    if (binding != null)
      parameters.setParameter(CmisConfig.BINDING_PARAM, binding);
    
    String username = variableContext.getParameter(CmisConfig.USERNAME_PARAM);
    if (username != null)
      parameters.setParameter(CmisConfig.USERNAME_PARAM, username);

    String password = variableContext.getParameter(CmisConfig.PASSWORD_PARAM);
    if (password != null)
      parameters.setParameter(CmisConfig.PASSWORD_PARAM, variableContext.mapKeyToPassword(password));

    String protocol = variableContext.getParameter(CmisConfig.PROTOCOL_PARAM);
    if (protocol != null) {
      parameters.setParameter(CmisConfig.PROTOCOL_PARAM, protocol);
    }
    
    String server = variableContext.getParameter(CmisConfig.SERVER_PARAM);
    if (server != null && !StringUtils.contains(server, '/')) {
      parameters.setParameter(CmisConfig.SERVER_PARAM, server);
    }
    
    String port = variableContext.getParameter(CmisConfig.PORT_PARAM);
    if (port != null){
      try {
        Integer.parseInt(port);
        parameters.setParameter(CmisConfig.PORT_PARAM, port);
      } catch (NumberFormatException e) {
        
      }
    }
    
    String path = variableContext.getParameter(CmisConfig.PATH_PARAM);
    if (path != null) {
      parameters.setParameter(CmisConfig.PATH_PARAM, path);
    }

    String repositoryId = variableContext.getParameter(CmisConfig.REPOSITORY_ID_PARAM);
    if (repositoryId != null) {
      parameters.setParameter(CmisConfig.REPOSITORY_ID_PARAM, repositoryId);
    }

    return null;
  }

  /** Fill in specification Velocity parameter map for CMISQuery tab.
  */
  private static void fillInCMISQuerySpecificationMap(Map<String,String> newMap, DocumentSpecification ds)
  {
    int i = 0;
    String cmisQuery = "";
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
        cmisQuery = sn.getAttributeValue(CmisConfig.CMIS_QUERY_PARAM);
      }
      i++;
    }
    newMap.put(CmisConfig.CMIS_QUERY_PARAM, cmisQuery);
  }

  /**
   * View specification. This method is called in the body section of a job's
   * view page. Its purpose is to present the document specification information
   * to the user. The coder can presume that the HTML that is output from this
   * configuration will be within appropriate <html> and <body> tags.
   * 
   * @param out
   *          is the output to which any HTML should be sent.
   * @param ds
   *          is the current document specification for this job.
   */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, DocumentSpecification ds)
      throws ManifoldCFException, IOException {
  
    Map<String,String> paramMap = new HashMap<String,String>();

    // Fill in the map with data from all tabs
    fillInCMISQuerySpecificationMap(paramMap, ds);
  
    outputResource(VIEW_SPEC_FORWARD, out, locale, paramMap);
  }

  /**
   * Process a specification post. This method is called at the start of job's
   * edit or view page, whenever there is a possibility that form data for a
   * connection has been posted. Its purpose is to gather form information and
   * modify the document specification accordingly. The name of the posted form
   * is "editjob".
   * 
   * @param variableContext
   *          contains the post data, including binary file-upload information.
   * @param ds
   *          is the current document specification for this job.
   * @return null if all is well, or a string error message if there is an error
   *         that should prevent saving of the job (and cause a redirection to
   *         an error page).
   */
  @Override
  public String processSpecificationPost(IPostParameters variableContext,
      DocumentSpecification ds) throws ManifoldCFException {
    String cmisQuery = variableContext.getParameter(CmisConfig.CMIS_QUERY_PARAM);
    if (cmisQuery != null) {
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode oldNode = ds.getChild(i);
        if (oldNode.getType().equals(JOB_STARTPOINT_NODE_TYPE)) {
          ds.removeChild(i);
          break;
        }
        i++;
      }
      SpecificationNode node = new SpecificationNode(JOB_STARTPOINT_NODE_TYPE);
      node.setAttribute(CmisConfig.CMIS_QUERY_PARAM, cmisQuery);
      variableContext.setParameter(CmisConfig.CMIS_QUERY_PARAM, cmisQuery);
      ds.addChild(ds.getChildCount(), node);
    }
    return null;
  }

  /**
   * Output the specification body section. This method is called in the body
   * section of a job page which has selected a repository connection of the
   * current type. Its purpose is to present the required form elements for
   * editing. The coder can presume that the HTML that is output from this
   * configuration will be within appropriate <html>, <body>, and <form> tags.
   * The name of the form is "editjob".
   * 
   * @param out
   *          is the output to which any HTML should be sent.
   * @param ds
   *          is the current document specification for this job.
   * @param tabName
   *          is the current tab name.
   */
  @Override
  public void outputSpecificationBody(IHTTPOutput out,
      Locale locale, DocumentSpecification ds, String tabName) throws ManifoldCFException,
      IOException {

    // Output CMISQuery tab
    Map<String,String> paramMap = new HashMap<String,String>();
    paramMap.put("TabName", tabName);
    fillInCMISQuerySpecificationMap(paramMap, ds);
    outputResource(EDIT_SPEC_FORWARD_CMISQUERY, out, locale, paramMap);
  }

  /**
   * Output the specification header section. This method is called in the head
   * section of a job page which has selected a repository connection of the
   * current type. Its purpose is to add the required tabs to the list, and to
   * output any javascript methods that might be needed by the job editing HTML.
   * 
   * @param out
   *          is the output to which any HTML should be sent.
   * @param ds
   *          is the current document specification for this job.
   * @param tabsArray
   *          is an array of tab names. Add to this array any tab names that are
   *          specific to the connector.
   */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out,
      Locale locale, DocumentSpecification ds, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale,CMIS_QUERY_TAB_PROPERTY));
  
    Map<String,String> paramMap = new HashMap<String,String>();
  
    // Fill in the specification header map, using data from all tabs.
    fillInCMISQuerySpecificationMap(paramMap, ds);

    outputResource(EDIT_SPEC_HEADER_FORWARD, out, locale, paramMap);
  }

  /** Process a set of documents.
   * This is the method that should cause each document to be fetched, processed, and the results either added
   * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
   * The document specification allows this class to filter what is done based on the job.
   *@param documentIdentifiers is the set of document identifiers to process.
   *@param versions is the corresponding document versions to process, as returned by getDocumentVersions() above.
   *       The implementation may choose to ignore this parameter and always process the current version.
   *@param activities is the interface this method should use to queue up new document references
   * and ingest documents.
   *@param spec is the document specification.
   *@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
   * should only find other references, and should not actually call the ingestion methods.
   *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
   */
  @SuppressWarnings("unchecked")
  @Override
  public void processDocuments(String[] documentIdentifiers, String[] versions,
      IProcessActivity activities, DocumentSpecification spec,
      boolean[] scanOnly) throws ManifoldCFException, ServiceInterruption {

    getSession();
    Logging.connectors.debug("CMIS: Inside processDocuments");
    int i = 0;

    while (i < documentIdentifiers.length) {
      long startTime = System.currentTimeMillis();
      String nodeId = documentIdentifiers[i];

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("CMIS: Processing document identifier '"
            + nodeId + "'");

      CmisObject cmisObject = session.getObject(nodeId);
      
      String errorCode = "OK";
      String errorDesc = StringUtils.EMPTY;
      String baseTypeId = cmisObject.getBaseType().getId();

      if (baseTypeId.equals(CMIS_FOLDER_BASE_TYPE)) {

        // adding all the children for a folder

        Folder folder = (Folder) cmisObject;
        ItemIterable<CmisObject> children = folder.getChildren();
        for (CmisObject child : children) {
          activities.addDocumentReference(child.getId(), nodeId,
              RELATIONSHIP_CHILD);
        }

      } else if(baseTypeId.equals(CMIS_DOCUMENT_BASE_TYPE)){

        // content ingestion

        Document document = (Document) cmisObject;
        long fileLength = document.getContentStreamLength();
        InputStream is = null;
        
        try {
          RepositoryDocument rd = new RepositoryDocument();
          Date createdDate = document.getCreationDate().getTime();
          Date modifiedDate = document.getLastModificationDate().getTime();
          
          rd.setFileName(document.getContentStreamFileName());
          rd.setMimeType(document.getContentStreamMimeType());
          rd.setCreatedDate(createdDate);
          rd.setModifiedDate(modifiedDate);
          
          //binary
          if(fileLength>0 && document.getContentStream()!=null){
            is = document.getContentStream().getStream();
            rd.setBinary(is, fileLength);
          }

          //properties
          List<Property<?>> properties = document.getProperties();
          String id = StringUtils.EMPTY;
          for (Property<?> property : properties) {
            String propertyId = property.getId();
            if (propertyId.endsWith(Constants.PARAM_OBJECT_ID))
              id = (String) property.getValue();

            if (property.getValue() !=null 
                || property.getValues() != null) {
              PropertyType propertyType = property.getType();

              switch (propertyType) {

              case STRING:
              case ID:
              case URI:
              case HTML:
                if(property.isMultiValued()){
                  List<String> htmlPropertyValues = (List<String>) property.getValues();
                  for (String htmlPropertyValue : htmlPropertyValues) {
                    rd.addField(propertyId, htmlPropertyValue);
                  }
                } else {
                  String stringValue = (String) property.getValue();
                  if(StringUtils.isNotEmpty(stringValue)){
                    rd.addField(propertyId, stringValue);
                  }
                }
                break;
     
              case BOOLEAN:
                if(property.isMultiValued()){
                  List<Boolean> booleanPropertyValues = (List<Boolean>) property.getValues();
                  for (Boolean booleanPropertyValue : booleanPropertyValues) {
                    rd.addField(propertyId, booleanPropertyValue.toString());
                  }
                } else {
                  Boolean booleanValue = (Boolean) property.getValue();
                  if(booleanValue!=null){
                    rd.addField(propertyId, booleanValue.toString());
                  }
                }
                break;

              case INTEGER:
                if(property.isMultiValued()){
                  List<BigInteger> integerPropertyValues = (List<BigInteger>) property.getValues();
                  for (BigInteger integerPropertyValue : integerPropertyValues) {
                    rd.addField(propertyId, integerPropertyValue.toString());
                  }
                } else {
                  BigInteger integerValue = (BigInteger) property.getValue();
                  if(integerValue!=null){
                    rd.addField(propertyId, integerValue.toString());
                  }
                }
                break;

              case DECIMAL:
                if(property.isMultiValued()){
                  List<BigDecimal> decimalPropertyValues = (List<BigDecimal>) property.getValues();
                  for (BigDecimal decimalPropertyValue : decimalPropertyValues) {
                    rd.addField(propertyId, decimalPropertyValue.toString());
                  }
                } else {
                  BigDecimal decimalValue = (BigDecimal) property.getValue();
                  if(decimalValue!=null){
                    rd.addField(propertyId, decimalValue.toString());
                  }
                }
                break;

              case DATETIME:
                if(property.isMultiValued()){
                  List<GregorianCalendar> datePropertyValues = (List<GregorianCalendar>) property.getValues();
                  for (GregorianCalendar datePropertyValue : datePropertyValues) {
                    rd.addField(propertyId,
                        ISO8601_DATE_FORMATTER.format(datePropertyValue.getTime()));
                  }
                } else {
                  GregorianCalendar dateValue = (GregorianCalendar) property.getValue();
                  if(dateValue!=null){
                    rd.addField(propertyId, ISO8601_DATE_FORMATTER.format(dateValue.getTime()));
                  }
                }
                break;

              default:
                break;
              }
            }
          }
          
          //ingestion
          
          //version label
          String version = document.getVersionLabel();
          if(StringUtils.isEmpty(version))
            version = StringUtils.EMPTY;
          
          //documentURI
          String documentURI = CmisRepositoryConnectorUtils.getDocumentURL(document, session);
          
          activities.ingestDocument(id, version, documentURI, rd);

        } finally {
          try {
            if(is!=null){
              is.close();
            }
          } catch (InterruptedIOException e) {
            errorCode = "Interrupted error";
            errorDesc = e.getMessage();
            throw new ManifoldCFException(e.getMessage(), e,
                ManifoldCFException.INTERRUPTED);
          } catch (IOException e) {
            errorCode = "IO ERROR";
            errorDesc = e.getMessage();
            Logging.connectors.warn(
                "CMIS: IOException closing file input stream: "
                    + e.getMessage(), e);
          }

          activities.recordActivity(new Long(startTime), ACTIVITY_READ,
              fileLength, nodeId, errorCode, errorDesc, null);
        }
      }
      i++;
    }
  }
  
  /** The short version of getDocumentVersions.
   * Get document versions given an array of document identifiers.
   * This method is called for EVERY document that is considered. It is
   * therefore important to perform as little work as possible here.
   *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
   *@param spec is the current document specification for the current job.  If there is a dependency on this
   * specification, then the version string should include the pertinent data, so that reingestion will occur
   * when the specification changes.  This is primarily useful for metadata.
   *@return the corresponding version strings, with null in the places where the document no longer exists.
   * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
   * will always be processed.
   */
  @Override
  public String[] getDocumentVersions(String[] documentIdentifiers,
      DocumentSpecification spec) throws ManifoldCFException,
      ServiceInterruption {
    
    getSession();
    
    String[] rval = new String[documentIdentifiers.length];
    int i = 0;
    while (i < rval.length){
      CmisObject cmisObject = session.getObject(documentIdentifiers[i]);
      if (cmisObject.getBaseType().getId().equals(CMIS_DOCUMENT_BASE_TYPE)) {
        Document document = (Document) cmisObject;
        
        //we have to check if this CMIS repository support versioning
        // or if the versioning is disabled for this content
        if(StringUtils.isNotEmpty(document.getVersionLabel())){
          rval[i] = document.getVersionLabel();
        } else {
        //a CMIS document that doesn't contain versioning information will always be processed
          rval[i] = StringUtils.EMPTY;
        }
      } else {
        //a CMIS folder will always be processed
        rval[i] = StringUtils.EMPTY;
      }
      i++;
    }
    return rval;
  }
}
