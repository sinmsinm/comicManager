/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.junit.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.junit.JUnitTestsManager;
import org.apache.sling.junit.TestsProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Service
public class JUnitTestsManagerImpl implements JUnitTestsManager {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private ServiceTracker tracker;
    private int lastTrackingCount = -1;
    private BundleContext bundleContext;
    
    // List of providers
    private List<TestsProvider> providers = new ArrayList<TestsProvider>();
    
    // Map of test names to their provider's PID
    private Map<String, String> tests = new HashMap<String, String>();
    
    // Last-modified values for each provider
    private Map<String, Long> lastModified = new HashMap<String, Long>();
    
    protected void activate(ComponentContext ctx) {
        bundleContext = ctx.getBundleContext();
        tracker = new ServiceTracker(bundleContext, TestsProvider.class.getName(), null);
        tracker.open();
    }

    protected void deactivate(ComponentContext ctx) {
        if(tracker != null) {
            tracker.close();
        }
        tracker = null;
        bundleContext = null;
    }
    
    /** inheritDoc */
    public Class<?> getTestClass(String testName) throws ClassNotFoundException {
        maybeUpdateProviders();

        // find TestsProvider that can instantiate testName
        final String providerPid = tests.get(testName);
        if(providerPid == null) {
            throw new IllegalStateException("Provider PID not found for test " + testName);
        }
        TestsProvider provider = null;
        for(TestsProvider p : providers) {
            if(p.getServicePid().equals(providerPid)) {
                provider = p;
                break;
            }
        }
        
        if(provider == null) {
            throw new IllegalStateException("No TestsProvider found for PID " + providerPid);
        }

        log.debug("Using provider {} to create test class {}", provider, testName);
        return provider.createTestClass(testName);
    }

    /** inheritDoc */
    public Collection<String> getTestNames() {
        maybeUpdateProviders();
        
        // If any provider has changes, reload the whole list
        // of test names (to keep things simple)
        boolean reload = false;
        for(TestsProvider p : providers) {
            final Long lastMod = lastModified.get(p.getServicePid());
            if(lastMod == null || lastMod.longValue() != p.lastModified()) {
                reload = true;
                log.debug("{} updated, will reload test names from all providers", p);
                break;
            }
        }
        
        if(reload) {
            tests.clear();
            for(TestsProvider p : providers) {
                final String pid = p.getServicePid();
                if(pid == null) {
                    log.warn("{} has null PID, ignored", p);
                    continue;
                }
                lastModified.put(pid, new Long(p.lastModified()));
                final List<String> names = p.getTestNames(); 
                for(String name : names) {
                    tests.put(name, pid);
                }
                log.debug("Added {} test names from provider {}", names.size(), p);
            }
            log.info("Test names reloaded, total {} names from {} providers", tests.size(), providers.size());
        }
        
        return tests.keySet();
    }
    
    /** Update our list of providers if tracker changed */
    private void maybeUpdateProviders() {
        if(tracker.getTrackingCount() != lastTrackingCount) {
            // List of providers changed, need to reload everything
            lastModified.clear();
            List<TestsProvider> newList = new ArrayList<TestsProvider>();
            for(ServiceReference ref : tracker.getServiceReferences()) {
                newList.add((TestsProvider)bundleContext.getService(ref));
            }
            synchronized (providers) {
                providers.clear();
                providers.addAll(newList);
            }
            log.info("Updated list of TestsProvider: {}", providers);
        }
        lastTrackingCount = tracker.getTrackingCount();
    }
}