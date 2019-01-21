/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Seiji Sogabe,
 *                          Andrew Bayer
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.model;

import hudson.ClassicPluginStrategy;
import hudson.ExtensionList;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.lifecycle.Lifecycle;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.HttpResponses;
import static jenkins.util.MemoryReductionUtil.*;
import hudson.util.TextFile;
import static java.util.concurrent.TimeUnit.*;
import hudson.util.VersionNumber;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jenkins.model.Jenkins;
import jenkins.model.DownloadSettings;
import jenkins.security.UpdateSiteWarningsConfiguration;
import jenkins.util.JSONSignatureValidator;
import jenkins.util.SystemProperties;
import jenkins.util.java.JavaUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Source of the update center information, like "http://jenkins-ci.org/update-center.json"
 *
 * <p>
 * Jenkins can have multiple {@link UpdateSite}s registered in the system, so that it can pick up plugins
 * from different locations.
 *
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 * @since 1.333
 */
@ExportedBean
public class UpdateSite {
    /**
     * What's the time stamp of data file?
     * 0 means never.
     */
    private transient volatile long dataTimestamp;

    /**
     * When was the last time we asked a browser to check the data for us?
     * 0 means never.
     *
     * <p>
     * There's normally some delay between when we send HTML that includes the check code,
     * until we get the data back, so this variable is used to avoid asking too many browsers
     * all at once.
     */
    private transient volatile long lastAttempt;

    /**
     * If the attempt to fetch data fails, we progressively use longer time out before retrying,
     * to avoid overloading the server.
     */
    private transient volatile long retryWindow;

    /**
     * Latest data as read from the data file.
     */
    private transient Data data;

    /**
     * ID string for this update source.
     */
    private final String id;

    /**
     * Path to {@code update-center.json}, like {@code http://jenkins-ci.org/update-center.json}.
     */
    private final String url;

    /**
     * the prefix for the signature validator name
     */
    private static final String signatureValidatorPrefix = "update site";


    public UpdateSite(String id, String url) {
        this.id = id;
        this.url = url;
    }

    /**
     * Get ID string.
     */
    @Exported
    public String getId() {
        return id;
    }

    @Exported
    public long getDataTimestamp() {
        assert dataTimestamp >= 0;
        return dataTimestamp;
    }

    /**
     * Update the data file from the given URL if the file
     * does not exist, or is otherwise due for update.
     * Accepted formats are JSONP or HTML with {@code postMessage}, not raw JSON.
     * @param signatureCheck whether to enforce the signature (may be off only for testing!)
     * @return null if no updates are necessary, or the future result
     * @since 1.502
     */
    public @CheckForNull Future<FormValidation> updateDirectly(final boolean signatureCheck) {
        if (! getDataFile().exists() || isDue()) {
            return Jenkins.getInstance().getUpdateCenter().updateService.submit(new Callable<FormValidation>() {
                @Override public FormValidation call() throws Exception {
                    return updateDirectlyNow(signatureCheck);
                }
            });
        } else {
            return null;
        }
    }

    @Restricted(NoExternalUse.class)
    public @Nonnull FormValidation updateDirectlyNow(boolean signatureCheck) throws IOException {
        return updateData(DownloadService.loadJSON(new URL(getUrl() + "?id=" + URLEncoder.encode(getId(), "UTF-8") + "&version=" + URLEncoder.encode(Jenkins.VERSION, "UTF-8"))), signatureCheck);
    }
    
    /**
     * This is the endpoint that receives the update center data file from the browser.
     */
    @RequirePOST
    public FormValidation doPostBack(StaplerRequest req) throws IOException, GeneralSecurityException {
        DownloadSettings.checkPostBackAccess();
        return updateData(IOUtils.toString(req.getInputStream(),"UTF-8"), true);
    }

    private FormValidation updateData(String json, boolean signatureCheck)
            throws IOException {

        dataTimestamp = System.currentTimeMillis();

        JSONObject o = JSONObject.fromObject(json);

        try {
            int v = o.getInt("updateCenterVersion");
            if (v != 1) {
                throw new IllegalArgumentException("Unrecognized update center version: " + v);
            }
        } catch (JSONException x) {
            throw new IllegalArgumentException("Could not find (numeric) updateCenterVersion in " + json, x);
        }

        if (signatureCheck) {
            FormValidation e = verifySignature(o);
            if (e.kind!=Kind.OK) {
                LOGGER.severe(e.toString());
                return e;
            }
        }

        LOGGER.info("Obtained the latest update center data file for UpdateSource " + id);
        retryWindow = 0;
        getDataFile().write(json);
        data = new Data(o);
        return FormValidation.ok();
    }

    public FormValidation doVerifySignature() throws IOException {
        return verifySignature(getJSONObject());
    }

