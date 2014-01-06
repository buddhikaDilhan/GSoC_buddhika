/* $Id: DfClientX.java 1349200 2012-06-12 09:02:58Z kwright $ */

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
package com.documentum.com;

import com.documentum.fc.client.IDfClient;
import com.documentum.fc.common.DfObject;
import com.documentum.fc.common.IDfLoginInfo;
import com.documentum.fc.common.DfException;

/** Stub interface to allow the connector to build fully.
*/
public class DfClientX extends DfObject implements IDfClientX
{
  public DfClientX()
  {
  }
  
  public IDfLoginInfo getLoginInfo()
  {
    return null;
  }
  
  public IDfClient getLocalClient()
    throws DfException
  {
    return null;
  }

}