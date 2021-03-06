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
package org.apache.sling.settings.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.settings.SlingSettingsService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

/**
 * This is the bundle activator.
 * It registers the SlingSettingsService.
 *
 */
public class Activator implements BundleActivator {

    /** The service registration */
    private ServiceRegistration serviceRegistration;

    /**
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public void start(final BundleContext bundleContext) throws Exception {
        final SlingSettingsService settingsService = new SlingSettingsServiceImpl(bundleContext);

        final Dictionary<String, String> props = new Hashtable<String, String>();
        props.put(Constants.SERVICE_PID, settingsService.getClass().getName());
        props.put(Constants.SERVICE_DESCRIPTION,
            "Apache Sling Settings Service");
        props.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");
        serviceRegistration = bundleContext.registerService(new String[] {
                                               SlingSettingsService.class.getName()},
                                               settingsService, props);
        SlingPropertiesPrinter.initPlugin(bundleContext);
        SlingSettingsPrinter.initPlugin(bundleContext, settingsService);
        try {
            RunModeCommand.initPlugin(bundleContext, settingsService.getRunModes());
        } catch (Throwable ignore) {
            // we just ignore this
        }

    }

    /**
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop(final BundleContext context) throws Exception {
        try {
            RunModeCommand.destroyPlugin();
        } catch (Throwable ignore) {
            // we just ignore this
        }
        SlingSettingsPrinter.destroyPlugin();
        SlingPropertiesPrinter.destroyPlugin();

        if ( serviceRegistration != null ) {
            serviceRegistration.unregister();
            serviceRegistration = null;
        }
    }
}
