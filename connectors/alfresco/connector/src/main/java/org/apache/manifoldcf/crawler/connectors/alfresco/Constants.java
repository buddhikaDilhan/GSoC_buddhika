/* $Id: Constants.java 1295462 2012-03-01 08:19:24Z piergiorgio $ */


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
package org.apache.manifoldcf.crawler.connectors.alfresco;


public class Constants extends org.alfresco.webservice.util.Constants {
  
  public static final String PROP_VERSION_LABEL = createQNameString(NAMESPACE_CONTENT_MODEL, "versionLabel");
  public static final String PROP_NODE_UUID = createQNameString(NAMESPACE_SYSTEM_MODEL, "node-uuid");
  public static final String PROP_STORE_PROTOCOL = createQNameString(NAMESPACE_SYSTEM_MODEL, "store-protocol");
  public static final String PROP_STORE_ID = createQNameString(NAMESPACE_SYSTEM_MODEL, "store-identifier");
  public static final String NODE_REFERENCE = createQNameString(NAMESPACE_CONTENT_MODEL, "noderef");

}
