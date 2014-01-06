/* $Id: IDfSession.java 1340870 2012-05-20 23:41:50Z kwright $ */

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
package com.documentum.fc.client;

import com.documentum.fc.common.*;

/** Stub interface to allow the connector to build fully.
*/
public interface IDfSession
{
  public IDfPersistentObject getObject(IDfId id)
    throws DfException;
  public IDfType getType(String type)
    throws DfException;
  public IDfPersistentObject getObjectByPath(String folderPath)
    throws DfException;
  public IDfPersistentObject getObjectByQualification(String dql)
    throws DfException;
  public String getSessionId()
    throws DfException;
  public String getServerVersion()
    throws DfException;
  public String getDocbaseName()
    throws DfException;
  public boolean isConnected();
}
