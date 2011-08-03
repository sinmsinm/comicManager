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
package org.apache.sling.security.impl;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyUnbounded;
import org.apache.felix.scr.annotations.sling.SlingFilter;
import org.apache.felix.scr.annotations.sling.SlingFilterScope;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SlingFilter(order=-1500000000,scope=SlingFilterScope.REQUEST,metatype=true,
        description="%referrer.description",
        label="%referrer.name")
public class ReferrerFilter implements Filter {

    /** Logger. */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** Default value for allow empty. */
    private static final boolean DEFAULT_ALLOW_EMPTY = false;

    /** Allow empty property. */
    @Property(boolValue=DEFAULT_ALLOW_EMPTY)
    private static final String PROP_ALLOW_EMPTY = "allow.empty";

    /** Allow empty property. */
    @Property(unbounded=PropertyUnbounded.ARRAY)
    private static final String PROP_HOSTS = "allow.hosts";

    /** Allow empty property. */
    @Property(unbounded=PropertyUnbounded.ARRAY, value={"POST", "PUT", "DELETE"})
    private static final String PROP_METHODS = "filter.methods";

    /** Do we allow empty referrer? */
    private boolean allowEmpty;

    /** Allowed referrers */
    private URL[] allowedReferrers;

    /** Methods to be filtered. */
    private String[] filterMethods;

    /**
     * Create a default list of referrers
     */
    private Set<String> getDefaultAllowedReferrers() {
        final Set<String> referrers = new HashSet<String>();
        try {
            final Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();

            while(ifaces.hasMoreElements()){
                final NetworkInterface iface = ifaces.nextElement();
                logger.info("Adding Allowed referers for Interface:" + iface.getDisplayName());
                final Enumeration<InetAddress> ias = iface.getInetAddresses();
                while(ias.hasMoreElements()){
                    final InetAddress ia = ias.nextElement();
                    final String address = ia.getHostAddress().trim().toLowerCase();
                    final String name = ia.getHostName().trim().toLowerCase();
                    if ( ia instanceof Inet4Address ) {
                        referrers.add("http://" + address + ":0");
                        referrers.add("https://" + address + ":0");
                        referrers.add("http://" + name + ":0");
                        referrers.add("https://" + name + ":0");
                        if (name.indexOf('.')>-1){
                            int index = name.indexOf('.');
                            String host = name.substring(0, index);
                            referrers.add("http://" + host.trim().toLowerCase() + ":0");
                            referrers.add("https://" + host.trim().toLowerCase() + ":0");
                        }
                    }
                    if ( ia instanceof Inet6Address ) {
                        referrers.add("http://[" + address + "]" + ":0");
                        referrers.add("https://[" + address + "]" + ":0");
                        referrers.add("http://[" + name + "]" + ":0");
                        referrers.add("https://[" + name + "]" + ":0");
                    }
                }
            }
        } catch ( final SocketException se) {
            logger.error("Unable to detect network interfaces", se);
        }
        referrers.add("http://localhost" + ":0");
        referrers.add("http://127.0.0.1" + ":0");
        referrers.add("http://[::1]" + ":0");
        referrers.add("https://localhost" + ":0");
        referrers.add("https://127.0.0.1" + ":0");
        referrers.add("https://[::1]" + ":0");
        return referrers;
    }

    private void add(final List<URL> urls, final String ref) {
        try {
            final URL u  = new URL(ref);
            urls.add(u);
        } catch (final MalformedURLException mue) {
            logger.warn("Unable to create URL from " + ref + " : " + mue.getMessage());
        }
    }