    /**
     * Extension point to allow implementations of {@link UpdateSite} to create a custom
     * {@link UpdateCenter.InstallationJob}.
     *
     * @param plugin      the plugin to create the {@link UpdateCenter.InstallationJob} for.
     * @param uc          the {@link UpdateCenter}.
     * @param dynamicLoad {@code true} if the plugin should be attempted to be dynamically loaded.
     * @return the {@link UpdateCenter.InstallationJob}.
     * @since 2.9
     */
    protected UpdateCenter.InstallationJob createInstallationJob(Plugin plugin, UpdateCenter uc, boolean dynamicLoad) {
        return uc.new InstallationJob(plugin, this, Jenkins.getAuthentication(), dynamicLoad);
    }

    /**
     * Verifies the signature in the update center data file.
     */
    private FormValidation verifySignature(JSONObject o) throws IOException {
        return getJsonSignatureValidator().verifySignature(o);
    }

    /**
     * Let sub-classes of UpdateSite provide their own signature validator.
     * @return the signature validator.
     * @deprecated use {@link #getJsonSignatureValidator(@CheckForNull String)} instead.
     */
    @Deprecated
    @Nonnull
    protected JSONSignatureValidator getJsonSignatureValidator() {
        return getJsonSignatureValidator(null);
    }

    /**
     * Let sub-classes of UpdateSite provide their own signature validator.
     * @param name, the name for the JSON signature Validator object.
     *              if name is null, then the default name will be used,
     *              which is "update site" followed by the update site id
     * @return the signature validator.
     * @since 2.21
     */
    @Nonnull
    protected JSONSignatureValidator getJsonSignatureValidator(@CheckForNull String name) {
        if (name == null) {
            name = signatureValidatorPrefix + " '" + id + "'";
        }
        return new JSONSignatureValidator(name);
    }

    /**
     * Returns true if it's time for us to check for new version.
     */
    public boolean isDue() {
        if(neverUpdate)     return false;
        if(dataTimestamp == 0)
            dataTimestamp = getDataFile().file.lastModified();
        long now = System.currentTimeMillis();
        
        retryWindow = Math.max(retryWindow,SECONDS.toMillis(15));
        
        boolean due = now - dataTimestamp > DAY && now - lastAttempt > retryWindow;
        if(due) {
            lastAttempt = now;
            retryWindow = Math.min(retryWindow*2, HOURS.toMillis(1)); // exponential back off but at most 1 hour
        }
        return due;
    }

