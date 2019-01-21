package jenkins.install;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.lang.StringUtils.defaultIfBlank;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import jenkins.model.JenkinsLocationConfiguration;
import jenkins.security.seed.UserSeedProperty;
import jenkins.util.SystemProperties;
import jenkins.util.UrlHelper;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.BulkChange;
import hudson.Extension;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.PageDecorator;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.model.User;
import hudson.security.AccountCreationFailedException;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.SecurityRealm;
import hudson.security.csrf.CrumbIssuer;
import hudson.security.csrf.DefaultCrumbIssuer;
import hudson.util.HttpResponses;
import hudson.util.PluginServletFilter;
import hudson.util.VersionNumber;
import java.io.File;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import jenkins.CLI;

import jenkins.model.Jenkins;
import jenkins.security.s2m.AdminWhitelistRule;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * A Jenkins instance used during first-run to provide a limited set of services while
 * initial installation is in progress
 * 
 * @since 2.0
 */
@Restricted(NoExternalUse.class)
@Extension
public class SetupWizard extends PageDecorator {
    public SetupWizard() {
        checkFilter();
    }

    /**
     * The security token parameter name
     */
    public static String initialSetupAdminUserName = "admin";

    private static final Logger LOGGER = Logger.getLogger(SetupWizard.class.getName());
    
    /**
     * Initialize the setup wizard, this will process any current state initializations
     */
    /*package*/ void init(boolean newInstall) throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.get();
        
        if(newInstall) {
            // this was determined to be a new install, don't run the update wizard here
            setCurrentLevel(Jenkins.getVersion());
            
            // Create an admin user by default with a 
            // difficult password
            FilePath iapf = getInitialAdminPasswordFile();
            if(jenkins.getSecurityRealm() == null || jenkins.getSecurityRealm() == SecurityRealm.NO_AUTHENTICATION) { // this seems very fragile
                try (BulkChange bc = new BulkChange(jenkins)) {
                    HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
                    jenkins.setSecurityRealm(securityRealm);
                    String randomUUID = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ENGLISH);
    
                    // create an admin user
                    securityRealm.createAccount(SetupWizard.initialSetupAdminUserName, randomUUID);
    
                    // JENKINS-33599 - write to a file in the jenkins home directory
                    // most native packages of Jenkins creates a machine user account 'jenkins' to run Jenkins,
                    // and use group 'jenkins' for admins. So we allow groups to read this file
                    iapf.touch(System.currentTimeMillis());
                    iapf.chmod(0640);
                    iapf.write(randomUUID + System.lineSeparator(), "UTF-8");
                    
    
                    // Lock Jenkins down:
                    FullControlOnceLoggedInAuthorizationStrategy authStrategy = new FullControlOnceLoggedInAuthorizationStrategy();
                    authStrategy.setAllowAnonymousRead(false);
                    jenkins.setAuthorizationStrategy(authStrategy);
    
                    // Disable jnlp by default, but honor system properties
                    jenkins.setSlaveAgentPort(SystemProperties.getInteger(Jenkins.class.getName()+".slaveAgentPort",-1));

                    // Disable CLI over Remoting
                    CLI.get().setEnabled(false);
                    
                    // require a crumb issuer
                    jenkins.setCrumbIssuer(new DefaultCrumbIssuer(SystemProperties.getBoolean(Jenkins.class.getName() + ".crumbIssuerProxyCompatibility",false)));
    
                    // set master -> slave security:
                    jenkins.getInjector().getInstance(AdminWhitelistRule.class)
                        .setMasterKillSwitch(false);
                
                    jenkins.save(); // TODO could probably be removed since some of the above setters already call save
                    bc.commit();
                }
            }
    
