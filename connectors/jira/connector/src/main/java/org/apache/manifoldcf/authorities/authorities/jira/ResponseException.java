/* $Id: ResponseException.java 1501683 2013-07-10 09:11:04Z kwright $ */

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

package org.apache.manifoldcf.authorities.authorities.jira;

/** This exception is thrown when the response from REST is not what
* was expected.
 */
public class ResponseException extends Exception {

  public ResponseException(String msg) {
    super(msg);
  }
  
  public ResponseException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