    /**
     * Invalidates the cached data and force retrieval.
     *
     * @since 1.432
     */
    @RequirePOST
    public HttpResponse doInvalidateData() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        dataTimestamp = 0;
        data = null;
        return HttpResponses.ok();
    }

    /**
     * Loads the update center data, if any.
     *
     * @return  null if no data is available.
     */
    public Data getData() {
        if (data == null) {
            JSONObject o = getJSONObject();
            if (o != null) {
                data = new Data(o);
            }
        }
        return data;
    }

    /**
     * Gets the raw update center JSON data.
     */
    public JSONObject getJSONObject() {
        TextFile df = getDataFile();
        if(df.exists()) {
            try {
                return JSONObject.fromObject(df.read());
            } catch (JSONException e) {
                LOGGER.log(Level.SEVERE,"Failed to parse "+df,e);
                df.delete(); // if we keep this file, it will cause repeated failures
                return null;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"Failed to parse "+df,e);
                df.delete(); // if we keep this file, it will cause repeated failures
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Returns a list of plugins that should be shown in the "available" tab.
     * These are "all plugins - installed plugins".
     */
    @Exported
    public List<Plugin> getAvailables() {
        List<Plugin> r = new ArrayList<Plugin>();
        Data data = getData();
        if(data==null)     return Collections.emptyList();
        for (Plugin p : data.plugins.values()) {
            if(p.getInstalled()==null)
                r.add(p);
        }
        return r;
    }

    /**
     * Gets the information about a specific plugin.
     *
     * @param artifactId
     *      The short name of the plugin. Corresponds to {@link PluginWrapper#getShortName()}.
     *
     * @return
     *      null if no such information is found.
     */
    public Plugin getPlugin(String artifactId) {
        Data dt = getData();
        if(dt==null)    return null;
        return dt.plugins.get(artifactId);
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Gets a URL for the Internet connection check.
     * @return  an "always up" server for Internet connectivity testing, or {@code null} if we are going to skip the test.
     */
    @Exported
    @CheckForNull
    public String getConnectionCheckUrl() {
        Data dt = getData();
        if(dt==null)    return "http://www.google.com/";
        return dt.connectionCheckUrl;
    }

    /**
     * This is where we store the update center data.
     */
    private TextFile getDataFile() {
        return new TextFile(new File(Jenkins.getInstance().getRootDir(),
                                     "updates/" + getId()+".json"));
    }
    
    /**
     * Returns the list of plugins that are updates to currently installed ones.
     *
     * @return
     *      can be empty but never null.
     */
    @Exported
    public List<Plugin> getUpdates() {
        Data data = getData();
        if(data==null)      return Collections.emptyList(); // fail to determine
        
        List<Plugin> r = new ArrayList<Plugin>();
        for (PluginWrapper pw : Jenkins.getInstance().getPluginManager().getPlugins()) {
            Plugin p = pw.getUpdateInfo();
            if(p!=null) r.add(p);
        }
        
        return r;
    }
    
    /**
     * Does any of the plugin has updates?
     */
    @Exported
    public boolean hasUpdates() {
        Data data = getData();
        if(data==null)      return false;
        
        for (PluginWrapper pw : Jenkins.getInstance().getPluginManager().getPlugins()) {
            if(!pw.isBundled() && pw.getUpdateInfo()!=null)
                // do not advertize updates to bundled plugins, since we generally want users to get them
                // as a part of jenkins.war updates. This also avoids unnecessary pinning of plugins. 
                return true;
        }
        return false;
    }
    
    
    /**
     * Exposed to get rid of hardcoding of the URL that serves up update-center.json
     * in Javascript.
     */
    @Exported
    public String getUrl() {
        return url;
    }


    /**
     * URL which exposes the metadata location in a specific update site.
     * @param downloadable, the downloadable id of a specific metatadata json (e.g. hudson.tasks.Maven.MavenInstaller.json)
     * @return the location
     * @since 2.20
     */
    @CheckForNull
    @Restricted(NoExternalUse.class)
    public String getMetadataUrlForDownloadable(String downloadable) {
        String siteUrl = getUrl();
        String updateSiteMetadataUrl = null;
        int baseUrlEnd = siteUrl.indexOf("update-center.json");
        if (baseUrlEnd != -1) {
            String siteBaseUrl = siteUrl.substring(0, baseUrlEnd);
            updateSiteMetadataUrl = siteBaseUrl + "updates/" + downloadable;
        } else {
            LOGGER.log(Level.WARNING, "Url {0} does not look like an update center:", siteUrl);
        }
        return updateSiteMetadataUrl;
    }

    /**
     * Where to actually download the update center?
     *
     * @deprecated
     *      Exposed only for UI.
     */
    @Deprecated
    public String getDownloadUrl() {
        return url;
    }

    /**
     * Is this the legacy default update center site?
     */
    public boolean isLegacyDefault() {
        return isHudsonCI() || isUpdatesFromHudsonLabs();
    }

    private boolean isHudsonCI() {
        return url != null && UpdateCenter.PREDEFINED_UPDATE_SITE_ID.equals(id) && url.startsWith("http://hudson-ci.org/");
    }

    private boolean isUpdatesFromHudsonLabs() {
        return url != null && url.startsWith("http://updates.hudson-labs.org/");
    }

    /**
     * In-memory representation of the update center data.
     */
    public final class Data {
        /**
         * The {@link UpdateSite} ID.
         */
        public final String sourceId;

        /**
         * The latest jenkins.war.
         */
        public final Entry core;
        /**
         * Plugins in the repository, keyed by their artifact IDs.
         */
        public final Map<String,Plugin> plugins = new TreeMap<String,Plugin>(String.CASE_INSENSITIVE_ORDER);
        /**
         * List of warnings (mostly security) published with the update site.
         *
         * @since 2.40
         */
        private final Set<Warning> warnings = new HashSet<Warning>();

        /**
         * If this is non-null, Jenkins is going to check the connectivity to this URL to make sure
         * the network connection is up. Null to skip the check.
         */
        public final String connectionCheckUrl;

        Data(JSONObject o) {
            this.sourceId = Util.intern((String)o.get("id"));
            JSONObject c = o.optJSONObject("core");
            if (c!=null) {
                core = new Entry(sourceId, c, url);
            } else {
                core = null;
            }

            JSONArray w = o.optJSONArray("warnings");
            if (w != null) {
                for (int i = 0; i < w.size(); i++) {
                    try {
                        warnings.add(new Warning(w.getJSONObject(i)));
                    } catch (JSONException ex) {
                        LOGGER.log(Level.WARNING, "Failed to parse JSON for warning", ex);
                    }
                }
            }

            for(Map.Entry<String,JSONObject> e : (Set<Map.Entry<String,JSONObject>>)o.getJSONObject("plugins").entrySet()) {
                Plugin p = new Plugin(sourceId, e.getValue());
                // JENKINS-33308 - include implied dependencies for older plugins that may need them
                List<PluginWrapper.Dependency> implicitDeps = ClassicPluginStrategy.getImpliedDependencies(p.name, p.requiredCore);
                if(!implicitDeps.isEmpty()) {
                    for(PluginWrapper.Dependency dep : implicitDeps) {
                        if(!p.dependencies.containsKey(dep.shortName)) {
                            p.dependencies.put(dep.shortName, dep.version);
                        }
                    }
                }
                plugins.put(Util.intern(e.getKey()), p);
            }

            connectionCheckUrl = (String)o.get("connectionCheckUrl");
        }

        /**
         * Returns the set of warnings
         * @return the set of warnings
         * @since 2.40
         */
        @Restricted(NoExternalUse.class)
        public Set<Warning> getWarnings() {
            return this.warnings;
        }

        /**
         * Is there a new version of the core?
         */
        public boolean hasCoreUpdates() {
            return core != null && core.isNewerThan(Jenkins.VERSION);
        }

        /**
         * Do we support upgrade?
         */
        public boolean canUpgrade() {
            return Lifecycle.get().canRewriteHudsonWar();
        }
    }

    @ExportedBean
    public static class Entry {
        /**
         * {@link UpdateSite} ID.
         */
        @Exported
        public final String sourceId;

        /**
         * Artifact ID.
         */
        @Exported
        public final String name;
        /**
         * The version.
         */
        @Exported
        public final String version;
        /**
         * Download URL.
         */
        @Exported
        public final String url;


        // non-private, non-final for test
        @Restricted(NoExternalUse.class)
        /* final */ String sha1;

        @Restricted(NoExternalUse.class)
        /* final */ String sha256;

        @Restricted(NoExternalUse.class)
        /* final */ String sha512;

        public Entry(String sourceId, JSONObject o) {
            this(sourceId, o, null);
        }

        Entry(String sourceId, JSONObject o, String baseURL) {
            this.sourceId = sourceId;
            this.name = Util.intern(o.getString("name"));
            this.version = Util.intern(o.getString("version"));

            // Trim this to prevent issues when the other end used Base64.encodeBase64String that added newlines
            // to the end in old commons-codec. Not the case on updates.jenkins-ci.org, but let's be safe.
            this.sha1 = Util.fixEmptyAndTrim(o.optString("sha1"));
            this.sha256 = Util.fixEmptyAndTrim(o.optString("sha256"));
            this.sha512 = Util.fixEmptyAndTrim(o.optString("sha512"));

            String url = o.getString("url");
            if (!URI.create(url).isAbsolute()) {
                if (baseURL == null) {
                    throw new IllegalArgumentException("Cannot resolve " + url + " without a base URL");
                }
                url = URI.create(baseURL).resolve(url).toString();
            }
            this.url = url;
        }

        /**
         * The base64 encoded binary SHA-1 checksum of the file.
         * Can be null if not provided by the update site.
         * @since 1.641 (and 1.625.3 LTS)
         */
        // TODO @Exported assuming we want this in the API
        public String getSha1() {
            return sha1;
        }

        /**
         * The base64 encoded SHA-256 checksum of the file.
         * Can be null if not provided by the update site.
         * @since 2.130
         */
        public String getSha256() {
            return sha256;
        }

        /**
         * The base64 encoded SHA-512 checksum of the file.
         * Can be null if not provided by the update site.
         * @since 2.130
         */
        public String getSha512() {
            return sha512;
        }

        /**
         * Checks if the specified "current version" is older than the version of this entry.
         *
         * @param currentVersion
         *      The string that represents the version number to be compared.
         * @return
         *      true if the version listed in this entry is newer.
         *      false otherwise, including the situation where the strings couldn't be parsed as version numbers.
         */
        public boolean isNewerThan(String currentVersion) {
            try {
                return new VersionNumber(currentVersion).compareTo(new VersionNumber(version)) < 0;
            } catch (IllegalArgumentException e) {
                // couldn't parse as the version number.
                return false;
            }
        }

        public Api getApi() {
            return new Api(this);
        }

    }

    /**
     * A version range for {@code Warning}s indicates which versions of a given plugin are affected
     * by it.
     *
     * {@link #name}, {@link #firstVersion} and {@link #lastVersion} fields are only used for administrator notices.
     *
     * The {@link #pattern} is used to determine whether a given warning applies to the current installation.
     *
     * @since 2.40
     */
    @Restricted(NoExternalUse.class)
    public static final class WarningVersionRange {
        /**
         * Human-readable English name for this version range, e.g. 'regular', 'LTS', '2.6 line'.
         */
        @Nullable
        public final String name;

        /**
         * First version in this version range to be subject to the warning.
         */
        @Nullable
        public final String firstVersion;

        /**
         * Last version in this version range to be subject to the warning.
         */
        @Nullable
        public final String lastVersion;

        /**
         * Regular expression pattern for this version range that matches all included version numbers.
         */
        @Nonnull
        private final Pattern pattern;

        public WarningVersionRange(JSONObject o) {
            this.name = Util.fixEmpty(o.optString("name"));
            this.firstVersion = Util.intern(Util.fixEmpty(o.optString("firstVersion")));
            this.lastVersion = Util.intern(Util.fixEmpty(o.optString("lastVersion")));
            Pattern p;
            try {
                p = Pattern.compile(o.getString("pattern"));
            } catch (PatternSyntaxException ex) {
                LOGGER.log(Level.WARNING, "Failed to compile pattern '" + o.getString("pattern") + "', using '.*' instead", ex);
                p = Pattern.compile(".*");
            }
            this.pattern = p;
        }

        public boolean includes(VersionNumber number) {
            return pattern.matcher(number.toString()).matches();
        }
    }

    /**
     * Represents a warning about a certain component, mostly related to known security issues.
     *
     * @see UpdateSiteWarningsConfiguration
     * @see jenkins.security.UpdateSiteWarningsMonitor
     *
     * @since 2.40
     */
    @Restricted(NoExternalUse.class)
    public static final class Warning {

        public enum Type {
            CORE,
            PLUGIN,
            UNKNOWN
        }

        /**
         * The type classifier for this warning.
         */
        @Nonnull
        public /* final */ Type type;

        /**
         * The globally unique ID of this warning.
         *
         * <p>This is typically the CVE identifier or SECURITY issue (Jenkins project);
         * possibly with a unique suffix (e.g. artifactId) if either applies to multiple components.</p>
         */
        @Exported
        @Nonnull
        public final String id;

        /**
         * The name of the affected component.
         * <ul>
         *   <li>If type is 'core', this is 'core' by convention.
         *   <li>If type is 'plugin', this is the artifactId of the affected plugin
         * </ul>
         */
        @Exported
        @Nonnull
        public final String component;

        /**
         * A short, English language explanation for this warning.
         */
        @Exported
        @Nonnull
        public final String message;

        /**
         * A URL with more information about this, typically a security advisory. For use in administrator notices
         * only, so
         */
        @Exported
        @Nonnull
        public final String url;

        /**
         * A list of named version ranges specifying which versions of the named component this warning applies to.
         *
         * If this list is empty, all versions of the component are considered to be affected by this warning.
         */
        @Exported
        @Nonnull
        public final List<WarningVersionRange> versionRanges;

        /**
         *
         * @param o the {@link JSONObject} representing the warning
         * @throws JSONException if the argument does not match the expected format
         */
        @Restricted(NoExternalUse.class)
        public Warning(JSONObject o) {
            try {
                this.type = Type.valueOf(o.getString("type").toUpperCase(Locale.US));
            } catch (IllegalArgumentException ex) {
                this.type = Type.UNKNOWN;
            }
            this.id = o.getString("id");
            this.component = Util.intern(o.getString("name"));
            this.message = o.getString("message");
            this.url = o.getString("url");

            if (o.has("versions")) {
                JSONArray versions = o.getJSONArray("versions");
                List<WarningVersionRange> ranges = new ArrayList<>(versions.size());
                for (int i = 0; i < versions.size(); i++) {
                    WarningVersionRange range = new WarningVersionRange(versions.getJSONObject(i));
                    ranges.add(range);
                }
                this.versionRanges = Collections.unmodifiableList(ranges);
            } else {
                this.versionRanges = Collections.emptyList();
            }
        }

        /**
         * Two objects are considered equal if they are the same type and have the same ID.
         *
         * @param o the other object
         * @return true iff this object and the argument are considered equal
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Warning)) return false;

            Warning warning = (Warning) o;

            return id.equals(warning.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        public boolean isPluginWarning(@Nonnull String pluginName) {
            return type == Type.PLUGIN && pluginName.equals(this.component);
        }

        /**
         * Returns true if this warning is relevant to the current configuration
         * @return true if this warning is relevant to the current configuration
         */
        public boolean isRelevant() {
            switch (this.type) {
                case CORE:
                    VersionNumber current = Jenkins.getVersion();

                    if (!isRelevantToVersion(current)) {
                        return false;
                    }
                    return true;
                case PLUGIN:

                    // check whether plugin is installed
                    PluginWrapper plugin = Jenkins.getInstance().getPluginManager().getPlugin(this.component);
                    if (plugin == null) {
                        return false;
                    }

                    // check whether warning is relevant to installed version
                    VersionNumber currentCore = plugin.getVersionNumber();
                    if (!isRelevantToVersion(currentCore)) {
                        return false;
                    }
                    return true;
                case UNKNOWN:
                default:
                    return false;
            }
        }

        public boolean isRelevantToVersion(@Nonnull VersionNumber version) {
            if (this.versionRanges.isEmpty()) {
                // no version ranges specified, so all versions are affected
                return true;
            }

            for (UpdateSite.WarningVersionRange range : this.versionRanges) {
                if (range.includes(version)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String get(JSONObject o, String prop) {
        if(o.has(prop))
            return o.getString(prop);
        else
            return null;
    }

    static final Predicate<Object> IS_DEP_PREDICATE = x -> x instanceof JSONObject && get(((JSONObject)x), "name") != null;
    static final Predicate<Object> IS_NOT_OPTIONAL = x-> "false".equals(get(((JSONObject)x), "optional"));

    public final class Plugin extends Entry {
        /**
         * Optional URL to the Wiki page that discusses this plugin.
         */
        @Exported
        public final String wiki;
        /**
         * Human readable title of the plugin, taken from Wiki page.
         * Can be null.
         *
         * <p>
         * beware of XSS vulnerability since this data comes from Wiki
         */
        @Exported
        public final String title;
        /**
         * Optional excerpt string.
         */
        @Exported
        public final String excerpt;
        /**
         * Optional version # from which this plugin release is configuration-compatible.
         */
        @Exported
        public final String compatibleSinceVersion;
        /**
         * Version of Jenkins core this plugin was compiled against.
         */
        @Exported
        public final String requiredCore;
        /**
         * Version of Java this plugin requires to run.
         *
         * @since TODO
         */
        @Exported
        public final String minimumJavaVersion;
        /**
         * Categories for grouping plugins, taken from labels assigned to wiki page.
         * Can be null.
         */
        @Exported
        public final String[] categories;

        /**
         * Dependencies of this plugin, a name -&gt; version mapping.
         */
        @Exported
        public final Map<String,String> dependencies;
        
        /**
         * Optional dependencies of this plugin.
         */
        @Exported
        public final Map<String,String> optionalDependencies;

        @DataBoundConstructor
        public Plugin(String sourceId, JSONObject o) {
            super(sourceId, o, UpdateSite.this.url);
            this.wiki = get(o,"wiki");
            this.title = get(o,"title");
            this.excerpt = get(o,"excerpt");
            this.compatibleSinceVersion = Util.intern(get(o,"compatibleSinceVersion"));
            this.minimumJavaVersion = Util.intern(get(o, "minimumJavaVersion"));
            this.requiredCore = Util.intern(get(o,"requiredCore"));
            this.categories = o.has("labels") ? internInPlace((String[])o.getJSONArray("labels").toArray(EMPTY_STRING_ARRAY)) : null;
            JSONArray ja = o.getJSONArray("dependencies");
            int depCount = (int)(ja.stream().filter(IS_DEP_PREDICATE.and(IS_NOT_OPTIONAL)).count());
            int optionalDepCount = (int)(ja.stream().filter(IS_DEP_PREDICATE.and(IS_NOT_OPTIONAL.negate())).count());
            dependencies = getPresizedMutableMap(depCount);
            optionalDependencies = getPresizedMutableMap(optionalDepCount);

            for(Object jo : o.getJSONArray("dependencies")) {
                JSONObject depObj = (JSONObject) jo;
                // Make sure there's a name attribute and that the optional value isn't true.
                String depName = Util.intern(get(depObj,"name"));
                if (depName!=null) {
                    if (get(depObj, "optional").equals("false")) {
                        dependencies.put(depName, Util.intern(get(depObj, "version")));
                    } else {
                        optionalDependencies.put(depName, Util.intern(get(depObj, "version")));
                    }
                }
            }

        }



        public String getDisplayName() {
            String displayName;
            if(title!=null)
                displayName = title;
            else
                displayName = name;
            return StringUtils.removeStart(displayName, "Jenkins ");
        }

        /**
         * If some version of this plugin is currently installed, return {@link PluginWrapper}.
         * Otherwise null.
         */
        @Exported
        public PluginWrapper getInstalled() {
            PluginManager pm = Jenkins.getInstance().getPluginManager();
            return pm.getPlugin(name);
        }

        /**
         * If the plugin is already installed, and the new version of the plugin has a "compatibleSinceVersion"
         * value (i.e., it's only directly compatible with that version or later), this will check to
         * see if the installed version is older than the compatible-since version. If it is older, it'll return false.
         * If it's not older, or it's not installed, or it's installed but there's no compatibleSinceVersion
         * specified, it'll return true.
         */
        @Exported
        public boolean isCompatibleWithInstalledVersion() {
            PluginWrapper installedVersion = getInstalled();
            if (installedVersion != null) {
                if (compatibleSinceVersion != null) {
                    if (new VersionNumber(installedVersion.getVersion())
                            .isOlderThan(new VersionNumber(compatibleSinceVersion))) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Returns a list of dependent plugins which need to be installed or upgraded for this plugin to work.
         */
        @Exported
        public List<Plugin> getNeededDependencies() {
            List<Plugin> deps = new ArrayList<Plugin>();

            for(Map.Entry<String,String> e : dependencies.entrySet()) {
                VersionNumber requiredVersion = e.getValue() != null ? new VersionNumber(e.getValue()) : null;
                Plugin depPlugin = Jenkins.getInstance().getUpdateCenter().getPlugin(e.getKey(), requiredVersion);
                if (depPlugin == null) {
                    LOGGER.log(Level.WARNING, "Could not find dependency {0} of {1}", new Object[] {e.getKey(), name});
                    continue;
                }

                // Is the plugin installed already? If not, add it.
                PluginWrapper current = depPlugin.getInstalled();

                if (current ==null) {
                    deps.add(depPlugin);
                }
                // If the dependency plugin is installed, is the version we depend on newer than
                // what's installed? If so, upgrade.
                else if (current.isOlderThan(requiredVersion)) {
                    deps.add(depPlugin);
                }
                // JENKINS-34494 - or if the plugin is disabled, this will allow us to enable it
                else if (!current.isEnabled()) {
                    deps.add(depPlugin);
                }
            }

            for(Map.Entry<String,String> e : optionalDependencies.entrySet()) {
                VersionNumber requiredVersion = e.getValue() != null ? new VersionNumber(e.getValue()) : null;
                Plugin depPlugin = Jenkins.getInstance().getUpdateCenter().getPlugin(e.getKey(), requiredVersion);
                if (depPlugin == null) {
                    continue;
                }

                PluginWrapper current = depPlugin.getInstalled();

                // If the optional dependency plugin is installed, is the version we depend on newer than
                // what's installed? If so, upgrade.
                if (current != null && current.isOlderThan(requiredVersion)) {
                    deps.add(depPlugin);
                }
            }

            return deps;
        }
        
        public boolean isForNewerHudson() {
            try {
                return requiredCore!=null && new VersionNumber(requiredCore).isNewerThan(
                  new VersionNumber(Jenkins.VERSION.replaceFirst("SHOT *\\(private.*\\)", "SHOT")));
            } catch (NumberFormatException nfe) {
                return true;  // If unable to parse version
            }
        }

        /**
         * Returns true iff the plugin declares a minimum Java version and it's newer than what the Jenkins master is running on.
         * @since TODO
         */
        public boolean isForNewerJava() {
            try {
                final VersionNumber currentRuntimeJavaVersion = JavaUtils.getCurrentJavaRuntimeVersionNumber();
                return minimumJavaVersion != null && new VersionNumber(minimumJavaVersion).isNewerThan(
                        currentRuntimeJavaVersion);
            } catch (NumberFormatException nfe) {
                logBadMinJavaVersion();
                return false; // treat this as undeclared minimum Java version
            }
        }

        public VersionNumber getNeededDependenciesRequiredCore() {
            VersionNumber versionNumber = null;
            try {
                versionNumber = requiredCore == null ? null : new VersionNumber(requiredCore);
            } catch (NumberFormatException nfe) {
                // unable to parse version
            }
            for (Plugin p: getNeededDependencies()) {
                VersionNumber v = p.getNeededDependenciesRequiredCore();
                if (versionNumber == null || v.isNewerThan(versionNumber)) versionNumber = v;
            }
            return versionNumber;
        }

        /**
         * Returns the minimum Java version needed to use the plugin and all its dependencies.
         * @since TODO
         * @return the minimum Java version needed to use the plugin and all its dependencies, or null if unspecified.
         */
        @CheckForNull
        public VersionNumber getNeededDependenciesMinimumJavaVersion() {
            VersionNumber versionNumber = null;
            try {
                versionNumber = minimumJavaVersion == null ? null : new VersionNumber(minimumJavaVersion);
            } catch (NumberFormatException nfe) {
                logBadMinJavaVersion();
            }
            for (Plugin p: getNeededDependencies()) {
                VersionNumber v = p.getNeededDependenciesMinimumJavaVersion();
                if (v == null) {
                    continue;
                }
                if (versionNumber == null || v.isNewerThan(versionNumber)) {
                    versionNumber = v;
                }
            }
            return versionNumber;
        }

        private void logBadMinJavaVersion() {
            LOGGER.log(Level.WARNING, "minimumJavaVersion was specified for plugin {0} but unparseable (received {1})",
                       new String[]{this.name, this.minimumJavaVersion});
        }

        public boolean isNeededDependenciesForNewerJenkins() {
            return isNeededDependenciesForNewerJenkins(new PluginManager.MetadataCache());
        }

        @Restricted(NoExternalUse.class) // table.jelly
        public boolean isNeededDependenciesForNewerJenkins(PluginManager.MetadataCache cache) {
            return cache.of("isNeededDependenciesForNewerJenkins:" + name, Boolean.class, () -> {
                for (Plugin p : getNeededDependencies()) {
                    if (p.isForNewerHudson() || p.isNeededDependenciesForNewerJenkins()) {
                        return true;
                    }
                }
                return false;
            });
        }

        /**
         * Returns true iff any of the plugin dependencies require a newer Java than Jenkins is running on.
         *
         * @since TODO
         */
        public boolean isNeededDependenciesForNewerJava() {
            for (Plugin p: getNeededDependencies()) {
                if (p.isForNewerJava() || p.isNeededDependenciesForNewerJava()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * If at least some of the plugin's needed dependencies are already installed, and the new version of the
         * needed dependencies plugin have a "compatibleSinceVersion"
         * value (i.e., it's only directly compatible with that version or later), this will check to
         * see if the installed version is older than the compatible-since version. If it is older, it'll return false.
         * If it's not older, or it's not installed, or it's installed but there's no compatibleSinceVersion
         * specified, it'll return true.
         */
        public boolean isNeededDependenciesCompatibleWithInstalledVersion() {
            return isNeededDependenciesCompatibleWithInstalledVersion(new PluginManager.MetadataCache());
        }

        @Restricted(NoExternalUse.class) // table.jelly
        public boolean isNeededDependenciesCompatibleWithInstalledVersion(PluginManager.MetadataCache cache) {
            return cache.of("isNeededDependenciesCompatibleWithInstalledVersion:" + name, Boolean.class, () -> {
                for (Plugin p : getNeededDependencies()) {
                    if (!p.isCompatibleWithInstalledVersion() || !p.isNeededDependenciesCompatibleWithInstalledVersion()) {
                        return false;
                    }
                }
                return true;
            });
        }

        /**
         * @since 2.40
         */
        @CheckForNull
        @Restricted(NoExternalUse.class)
        public Set<Warning> getWarnings() {
            UpdateSiteWarningsConfiguration configuration = ExtensionList.lookupSingleton(UpdateSiteWarningsConfiguration.class);
            Set<Warning> warnings = new HashSet<>();

            for (Warning warning: configuration.getAllWarnings()) {
                if (configuration.isIgnored(warning)) {
                    // warning is currently being ignored
                    continue;
                }
                if (!warning.isPluginWarning(this.name)) {
                    // warning is not about this plugin
                    continue;
                }

                if (!warning.isRelevantToVersion(new VersionNumber(this.version))) {
                    // warning is not relevant to this version
                    continue;
                }
                warnings.add(warning);
            }

            return warnings;
        }

        /**
         * @since 2.40
         */
        @Restricted(DoNotUse.class)
        public boolean hasWarnings() {
            return getWarnings().size() > 0;
        }

        /**
         * @deprecated as of 1.326
         *      Use {@link #deploy()}.
         */
        @Deprecated
        public void install() {
            deploy();
        }

        public Future<UpdateCenterJob> deploy() {
            return deploy(false);
        }

        /**
         * Schedules the installation of this plugin.
         *
         * <p>
         * This is mainly intended to be called from the UI. The actual installation work happens
         * asynchronously in another thread.
         *
         * @param dynamicLoad
         *      If true, the plugin will be dynamically loaded into this Jenkins. If false,
         *      the plugin will only take effect after the reboot.
         *      See {@link UpdateCenter#isRestartRequiredForCompletion()}
         */
        public Future<UpdateCenterJob> deploy(boolean dynamicLoad) {
            return deploy(dynamicLoad, null);
        }

        /**
         * Schedules the installation of this plugin.
         *
         * <p>
         * This is mainly intended to be called from the UI. The actual installation work happens
         * asynchronously in another thread.
         *
         * @param dynamicLoad
         *      If true, the plugin will be dynamically loaded into this Jenkins. If false,
         *      the plugin will only take effect after the reboot.
         *      See {@link UpdateCenter#isRestartRequiredForCompletion()}
         * @param correlationId A correlation ID to be set on the job.
         */
        @Restricted(NoExternalUse.class)
        public Future<UpdateCenterJob> deploy(boolean dynamicLoad, @CheckForNull UUID correlationId) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
            for (Plugin dep : getNeededDependencies()) {
                UpdateCenter.InstallationJob job = uc.getJob(dep);
                if (job == null || job.status instanceof UpdateCenter.DownloadJob.Failure) {
                    LOGGER.log(Level.INFO, "Adding dependent install of " + dep.name + " for plugin " + name);
                    dep.deploy(dynamicLoad);
                } else {
                    LOGGER.log(Level.INFO, "Dependent install of " + dep.name + " for plugin " + name + " already added, skipping");
                }
            }
            PluginWrapper pw = getInstalled();
            if(pw != null) { // JENKINS-34494 - check for this plugin being disabled
                Future<UpdateCenterJob> enableJob = null;
                if(!pw.isEnabled()) {
                    UpdateCenter.EnableJob job = uc.new EnableJob(UpdateSite.this, null, this, dynamicLoad);
                    job.setCorrelationId(correlationId);
                    enableJob = uc.addJob(job);
                }
                if(pw.getVersionNumber().equals(new VersionNumber(version))) {
                    return enableJob != null ? enableJob : uc.addJob(uc.new NoOpJob(UpdateSite.this, null, this));
                }
            }
            UpdateCenter.InstallationJob job = createInstallationJob(this, uc, dynamicLoad);
            job.setCorrelationId(correlationId);
            return uc.addJob(job);
        }

        /**
         * Schedules the downgrade of this plugin.
         */
        public Future<UpdateCenterJob> deployBackup() {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
            return uc.addJob(uc.new PluginDowngradeJob(this, UpdateSite.this, Jenkins.getAuthentication()));
        }
        /**
         * Making the installation web bound.
         */
        @RequirePOST
        public HttpResponse doInstall() throws IOException {
            deploy(false);
            return HttpResponses.redirectTo("../..");
        }

        @RequirePOST
        public HttpResponse doInstallNow() throws IOException {
            deploy(true);
            return HttpResponses.redirectTo("../..");
        }

        /**
         * Performs the downgrade of the plugin.
         */
        @RequirePOST
        public HttpResponse doDowngrade() throws IOException {
            deployBackup();
            return HttpResponses.redirectTo("../..");
        }
    }

    private static final long DAY = DAYS.toMillis(1);

    private static final Logger LOGGER = Logger.getLogger(UpdateSite.class.getName());

    // The name uses UpdateCenter for compatibility reason.
    public static boolean neverUpdate = SystemProperties.getBoolean(UpdateCenter.class.getName()+".never");

}
