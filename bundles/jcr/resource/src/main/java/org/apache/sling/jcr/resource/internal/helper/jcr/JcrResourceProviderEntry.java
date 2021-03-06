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
package org.apache.sling.jcr.resource.internal.helper.jcr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceProvider;
import org.apache.sling.jcr.resource.internal.helper.ResourceProviderEntry;

public class JcrResourceProviderEntry extends ResourceProviderEntry {

    /**
     *
     */
    private static final long serialVersionUID = 5672648586247261128L;

    private final ResourceProviderEntry delegatee;

    private final Session session;

    public JcrResourceProviderEntry(Session session,
            ResourceProviderEntry delegatee,
            final ClassLoader dynamicClassLoader,
            boolean useMultiWorkspaces) {
        super("/", new ResourceProvider[] { new JcrResourceProvider(session,
                dynamicClassLoader, useMultiWorkspaces) });

        this.delegatee = delegatee;
        this.session = session;
    }

    public Session getSession() {
        return session;
    }

    @Override
    public boolean addResourceProvider(String prefix, ResourceProvider provider, Comparable<?> comparable) {
        return delegatee.addResourceProvider(prefix, provider, comparable);
    }

    @Override
    public boolean removeResourceProvider(String prefix,
            ResourceProvider provider, Comparable<?> comparable) {
        return delegatee.removeResourceProvider(prefix, provider, comparable);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.sling.jcr.resource.internal.helper.ResourceProviderEntry2#put(java.lang.String,
     *      org.apache.sling.jcr.resource.internal.helper.ResourceProviderEntry2)
     */

    /**
     * {@inheritDoc}
     *
     * @see org.apache.sling.jcr.resource.internal.helper.ResourceProviderEntry#get(java.lang.String)
     */
    @Override
    public ResourceProviderEntry get(String key) {
        ResourceProviderEntry rpe = super.get(key);
        if (rpe == null) {
            rpe = delegatee.get(key);
        }
        return rpe;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.sling.jcr.resource.internal.helper.ResourceProviderEntry#values()
     */
    @Override
    public Collection<ResourceProviderEntry> values() {
        List<ResourceProviderEntry> list = new ArrayList<ResourceProviderEntry>(
                super.values());
        list.addAll(delegatee.values());
        return list;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.sling.jcr.resource.internal.helper.ResourceProviderEntry#containsKey(java.lang.String)
     */
    @Override
    public boolean containsKey(String key) {
        return (super.containsKey(key) || delegatee.containsKey(key));
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.sling.jcr.resource.internal.helper.ResourceProviderEntry#getResourceProviders()
     */
    @Override
    public ResourceProvider[] getResourceProviders() {
        // merge the delegatee and this set of providers,
        // its probably better to do this every time, as

        ResourceProvider[] delegateeProviders = delegatee
                .getResourceProviders();
        ResourceProvider[] superProviders = super.getResourceProviders();
        if (delegateeProviders == null && superProviders == null) {
            return null;
        }
        if (delegateeProviders == null) {
            delegateeProviders = new ResourceProvider[0];
        }
        if (superProviders == null) {
            superProviders = new ResourceProvider[0];
        }
        ResourceProvider[] resourceProviders = new ResourceProvider[delegateeProviders.length
                + superProviders.length];
        System.arraycopy(delegateeProviders, 0, resourceProviders, 0,
                delegateeProviders.length);
        System.arraycopy(superProviders, 0, resourceProviders,
                delegateeProviders.length, superProviders.length);
        return resourceProviders;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.apache.sling.jcr.resource.internal.helper.ResourceProviderEntry#toString()
     */
    @Override
    public String toString() {
        return delegatee.toString();
    }

}
