/* $Id: RegisterMapper.java 1498128 2013-06-30 14:15:49Z kwright $ */

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
package org.apache.manifoldcf.authorities;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.*;

public class RegisterMapper extends BaseMappersInitializationCommand
{
  public static final String _rcsid = "@(#)$Id: RegisterMapper.java 1498128 2013-06-30 14:15:49Z kwright $";

  private final String className;
  private final String description;

  public RegisterMapper(String className, String description)
  {
    this.className = className;
    this.description = description;
  }

  protected void doExecute(IMappingConnectorManager mgr) throws ManifoldCFException
  {
    mgr.registerConnector(description,className);
    Logging.root.info("Successfully registered connector '"+className+"'");
  }

  public static void main(String[] args)
  {
    if (args.length != 2)
    {
      System.err.println("Usage: RegisterMapper <classname> <description>");
      System.exit(1);
    }

    String className = args[0];
    String description = args[1];

    try
    {
      RegisterMapper registerMapper = new RegisterMapper(className,description);
      registerMapper.execute();
      System.err.println("Successfully registered connector '"+className+"'");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