    /**
     * Create URLs out of the referrer list
     */
    private URL[] createReferrerUrls(final Set<String> referrers) {
        final List<URL> urls = new ArrayList<URL>();

        for(final String ref : referrers) {
            final int pos = ref.indexOf("://");
            // valid url?
            if ( pos != -1 ) {
                this.add(urls, ref);
            } else {
                this.add(urls, "http://" + ref + ":0");
                this.add(urls, "https://" + ref + ":0");
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    /**
     * Activate
     */
    protected void activate(final ComponentContext ctx) {
        this.allowEmpty = OsgiUtil.toBoolean(ctx.getProperties().get(PROP_ALLOW_EMPTY), DEFAULT_ALLOW_EMPTY);
        String[] allowHosts = OsgiUtil.toStringArray(ctx.getProperties().get(PROP_HOSTS));
        if ( allowHosts != null ) {
            if ( allowHosts.length == 0 ) {
                allowHosts = null;
            } else if ( allowHosts.length == 1 && allowHosts[0].trim().length() == 0 ) {
                allowHosts = null;
            }
        }
        final Set<String> allowedReferrers = this.getDefaultAllowedReferrers();
        if ( allowHosts != null ) {
            for(final String host : allowHosts) {
                allowedReferrers.add(host);
            }
        }
        this.allowedReferrers = this.createReferrerUrls(allowedReferrers);
        this.filterMethods = OsgiUtil.toStringArray(ctx.getProperties().get(PROP_METHODS));
        if ( this.filterMethods != null && this.filterMethods.length == 1 && (this.filterMethods[0] == null || this.filterMethods[0].trim().length() == 0) ) {
            this.filterMethods = null;
        }
        if ( this.filterMethods != null ) {
            for(int i=0; i<filterMethods.length; i++) {
                filterMethods[i] = filterMethods[i].toUpperCase();
            }
        }
    }

    private boolean isModification(final HttpServletRequest req) {
        final String method = req.getMethod();
        if ( filterMethods != null ) {
            for(final String m : filterMethods) {
                if ( m.equals(method) ) {
                    return true;
                }
            }
        }
        return false;
    }

    public void doFilter(final ServletRequest req,
                         final ServletResponse res,
                         final FilterChain chain)
    throws IOException, ServletException {
        if ( req instanceof HttpServletRequest && res instanceof HttpServletResponse ) {
            final HttpServletRequest request = (HttpServletRequest)req;

            // is this a modification request
            if ( this.isModification(request) ) {
                if ( !this.isValidRequest(request) ) {
                    final HttpServletResponse response = (HttpServletResponse)res;
                    // we use 500
                    response.sendError(500);
                    return;
                }
            }
        }
        chain.doFilter(req, res);
    }

    final static class HostInfo {
        public String host;
        public String scheme;
        public int port;
    }

    HostInfo getHost(final String referrer) {
        final int startPos = referrer.indexOf("://") + 3;
        if ( startPos == 2 ) {
            // we consider this illegal
            return null;
        }
        final HostInfo info = new HostInfo();
        info.scheme = referrer.substring(0, startPos - 3);

        final int paramStart = referrer.indexOf('?');
        final String hostAndPath = (paramStart == -1 ? referrer : referrer.substring(0, paramStart));
        final int endPos = hostAndPath.indexOf('/', startPos);
        final String hostPart = (endPos == -1 ? hostAndPath.substring(startPos) : hostAndPath.substring(startPos, endPos));
        final int hostNameStart = hostPart.indexOf('@') + 1;
        final int hostNameEnd = hostPart.lastIndexOf(':');
        if (hostNameEnd < hostNameStart ) {
            info.host = hostPart.substring(hostNameStart);
            if ( info.scheme.equals("http") ) {
                info.port = 80;
            } else if ( info.scheme.equals("https") ) {
                info.port = 443;
            }
        } else {
            info.host = hostPart.substring(hostNameStart, hostNameEnd);
            info.port = Integer.valueOf(hostPart.substring(hostNameEnd + 1));
        }
        return info;
    }

    boolean isValidRequest(final HttpServletRequest request) {
        final String referrer = request.getHeader("referer");
        // check for missing/empty referrer
        if ( referrer == null || referrer.trim().length() == 0 ) {
            if ( !this.allowEmpty ) {
                this.logger.info("Rejected empty referrer header for {} request to {}", request.getMethod(), request.getRequestURI());
            }
            return this.allowEmpty;
        }
        // check for relative referrer - which is always allowed
        if ( referrer.indexOf(":/") == - 1 ) {
            return true;
        }

        final HostInfo info = getHost(referrer);
        if ( info == null ) {
            // if this is invalid we just return invalid
            this.logger.info("Rejected illegal referrer header for {} request to {} : {}",
                    new Object[] {request.getMethod(), request.getRequestURI(), referrer});
            return false;
        }

        boolean valid = false;
        for(final URL ref : this.allowedReferrers) {
            if ( info.host.equals(ref.getHost()) && info.scheme.equals(ref.getProtocol()) ) {
                if ( ref.getPort() == 0 || info.port == ref.getPort() ) {
                    valid = true;
                    break;
                }
            }
        }
        if ( !valid) {
            this.logger.info("Rejected referrer header for {} request to {} : {}",
                    new Object[] {request.getMethod(), request.getRequestURI(), referrer});
        }
        return valid;
    }

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig arg0) throws ServletException {
        // nothing to do
    }

    /**
     * @see javax.servlet.Filter#destroy()
     */
    public void destroy() {
        // nothing to do
    }
}