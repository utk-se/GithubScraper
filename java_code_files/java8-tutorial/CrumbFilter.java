/**
 * Copyright (c) 2008-2009 Yahoo! Inc. 
 * All rights reserved. 
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.security.csrf;

import hudson.util.MultipartFormDataParser;
import jenkins.model.Jenkins;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.kohsuke.MetaInfServices;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Checks for and validates crumbs on requests that cause state changes, to
 * protect against cross site request forgeries.
 * 
 * @author dty
 */
public class CrumbFilter implements Filter {
    /**
     * Because servlet containers generally don't specify the ordering of the initialization
     * (and different implementations indeed do this differently --- See HUDSON-3878),
     * we cannot use Hudson to the CrumbIssuer into CrumbFilter eagerly.
     */
    public CrumbIssuer getCrumbIssuer() {
        Jenkins h = Jenkins.getInstanceOrNull();
        if(h==null)     return null;    // before Jenkins is initialized?
        return h.getCrumbIssuer();
    }

    @Restricted(NoExternalUse.class)
    @MetaInfServices
    public static class ErrorCustomizer implements RequirePOST.ErrorCustomizer {
        @Override
        public ForwardToView getForwardView() {
            return new ForwardToView(CrumbFilter.class, "retry");
        }
    }

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        CrumbIssuer crumbIssuer = getCrumbIssuer();
        if (crumbIssuer == null || !(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("POST".equals(httpRequest.getMethod())) {
            for (CrumbExclusion e : CrumbExclusion.all()) {
                if (e.process(httpRequest,httpResponse,chain))
                    return;
            }

            String crumbFieldName = crumbIssuer.getDescriptor().getCrumbRequestField();
            String crumbSalt = crumbIssuer.getDescriptor().getCrumbSalt();

            boolean valid = false;
            String crumb = extractCrumbFromRequest(httpRequest, crumbFieldName);
            if (crumb == null) {
                // compatibility for clients that hard-code the default crumb name up to Jenkins 1.TODO
                extractCrumbFromRequest(httpRequest, ".crumb");
            }

            // JENKINS-40344: Don't spam the log just because a session is expired
            Level level = Jenkins.getAuthentication() instanceof AnonymousAuthenticationToken ? Level.FINE : Level.WARNING;

            if (crumb != null) {
                if (crumbIssuer.validateCrumb(httpRequest, crumbSalt, crumb)) {
                    valid = true;
                } else {
                    LOGGER.log(level, "Found invalid crumb {0}.  Will check remaining parameters for a valid one...", crumb);
                }
            }

            if (valid) {
                chain.doFilter(request, response);
            } else {
                LOGGER.log(level, "No valid crumb was included in request for {0} by {1}. Returning {2}.", new Object[] {httpRequest.getRequestURI(), Jenkins.getAuthentication().getName(), HttpServletResponse.SC_FORBIDDEN});
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,"No valid crumb was included in the request");
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private String extractCrumbFromRequest(HttpServletRequest httpRequest, String crumbFieldName) {
        String crumb = httpRequest.getHeader(crumbFieldName);
        if (crumb == null) {
            Enumeration<?> paramNames = httpRequest.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = (String) paramNames.nextElement();
                if (crumbFieldName.equals(paramName)) {
                    crumb = httpRequest.getParameter(paramName);
                    break;
                }
            }
        }
        return crumb;
    }

    protected static boolean isMultipart(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        return MultipartFormDataParser.isMultiPartForm(request.getContentType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
    }

    private static final Logger LOGGER = Logger.getLogger(CrumbFilter.class.getName());
}
