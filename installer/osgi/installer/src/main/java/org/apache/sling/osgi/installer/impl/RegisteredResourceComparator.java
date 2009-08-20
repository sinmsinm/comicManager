/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.osgi.installer.impl;

import java.util.Comparator;

import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/** Comparator that defines priorities between RegisteredResources */
class RegisteredResourceComparator implements Comparator<RegisteredResource >{

    public int compare(RegisteredResource a, RegisteredResource b) {
        if(a.getResourceType() == RegisteredResource.ResourceType.BUNDLE) {
            return compareBundles(a, b);
        } else {
            return compareConfig(a, b);
        }
    }
    
    int compareBundles(RegisteredResource a, RegisteredResource b) {
        
        final String nameA = (String)a.getAttributes().get(Constants.BUNDLE_SYMBOLICNAME);
        final String nameB = (String)b.getAttributes().get(Constants.BUNDLE_SYMBOLICNAME);
        int result = nameA.compareTo(nameB);
        
        if(result == 0) {
            final Version va = (Version)a.getAttributes().get(Constants.BUNDLE_VERSION);
            final Version vb = (Version)b.getAttributes().get(Constants.BUNDLE_VERSION);
            // higher version has more priority, must come first so invert comparison
            result = vb.compareTo(va);
        }
        
        return result;
    }
    
    int compareConfig(RegisteredResource a, RegisteredResource b) {
        return 0;
    }
}
