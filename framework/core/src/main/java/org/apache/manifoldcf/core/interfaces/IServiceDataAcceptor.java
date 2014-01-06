/* $Id: IServiceDataAcceptor.java 1549105 2013-12-08 18:54:09Z kwright $ */

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
package org.apache.manifoldcf.core.interfaces;


/** The IServiceDataAcceptor interface describes functionality needed to
* tally service data values across all active services of a type.
*/
public interface IServiceDataAcceptor
{
  public static final String _rcsid = "@(#)$Id: IServiceDataAcceptor.java 1549105 2013-12-08 18:54:09Z kwright $";

  /** Accept service data.
  *@param serviceName is the name of the service that owns the data.
  *@param serviceData is the actual data that is owned.
  *@return true to abort the scan.
  */
  public boolean acceptServiceData(String serviceName, byte[] serviceData)
    throws ManifoldCFException;

}
