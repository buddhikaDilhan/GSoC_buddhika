/* $Id: EngineRuntimeException.java 1346314 2012-06-05 09:39:56Z kwright $ */

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
package com.filenet.api.exception;

/** Stub interface to allow the connector to build fully.
*/
public class EngineRuntimeException extends RuntimeException implements java.io.Externalizable
{
  public ExceptionCode getExceptionCode()
  {
    return null;
  }
  
  public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException
  {
  }
  
  public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException, ClassNotFoundException
  {
  }
  
}