            if(iapf.exists()) {
                String setupKey = iapf.readToString().trim();
                String ls = System.lineSeparator();
                LOGGER.info(ls + ls + "*************************************************************" + ls
                        + "*************************************************************" + ls
                        + "*************************************************************" + ls
                        + ls
                        + "Jenkins initial setup is required. An admin user has been created and "
                        + "a password generated." + ls
                        + "Please use the following password to proceed to installation:" + ls
                        + ls
                        + setupKey + ls
                        + ls
                        + "This may also be found at: " + iapf.getRemote() + ls
                        + ls
                        + "*************************************************************" + ls
                        + "*************************************************************" + ls
                        + "*************************************************************" + ls);
            }
        }

        try {
            // Make sure plugin metadata is up to date
            UpdateCenter.updateDefaultSite();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e.getMessage(), e);
        }
    }

    private void setUpFilter() {
        try {
            if (!PluginServletFilter.hasFilter(FORCE_SETUP_WIZARD_FILTER)) {
                PluginServletFilter.addFilter(FORCE_SETUP_WIZARD_FILTER);
            }
        } catch (ServletException e) {
            throw new RuntimeException("Unable to add PluginServletFilter for the SetupWizard", e);
        }
    }

    private void tearDownFilter() {
        try {
            if (PluginServletFilter.hasFilter(FORCE_SETUP_WIZARD_FILTER)) {
                PluginServletFilter.removeFilter(FORCE_SETUP_WIZARD_FILTER);
            }
        } catch (ServletException e) {
            throw new RuntimeException("Unable to remove PluginServletFilter for the SetupWizard", e);
        }
    }
 
    /**
     * Indicates a generated password should be used - e.g. this is a new install, no security realm set up
     */
    @SuppressWarnings("unused") // used by jelly
    public boolean isUsingSecurityToken() {
        try {
            return !Jenkins.get().getInstallState().isSetupComplete()
                    && isUsingSecurityDefaults();
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    /**
     * Determines if the security settings seem to match the defaults. Here, we only
     * really care about and test for HudsonPrivateSecurityRealm and the user setup.
     * Other settings are irrelevant.
     */
    /*package*/ boolean isUsingSecurityDefaults() {
        Jenkins j = Jenkins.get();
        if (j.getSecurityRealm() instanceof HudsonPrivateSecurityRealm) {
            HudsonPrivateSecurityRealm securityRealm = (HudsonPrivateSecurityRealm)j.getSecurityRealm();
            try {
                if(securityRealm.getAllUsers().size() == 1) {
                    HudsonPrivateSecurityRealm.Details details = securityRealm.loadUserByUsername(SetupWizard.initialSetupAdminUserName);
                    FilePath iapf = getInitialAdminPasswordFile();
                    if (iapf.exists()) {
                        if (details.isPasswordCorrect(iapf.readToString().trim())) {
                            return true;
                        }
                    }
                }
            } catch(UsernameNotFoundException | IOException | InterruptedException e) {
                return false; // Not initial security setup if no transitional admin user / password found
            }
        }
        return false;
    }

    /**
     * Called during the initial setup to create an admin user
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public HttpResponse doCreateAdminUser(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins j = Jenkins.getInstance();

        j.checkPermission(Jenkins.ADMINISTER);

        // This will be set up by default. if not, something changed, ok to fail
        HudsonPrivateSecurityRealm securityRealm = (HudsonPrivateSecurityRealm) j.getSecurityRealm();

        User admin = securityRealm.getUser(SetupWizard.initialSetupAdminUserName);
        try {
            if (admin != null) {
                admin.delete(); // assume the new user may well be 'admin'
            }

            User newUser = securityRealm.createAccountFromSetupWizard(req);
            if (admin != null) {
                admin = null;
            }

            // Success! Delete the temporary password file:
            try {
                getInitialAdminPasswordFile().delete();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }

            InstallUtil.proceedToNextStateFrom(InstallState.CREATE_ADMIN_USER);

            // ... and then login
            Authentication auth = new UsernamePasswordAuthenticationToken(newUser.getId(), req.getParameter("password1"));
            auth = securityRealm.getSecurityComponents().manager.authenticate(auth);
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            HttpSession session = req.getSession(false);
            if (session != null) {
                // avoid session fixation
                session.invalidate();
            }
            HttpSession newSession = req.getSession(true);

            UserSeedProperty userSeed = newUser.getProperty(UserSeedProperty.class);
            String sessionSeed = userSeed.getSeed();
            // include the new seed
            newSession.setAttribute(UserSeedProperty.USER_SESSION_SEED, sessionSeed);
            
            CrumbIssuer crumbIssuer = Jenkins.getInstance().getCrumbIssuer();
            JSONObject data = new JSONObject();
            if (crumbIssuer != null) {
                data.accumulate("crumbRequestField", crumbIssuer.getCrumbRequestField()).accumulate("crumb", crumbIssuer.getCrumb(req));
            }
            return HttpResponses.okJSON(data);
        } catch (AccountCreationFailedException e) {
            /*
            Return Unprocessable Entity from WebDAV. While this is not technically in the HTTP/1.1 standard, browsers
            seem to accept this. 400 Bad Request is technically inappropriate because that implies invalid *syntax*,
            not incorrect data. The client only cares about it being >200 anyways.
             */
            rsp.setStatus(422);
            return HttpResponses.forwardToView(securityRealm, "/jenkins/install/SetupWizard/setupWizardFirstUser.jelly");
        } finally {
            if (admin != null) {
                admin.save(); // recreate this initial user if something failed
            }
        }
    }    
    
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public HttpResponse doConfigureInstance(StaplerRequest req, @QueryParameter String rootUrl) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        
        Map<String, String> errors = new HashMap<>();
        // pre-check data
        checkRootUrl(errors, rootUrl);
        
        if(!errors.isEmpty()){
            return HttpResponses.errorJSON(Messages.SetupWizard_ConfigureInstance_ValidationErrors(), errors);
        }
        
        // use the parameters to configure the instance
        useRootUrl(errors, rootUrl);
        
        if(!errors.isEmpty()){
            return HttpResponses.errorJSON(Messages.SetupWizard_ConfigureInstance_ValidationErrors(), errors);
        }
        
        InstallUtil.proceedToNextStateFrom(InstallState.CONFIGURE_INSTANCE);

        CrumbIssuer crumbIssuer = Jenkins.get().getCrumbIssuer();
        JSONObject data = new JSONObject();
        if (crumbIssuer != null) {
            data.accumulate("crumbRequestField", crumbIssuer.getCrumbRequestField()).accumulate("crumb", crumbIssuer.getCrumb(req));
        }
        return HttpResponses.okJSON(data);
    }
    
    private void checkRootUrl(Map<String, String> errors, @CheckForNull String rootUrl){
        if(rootUrl == null){
            errors.put("rootUrl", Messages.SetupWizard_ConfigureInstance_RootUrl_Empty());
            return;
        }
        if(!UrlHelper.isValidRootUrl(rootUrl)){
            errors.put("rootUrl", Messages.SetupWizard_ConfigureInstance_RootUrl_Invalid());
        }
    }
    
    private void useRootUrl(Map<String, String> errors, @CheckForNull String rootUrl){
        LOGGER.log(Level.FINE, "Root URL set during SetupWizard to {0}", new Object[]{ rootUrl });
        JenkinsLocationConfiguration.getOrDie().setUrl(rootUrl);
    }

    /*package*/ void setCurrentLevel(VersionNumber v) throws IOException {
        FileUtils.writeStringToFile(getUpdateStateFile(), v.toString());
    }
    
    /**
     * File that captures the state of upgrade.
     *
     * This file records the version number that the installation has upgraded to.
     */
    /*package*/ static File getUpdateStateFile() {
        return new File(Jenkins.get().getRootDir(),"jenkins.install.UpgradeWizard.state");
    }
    
    /**
     * What is the version the upgrade wizard has run the last time and upgraded to?.
     * If {@link #getUpdateStateFile()} is missing, presumes the baseline is 1.0
     * @return Current baseline. {@code null} if it cannot be retrieved.
     */
    @Restricted(NoExternalUse.class)
    @CheckForNull
    public VersionNumber getCurrentLevel() {
        VersionNumber from = new VersionNumber("1.0");
        File state = getUpdateStateFile();
        if (state.exists()) {
            try {
                from = new VersionNumber(defaultIfBlank(readFileToString(state), "1.0").trim());
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Cannot read the current version file", ex);
                return null;
            }
        }
        return from;
    }
    
    /**
     * Returns the initial plugin list in JSON format
     */
    @Restricted(DoNotUse.class) // WebOnly
    public HttpResponse doPlatformPluginList() throws IOException {
        SetupWizard setupWizard = Jenkins.get().getSetupWizard();
        if (setupWizard != null) {
            if (InstallState.UPGRADE.equals(Jenkins.get().getInstallState())) {
                JSONArray initialPluginData = getPlatformPluginUpdates();
                if(initialPluginData != null) {
                    return HttpResponses.okJSON(initialPluginData);
                }
            } else {
                JSONArray initialPluginData = getPlatformPluginList();
                if(initialPluginData != null) {
                    return HttpResponses.okJSON(initialPluginData);
                }
            }
        }
        return HttpResponses.okJSON();
    }
    
    /**
     * Returns whether the system needs a restart, and if it is supported
     * e.g. { restartRequired: true, restartSupported: false }
     */
    @Restricted(DoNotUse.class) // WebOnly
    public HttpResponse doRestartStatus() throws IOException {
        JSONObject response = new JSONObject();
        Jenkins jenkins = Jenkins.get();
        response.put("restartRequired", jenkins.getUpdateCenter().isRestartRequiredForCompletion());
        response.put("restartSupported", jenkins.getLifecycle().canRestart());
        return HttpResponses.okJSON(response);
    }

    /**
     * Provides the list of platform plugin updates from the last time
     * the upgrade was run.
     * @return {@code null} if the version range cannot be retrieved.
     */
    @CheckForNull
    public JSONArray getPlatformPluginUpdates() {
        final VersionNumber version = getCurrentLevel();
        if (version == null) {
            return null;
        }
        return getPlatformPluginsForUpdate(version, Jenkins.getVersion());
    }
    
    /**
     * Gets the suggested plugin list from the update sites, falling back to a local version
     * @return JSON array with the categorized plugin list
     */
    @CheckForNull
    /*package*/ JSONArray getPlatformPluginList() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        JSONArray initialPluginList = null;
        updateSiteList: for (UpdateSite updateSite : Jenkins.get().getUpdateCenter().getSiteList()) {
            String updateCenterJsonUrl = updateSite.getUrl();
            String suggestedPluginUrl = updateCenterJsonUrl.replace("/update-center.json", "/platform-plugins.json");
            try {
                URLConnection connection = ProxyConfiguration.open(new URL(suggestedPluginUrl));
                
                try {
                    if(connection instanceof HttpURLConnection) {
                        int responseCode = ((HttpURLConnection)connection).getResponseCode();
                        if(HttpURLConnection.HTTP_OK != responseCode) {
                            throw new HttpRetryException("Invalid response code (" + responseCode + ") from URL: " + suggestedPluginUrl, responseCode);
                        }
                    }
                    
                    String initialPluginJson = IOUtils.toString(connection.getInputStream(), "utf-8");
                    initialPluginList = JSONArray.fromObject(initialPluginJson);
                    break updateSiteList;
                } catch(Exception e) {
                    // not found or otherwise unavailable
                    LOGGER.log(Level.FINE, e.getMessage(), e);
                    continue updateSiteList;
                }
            } catch(Exception e) {
                LOGGER.log(Level.FINE, e.getMessage(), e);
            }
        }
        if (initialPluginList == null) {
            // fall back to local file
            try {
                ClassLoader cl = getClass().getClassLoader();
                URL localPluginData = cl.getResource("jenkins/install/platform-plugins.json");
                String initialPluginJson = IOUtils.toString(localPluginData.openStream(), "utf-8");
                initialPluginList =  JSONArray.fromObject(initialPluginJson);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            }
        }
        return initialPluginList;
    }

    /**
     * Get the platform plugins added in the version range
     */
    /*package*/ JSONArray getPlatformPluginsForUpdate(VersionNumber from, VersionNumber to) {
        Jenkins jenkins = Jenkins.get();
        JSONArray pluginCategories = JSONArray.fromObject(getPlatformPluginList().toString());
        for (Iterator<?> categoryIterator = pluginCategories.iterator(); categoryIterator.hasNext();) {
            Object category = categoryIterator.next();
            if (category instanceof JSONObject) {
                JSONObject cat = (JSONObject)category;
                JSONArray plugins = cat.getJSONArray("plugins");
                
                nextPlugin: for (Iterator<?> pluginIterator = plugins.iterator(); pluginIterator.hasNext();) {
                    Object pluginData = pluginIterator.next();
                    if (pluginData instanceof JSONObject) {
                        JSONObject plugin = (JSONObject)pluginData;
                        if (plugin.has("added")) {
                            String sinceVersion = plugin.getString("added");
                            if (sinceVersion != null) {
                                VersionNumber v = new VersionNumber(sinceVersion);
                                if(v.compareTo(to) <= 0 && v.compareTo(from) > 0) {
                                    // This plugin is valid, we'll leave "suggested" state
                                    // to match the experience during install
                                    // but only add it if it's currently uninstalled
                                    String pluginName = plugin.getString("name");
                                    if (null == jenkins.getPluginManager().getPlugin(pluginName)) {
                                        // Also check that a compatible version exists in an update site
                                        boolean foundCompatibleVersion = false;
                                        for (UpdateSite site : jenkins.getUpdateCenter().getSiteList()) {
                                            UpdateSite.Plugin sitePlug = site.getPlugin(pluginName);
                                            if (sitePlug != null
                                                    && !sitePlug.isForNewerHudson() && !sitePlug.isForNewerJava()
                                                    && !sitePlug.isNeededDependenciesForNewerJenkins()) {
                                                foundCompatibleVersion = true;
                                                break;
                                            }
                                        }
                                        if (foundCompatibleVersion) {
                                            continue nextPlugin;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    pluginIterator.remove();
                }
                
                if (plugins.isEmpty()) {
                    categoryIterator.remove();
                }
            }
        }
        return pluginCategories;
    }

    /**
     * Gets the file used to store the initial admin password
     */
    public FilePath getInitialAdminPasswordFile() {
        return Jenkins.get().getRootPath().child("secrets/initialAdminPassword");
    }

    /**
     * Remove the setupWizard filter, ensure all updates are written to disk, etc
     */
    @RequirePOST
    public HttpResponse doCompleteInstall() throws IOException, ServletException {
        completeSetup();
        return HttpResponses.okJSON();
    }
    
    /*package*/ void completeSetup() throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        InstallUtil.saveLastExecVersion();
        setCurrentLevel(Jenkins.getVersion());
        InstallUtil.proceedToNextStateFrom(InstallState.INITIAL_SETUP_COMPLETED);
    }
    
    /**
     * Gets all the install states
     */
    public List<InstallState> getInstallStates() {
        return InstallState.all();
    }
    
    /**
     * Returns an installState by name
     */
    public InstallState getInstallState(String name) {
        if (name == null) {
            return null;
        }
        return InstallState.valueOf(name);
    }

    /**
     * Called upon install state update.
     * @param state the new install state.
     * @since 2.94
     */
    public void onInstallStateUpdate(InstallState state) {
        if (state.isSetupComplete()) {
            tearDownFilter();
        } else {
            setUpFilter();
        }
    }

    /**
     * Returns whether the setup wizard filter is currently registered.
     * @since 2.94
     */
    public boolean hasSetupWizardFilter() {
        return PluginServletFilter.hasFilter(FORCE_SETUP_WIZARD_FILTER);
    }

    /**
     * This filter will validate that the security token is provided
     */
    private final Filter FORCE_SETUP_WIZARD_FILTER = new Filter() {
        @Override
        public void init(FilterConfig cfg) throws ServletException {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            // Force root requests to the setup wizard
            if (request instanceof HttpServletRequest) {
                HttpServletRequest req = (HttpServletRequest) request;
                String requestURI = req.getRequestURI();
                if (requestURI.equals(req.getContextPath()) && !requestURI.endsWith("/")) {
                    ((HttpServletResponse) response).sendRedirect(req.getContextPath() + "/");
                    return;
                } else if (req.getRequestURI().equals(req.getContextPath() + "/")) {
                    Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                    chain.doFilter(new HttpServletRequestWrapper(req) {
                        public String getRequestURI() {
                            return getContextPath() + "/setupWizard/";
                        }
                    }, response);
                    return;
                }
                // fall through to handling the request normally
            }
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    };

    /**
     * Sets up the Setup Wizard filter if the current state requires it.
     */
    private void checkFilter() {
        if (!Jenkins.get().getInstallState().isSetupComplete()) {
            setUpFilter();
        }
    }
}
