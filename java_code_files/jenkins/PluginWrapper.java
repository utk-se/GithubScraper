/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Yahoo! Inc., Erik Ramfelt, Tom Huybrechts
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
package hudson;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import hudson.PluginManager.PluginInstanceStore;
import hudson.model.AdministrativeMonitor;
import hudson.model.Api;
import hudson.model.ModelObject;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.util.VersionNumber;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import jenkins.util.java.JavaUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static hudson.PluginWrapper.PluginDisableStatus.ALREADY_DISABLED;
import static hudson.PluginWrapper.PluginDisableStatus.DISABLED;
import static hudson.PluginWrapper.PluginDisableStatus.ERROR_DISABLING;
import static hudson.PluginWrapper.PluginDisableStatus.NOT_DISABLED_DEPENDANTS;
import static hudson.PluginWrapper.PluginDisableStatus.NO_SUCH_PLUGIN;
import static java.util.logging.Level.WARNING;
import static org.apache.commons.io.FilenameUtils.getBaseName;

/**
 * Represents a Jenkins plug-in and associated control information
 * for Jenkins to control {@link Plugin}.
 *
 * <p>
 * A plug-in is packaged into a jar file whose extension is {@code ".jpi"} (or {@code ".hpi"} for backward compatibility),
 * A plugin needs to have a special manifest entry to identify what it is.
 *
 * <p>
 * At the runtime, a plugin has two distinct state axis.
 * <ol>
 *  <li>Enabled/Disabled. If enabled, Jenkins is going to use it
 *      next time Jenkins runs. Otherwise the next run will ignore it.
 *  <li>Activated/Deactivated. If activated, that means Jenkins is using
 *      the plugin in this session. Otherwise it's not.
 * </ol>
 * <p>
 * For example, an activated but disabled plugin is still running but the next
 * time it won't.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class PluginWrapper implements Comparable<PluginWrapper>, ModelObject {
    /**
     * A plugin won't be loaded unless his declared dependencies are present and match the required minimal version.
     * This can be set to false to disable the version check (legacy behaviour)
     */
    private static final boolean ENABLE_PLUGIN_DEPENDENCIES_VERSION_CHECK = Boolean.parseBoolean(System.getProperty(PluginWrapper.class.getName()+"." + "dependenciesVersionCheck.enabled", "true"));

    /**
     * {@link PluginManager} to which this belongs to.
     */
    public final PluginManager parent;

    /**
     * Plugin manifest.
     * Contains description of the plugin.
     */
    private final Manifest manifest;

    /**
     * {@link ClassLoader} for loading classes from this plugin.
     * Null if disabled.
     */
    public final ClassLoader classLoader;

    /**
     * Base URL for loading static resources from this plugin.
     * Null if disabled. The static resources are mapped under
     * {@code CONTEXTPATH/plugin/SHORTNAME/}.
     */
    public final URL baseResourceURL;

    /**
     * Used to control enable/disable setting of the plugin.
     * If this file exists, plugin will be disabled.
     */
    private final File disableFile;

    /**
     * A .jpi file, an exploded plugin directory, or a .jpl file.
     */
    private final File archive;

    /**
     * Short name of the plugin. The artifact Id of the plugin.
     * This is also used in the URL within Jenkins, so it needs
     * to remain stable even when the *.jpi file name is changed
     * (like Maven does.)
     */
    private final String shortName;

    /**
     * True if this plugin is activated for this session.
     * The snapshot of {@code disableFile.exists()} as of the start up.
     */
    private final boolean active;
    
    private boolean hasCycleDependency = false;

    private final List<Dependency> dependencies;
    private final List<Dependency> optionalDependencies;

    public List<String> getDependencyErrors() {
        return Collections.unmodifiableList(new ArrayList<>(dependencyErrors.keySet()));
    }

    @Restricted(NoExternalUse.class) // Jelly use
    public List<String> getOriginalDependencyErrors() {
        Predicate<Map.Entry<String, Boolean>> p = Map.Entry::getValue;
        return dependencyErrors.entrySet().stream().filter(p.negate()).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    @Restricted(NoExternalUse.class) // Jelly use
    public boolean hasOriginalDependencyErrors() {
        return !getOriginalDependencyErrors().isEmpty();
    }

    @Restricted(NoExternalUse.class) // Jelly use
    public List<String> getDerivedDependencyErrors() {
        return dependencyErrors.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    @Restricted(NoExternalUse.class) // Jelly use
    public boolean hasDerivedDependencyErrors() {
        return !getDerivedDependencyErrors().isEmpty();
    }

    /**
     * A String error message, and a boolean indicating whether it's an original error (false) or downstream from an original one (true)
     */
    private final transient Map<String, Boolean> dependencyErrors = new HashMap<>(0);

    /**
     * Is this plugin bundled in jenkins.war?
     */
    /*package*/ boolean isBundled;

    /**
     * List of plugins that depend on this plugin.
     */
    private Set<String> dependants = Collections.emptySet();

    /**
     * List of plugins that depend optionally on this plugin.
     */
    private Set<String> optionalDependants = Collections.emptySet();

    /**
     * The core can depend on a plugin if it is bundled. Sometimes it's the only thing that
     * depends on the plugin e.g. UI support library bundle plugin.
     */
    private static Set<String> CORE_ONLY_DEPENDANT = ImmutableSet.copyOf(Arrays.asList("jenkins-core"));

    /**
     * Set the list of components that depend on this plugin.
     * @param dependants The list of components that depend on this plugin.
     */
    public void setDependants(@Nonnull Set<String> dependants) {
        this.dependants = dependants;
    }

    /**
     * Set the list of components that depend optionally on this plugin.
     * @param optionalDependants The list of components that depend optionally on this plugin.
     */
    public void setOptionalDependants(@Nonnull Set<String> optionalDependants) {
        this.optionalDependants = optionalDependants;
    }

    /**
     * Get the list of components that depend on this plugin.
     * @return The list of components that depend on this plugin.
     */
    public @Nonnull Set<String> getDependants() {
        if (isBundled && dependants.isEmpty()) {
            return CORE_ONLY_DEPENDANT;
        } else {
            return dependants;
        }
    }

    /**
     * @return The list of components that depend optionally on this plugin.
     */
    public @Nonnull Set<String> getOptionalDependants() {
        return optionalDependants;
    }

    /**
     * Does this plugin have anything that depends on it.
     * @return {@code true} if something (Jenkins core, or another plugin) depends on this
     * plugin, otherwise {@code false}.
     */
    public boolean hasDependants() {
        return (isBundled || !dependants.isEmpty());
    }

    /**
     * Does this plugin have anything that depends optionally on it.
     * @return {@code true} if something (Jenkins core, or another plugin) depends optionally on this
     * plugin, otherwise {@code false}.
     */
    public boolean hasOptionalDependants() {
        return !optionalDependants.isEmpty();
    }


    /**
     * Does this plugin depend on any other plugins.
     * @return {@code true} if this plugin depends on other plugins, otherwise {@code false}.
     */
    public boolean hasDependencies() {
        return (dependencies != null && !dependencies.isEmpty());
    }

    @ExportedBean
    public static final class Dependency {
        @Exported
        public final String shortName;
        @Exported
        public final String version;
        @Exported
        public final boolean optional;

        public Dependency(String s) {
            int idx = s.indexOf(':');
            if(idx==-1)
                throw new IllegalArgumentException("Illegal dependency specifier "+s);
            this.shortName = Util.intern(s.substring(0,idx));
            String version = Util.intern(s.substring(idx+1));

            boolean isOptional = false;
            String[] osgiProperties = version.split("[;]");
            for (int i = 1; i < osgiProperties.length; i++) {
                String osgiProperty = osgiProperties[i].trim();
                if (osgiProperty.equalsIgnoreCase("resolution:=optional")) {
                    isOptional = true;
                }
            }
            this.optional = isOptional;
            if (isOptional) {
                this.version = osgiProperties[0];
            } else {
                this.version = version;
            }
        }

        @Override
        public String toString() {
            return shortName + " (" + version + ")" + (optional ? " optional" : "");
        }        
    }

    /**
     * @param archive
     *      A .jpi archive file jar file, or a .jpl linked plugin.
     *  @param manifest
     *  	The manifest for the plugin
     *  @param baseResourceURL
     *  	A URL pointing to the resources for this plugin
     *  @param classLoader
     *  	a classloader that loads classes from this plugin and its dependencies
     *  @param disableFile
     *  	if this file exists on startup, the plugin will not be activated
     *  @param dependencies a list of mandatory dependencies
     *  @param optionalDependencies a list of optional dependencies
     */
    public PluginWrapper(PluginManager parent, File archive, Manifest manifest, URL baseResourceURL, 
			ClassLoader classLoader, File disableFile, 
			List<Dependency> dependencies, List<Dependency> optionalDependencies) {
        this.parent = parent;
		this.manifest = manifest;
		this.shortName = Util.intern(computeShortName(manifest, archive.getName()));
		this.baseResourceURL = baseResourceURL;
		this.classLoader = classLoader;
		this.disableFile = disableFile;
		this.active = !disableFile.exists();
		this.dependencies = dependencies;
		this.optionalDependencies = optionalDependencies;
        this.archive = archive;
    }

    public String getDisplayName() {
        return StringUtils.removeStart(getLongName(), "Jenkins ");
    }

    public Api getApi() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        return new Api(this);
    }

    /**
     * Returns the URL of the index page jelly script.
     */
    public URL getIndexPage() {
        // In the current impl dependencies are checked first, so the plugin itself
        // will add the last entry in the getResources result.
        URL idx = null;
        try {
            Enumeration<URL> en = classLoader.getResources("index.jelly");
            while (en.hasMoreElements())
                idx = en.nextElement();
        } catch (IOException ignore) { }
        // In case plugin has dependencies but is missing its own index.jelly,
        // check that result has this plugin's artifactId in it:
        return idx != null && idx.toString().contains(shortName) ? idx : null;
    }

    static String computeShortName(Manifest manifest, String fileName) {
        // use the name captured in the manifest, as often plugins
        // depend on the specific short name in its URLs.
        String n = manifest.getMainAttributes().getValue("Short-Name");
        if(n!=null)     return n;

        // maven seems to put this automatically, so good fallback to check.
        n = manifest.getMainAttributes().getValue("Extension-Name");
        if(n!=null)     return n;

        // otherwise infer from the file name, since older plugins don't have
        // this entry.
        return getBaseName(fileName);
    }

    @Exported
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public List<Dependency> getOptionalDependencies() {
        return optionalDependencies;
    }


    /**
     * Returns the short name suitable for URL.
     */
    @Exported
    public String getShortName() {
        return shortName;
    }

    /**
     * Gets the instance of {@link Plugin} contributed by this plugin.
     */
    public @CheckForNull Plugin getPlugin() {
        PluginInstanceStore pis = Jenkins.lookup(PluginInstanceStore.class);
        return pis != null ? pis.store.get(this) : null;
    }

    /**
     * Gets the URL that shows more information about this plugin.
     * @return
     *      null if this information is unavailable.
     * @since 1.283
     */
    @Exported
    public String getUrl() {
        // first look for the manifest entry. This is new in maven-hpi-plugin 1.30
        String url = manifest.getMainAttributes().getValue("Url");
        if(url!=null)      return url;

        // fallback to update center metadata
        UpdateSite.Plugin ui = getInfo();
        if(ui!=null)    return ui.wiki;

        return null;
    }
    
    

    @Override
    public String toString() {
        return "Plugin:" + getShortName();
    }

    /**
     * Returns a one-line descriptive name of this plugin.
     */
    @Exported
    public String getLongName() {
        String name = manifest.getMainAttributes().getValue("Long-Name");
        if(name!=null)      return name;
        return shortName;
    }

    /**
     * Does this plugin supports dynamic loading?
     */
    @Exported
    public YesNoMaybe supportsDynamicLoad() {
        String v = manifest.getMainAttributes().getValue("Support-Dynamic-Loading");
        if (v==null) return YesNoMaybe.MAYBE;
        return Boolean.parseBoolean(v) ? YesNoMaybe.YES : YesNoMaybe.NO;
    }

    /**
     * Returns the version number of this plugin
     */
    @Exported
    public String getVersion() {
        return getVersionOf(manifest);
    }

    private String getVersionOf(Manifest manifest) {
        String v = manifest.getMainAttributes().getValue("Plugin-Version");
        if(v!=null)      return v;

        // plugins generated before maven-hpi-plugin 1.3 should still have this attribute
        v = manifest.getMainAttributes().getValue("Implementation-Version");
        if(v!=null)      return v;

        return "???";
    }

    /**
     * Returns the required Jenkins core version of this plugin.
     * @return the required Jenkins core version of this plugin.
     * @since 2.16
     */
    @Exported
    public @CheckForNull String getRequiredCoreVersion() {
        String v = manifest.getMainAttributes().getValue("Jenkins-Version");
        if (v!= null) return v;

        v = manifest.getMainAttributes().getValue("Hudson-Version");
        if (v!= null) return v;
        return null;
    }

    /**
     * Returns the minimum Java version of this plugin, as specified in the plugin metadata.
     * Generally coming from the <code>java.level</code> extracted as MANIFEST's metadata with
     * <a href="https://github.com/jenkinsci/plugin-pom/pull/134">this addition on the plugins' parent pom</a>.
     *
     * @see <a href="https://github.com/jenkinsci/maven-hpi-plugin/pull/75">maven-hpi-plugin#PR-75</a>.
     *
     * @since TODO
     */
    @Exported
    public @CheckForNull String getMinimumJavaVersion() {
        return manifest.getMainAttributes().getValue("Minimum-Java-Version");
    }

    /**
     * Returns the version number of this plugin
     */
    public VersionNumber getVersionNumber() {
        return new VersionNumber(getVersion());
    }

    /**
     * Returns true if the version of this plugin is older than the given version.
     */
    public boolean isOlderThan(VersionNumber v) {
        try {
            return getVersionNumber().compareTo(v) < 0;
        } catch (IllegalArgumentException e) {
            // if we can't figure out our current version, it probably means it's very old,
            // since the version information is missing only from the very old plugins 
            return true;
        }
    }

    /**
     * Terminates the plugin.
     */
    public void stop() {
        Plugin plugin = getPlugin();
        if (plugin != null) {
            try {
                LOGGER.log(Level.FINE, "Stopping {0}", shortName);
                plugin.stop();
            } catch (Throwable t) {
                LOGGER.log(WARNING, "Failed to shut down " + shortName, t);
            }
        } else {
            LOGGER.log(Level.FINE, "Could not find Plugin instance to stop for {0}", shortName);
        }
        // Work around a bug in commons-logging.
        // See http://www.szegedi.org/articles/memleak.html
        LogFactory.release(classLoader);
    }

    public void releaseClassLoader() {
        if (classLoader instanceof Closeable)
            try {
                ((Closeable) classLoader).close();
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to shut down classloader",e);
            }
    }

    /**
     * Enables this plugin next time Jenkins runs.
     */
    public void enable() throws IOException {
        if (!disableFile.exists()) {
            LOGGER.log(Level.FINEST, "Plugin {0} has been already enabled. Skipping the enable() operation", getShortName());
            return;
        }
        if(!disableFile.delete())
            throw new IOException("Failed to delete "+disableFile);
    }

    /**
     * Disables this plugin next time Jenkins runs. As it doesn't check anything, it's recommended to use the method
     * {@link #disable(PluginDisableStrategy)}
     */
    @Deprecated //see https://issues.jenkins-ci.org/browse/JENKINS-27177
    public void disable() throws IOException {
        disableWithoutCheck();
    }

    /**
     * Disable a plugin wihout checking any dependency. Only add the disable file.
     * @throws IOException
     */
    private void disableWithoutCheck() throws IOException {
        // creates an empty file
        try (OutputStream os = Files.newOutputStream(disableFile.toPath())) {
            os.close();
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }

    /**
     * Disable this plugin using a strategy.
     * @param strategy strategy to use
     * @return an object representing the result of the disablement of this plugin and its dependants plugins.
     */
    public @Nonnull PluginDisableResult disable(@Nonnull PluginDisableStrategy strategy) {
        PluginDisableResult result = new PluginDisableResult(shortName);

        if (!this.isEnabled()) {
            result.setMessage(Messages.PluginWrapper_Already_Disabled(shortName));
            result.setStatus(ALREADY_DISABLED);
            return result;
        }

        // Act as a flag indicating if this plugin, finally, can be disabled. If there is a not-disabled-dependant
        // plugin, this one couldn't be disabled.
        String aDependantNotDisabled = null;

        // List of dependants plugins to 'check'. 'Check' means disable for mandatory or all strategies, or review if
        // this dependant-mandatory plugin is enabled in order to return an error for the NONE strategy.
        Set<String> dependantsToCheck = dependantsToCheck(strategy);

        // Review all the dependants and add to the plugin result what happened with its dependants
        for (String dependant : dependantsToCheck) {
            PluginWrapper dependantPlugin = parent.getPlugin(dependant);

            // The dependant plugin doesn't exist, add an error to the report
            if (dependantPlugin == null) {
                PluginDisableResult dependantStatus = new PluginDisableResult(dependant, NO_SUCH_PLUGIN, Messages.PluginWrapper_NoSuchPlugin(dependant));
                result.addDependantDisableStatus(dependantStatus);

            // If the strategy is none and there is some enabled dependant plugin, the plugin cannot be disabled. If
            // this dependant plugin is not enabled, continue searching for one enabled.
            } else if (strategy.equals(PluginDisableStrategy.NONE)) {
                if (dependantPlugin.isEnabled()) {
                    aDependantNotDisabled = dependant;
                    break; // in this case, we don't need to continue with the rest of its dependants
                }

            // If the strategy is not none and this dependant plugin is not enabled, add it as already disabled
            } else if (!dependantPlugin.isEnabled()) {
                PluginDisableResult dependantStatus = new PluginDisableResult(dependant, ALREADY_DISABLED, Messages.PluginWrapper_Already_Disabled(dependant));
                result.addDependantDisableStatus(dependantStatus);

            // If the strategy is not none and this dependant plugin is enabled, disable it
            } else {
                // As there is no cycles in the plugin dependencies, the recursion shouldn't be infinite. The
                // strategy used is the same for its dependants plugins
                PluginDisableResult dependantResult = dependantPlugin.disable(strategy);
                PluginDisableStatus dependantStatus = dependantResult.status;

                // If something wrong happened, flag this dependant plugin to set the plugin later as not-disabled due
                // to its dependants plugins.
                if (ERROR_DISABLING.equals(dependantStatus) || NOT_DISABLED_DEPENDANTS.equals(dependantStatus)) {
                    aDependantNotDisabled = dependant;
                    break; // we found a dependant plugin enabled, stop looking for dependant plugins to disable.
                }
                result.addDependantDisableStatus(dependantResult);
            }
        }

        // If there is no enabled-dependant plugin, disable this plugin and add it to the result
        if (aDependantNotDisabled == null) {
            try {
                this.disableWithoutCheck();
                result.setMessage(Messages.PluginWrapper_Plugin_Disabled(shortName));
                result.setStatus(DISABLED);
            } catch (IOException io) {
                result.setMessage(Messages.PluginWrapper_Error_Disabling(shortName, io.toString()));
                result.setStatus(ERROR_DISABLING);
            }
        // if there is yet some not disabled dependant plugin (only possible with none strategy), this plugin cannot
        // be disabled.
        } else {
            result.setMessage(Messages.PluginWrapper_Plugin_Has_Dependant(shortName, aDependantNotDisabled, strategy));
            result.setStatus(NOT_DISABLED_DEPENDANTS);
        }

        return result;
    }

    private Set<String> dependantsToCheck(PluginDisableStrategy strategy) {
        Set<String> dependantsToCheck;
        switch (strategy) {
            case ALL:
                // getDependants returns all the dependant plugins, mandatory or optional.
                dependantsToCheck = this.getDependants();
                break;
            default:
                // It includes MANDATORY, NONE:
                // with NONE, the process only fail if mandatory dependant plugins exists
                // As of getDependants has all the dependants, we get the difference between them and only the optionals
                dependantsToCheck = Sets.difference(this.getDependants(), this.getOptionalDependants());
        }
        return dependantsToCheck;
    }

    /**
     * Returns true if this plugin is enabled for this session.
     */
    @Exported
    public boolean isActive() {
        return active && !hasCycleDependency();
    }
    
    public boolean hasCycleDependency(){
        return hasCycleDependency;
    }

    public void setHasCycleDependency(boolean hasCycle){
        hasCycleDependency = hasCycle;
    }
    
    @Exported
    public boolean isBundled() {
        return isBundled;
    }

    /**
     * If true, the plugin is going to be activated next time
     * Jenkins runs.
     */
    @Exported
    public boolean isEnabled() {
        return !disableFile.exists();
    }

    public Manifest getManifest() {
        return manifest;
    }

    public void setPlugin(Plugin plugin) {
        Jenkins.lookup(PluginInstanceStore.class).store.put(this,plugin);
        plugin.wrapper = this;
    }

    public String getPluginClass() {
        return manifest.getMainAttributes().getValue("Plugin-Class");
    }

    public boolean hasLicensesXml() {
        try {
            new URL(baseResourceURL,"WEB-INF/licenses.xml").openStream().close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Makes sure that all the dependencies exist, and then accept optional dependencies
     * as real dependencies.
     *
     * @throws IOException
     *             thrown if one or several mandatory dependencies doesn't exists.
     */
    /*package*/ void resolvePluginDependencies() throws IOException {
        if (ENABLE_PLUGIN_DEPENDENCIES_VERSION_CHECK) {
            String requiredCoreVersion = getRequiredCoreVersion();
            if (requiredCoreVersion == null) {
                LOGGER.warning(shortName + " doesn't declare required core version.");
            } else {
                VersionNumber actualVersion = Jenkins.getVersion();
                if (actualVersion.isOlderThan(new VersionNumber(requiredCoreVersion))) {
                    versionDependencyError(Messages.PluginWrapper_obsoleteCore(Jenkins.getVersion().toString(), requiredCoreVersion), Jenkins.getVersion().toString(), requiredCoreVersion);
                }
            }

            String minimumJavaVersion = getMinimumJavaVersion();
            if (minimumJavaVersion != null) {
                VersionNumber actualVersion = JavaUtils.getCurrentJavaRuntimeVersionNumber();
                if (actualVersion.isOlderThan(new VersionNumber(minimumJavaVersion))) {
                    versionDependencyError(Messages.PluginWrapper_obsoleteJava(actualVersion.toString(), minimumJavaVersion), actualVersion.toString(), minimumJavaVersion);
                }
            }
        }
        // make sure dependencies exist
        for (Dependency d : dependencies) {
            PluginWrapper dependency = parent.getPlugin(d.shortName);
            if (dependency == null) {
                PluginWrapper failedDependency = NOTICE.getPlugin(d.shortName);
                if (failedDependency != null) {
                    dependencyErrors.put(Messages.PluginWrapper_failed_to_load_dependency(failedDependency.getLongName(), failedDependency.getVersion()), true);
                    break;
                } else {
                    dependencyErrors.put(Messages.PluginWrapper_missing(d.shortName, d.version), false);
                }
            } else {
                if (dependency.isActive()) {
                    if (isDependencyObsolete(d, dependency)) {
                        versionDependencyError(Messages.PluginWrapper_obsolete(dependency.getLongName(), dependency.getVersion(), d.version), dependency.getVersion(), d.version);
                    }
                } else {
                    if (isDependencyObsolete(d, dependency)) {
                        versionDependencyError(Messages.PluginWrapper_disabledAndObsolete(dependency.getLongName(), dependency.getVersion(), d.version), dependency.getVersion(), d.version);
                    } else {
                        dependencyErrors.put(Messages.PluginWrapper_disabled(dependency.getLongName()), false);
                    }
                }

            }
        }
        // add the optional dependencies that exists
        for (Dependency d : optionalDependencies) {
            PluginWrapper dependency = parent.getPlugin(d.shortName);
            if (dependency != null && dependency.isActive()) {
                if (isDependencyObsolete(d, dependency)) {
                    versionDependencyError(Messages.PluginWrapper_obsolete(dependency.getLongName(), dependency.getVersion(), d.version), dependency.getVersion(), d.version);
                } else {
                    dependencies.add(d);
                }
            }
        }
        if (!dependencyErrors.isEmpty()) {
            NOTICE.addPlugin(this);
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(Messages.PluginWrapper_failed_to_load_plugin(getLongName(), getVersion())).append(System.lineSeparator());
            for (Iterator<String> iterator = dependencyErrors.keySet().iterator(); iterator.hasNext(); ) {
                String dependencyError = iterator.next();
                messageBuilder.append(" - ").append(dependencyError);
                if (iterator.hasNext()) {
                    messageBuilder.append(System.lineSeparator());
                }
            }
            throw new IOException(messageBuilder.toString());
        }
    }

    private boolean isDependencyObsolete(Dependency d, PluginWrapper dependency) {
        return ENABLE_PLUGIN_DEPENDENCIES_VERSION_CHECK && dependency.getVersionNumber().isOlderThan(new VersionNumber(d.version));
    }

    /**
     * Called when there appears to be a core or plugin version which is too old for a stated dependency.
     * Normally records an error in {@link #dependencyErrors}.
     * But if one or both versions {@link #isSnapshot}, just issue a warning (JENKINS-52665).
     */
    private void versionDependencyError(String message, String actual, String minimum) {
        if (isSnapshot(actual) || isSnapshot(minimum)) {
            LOGGER.log(WARNING, "Suppressing dependency error in {0} v{1}: {2}", new Object[] {getLongName(), getVersion(), message});
        } else {
            dependencyErrors.put(message, false);
        }
    }

    /**
     * Similar to {@code org.apache.maven.artifact.ArtifactUtils.isSnapshot}.
     */
    static boolean isSnapshot(@Nonnull String version) {
        return version.contains("-SNAPSHOT") || version.matches(".+-[0-9]{8}.[0-9]{6}-[0-9]+");
    }

    /**
     * If the plugin has {@link #getUpdateInfo() an update},
     * returns the {@link hudson.model.UpdateSite.Plugin} object.
     *
     * @return
     *      This method may return null &mdash; for example,
     *      the user may have installed a plugin locally developed.
     */
    public UpdateSite.Plugin getUpdateInfo() {
        UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
        UpdateSite.Plugin p = uc.getPlugin(getShortName(), getVersionNumber());
        if(p!=null && p.isNewerThan(getVersion())) return p;
        return null;
    }
    
    /**
     * returns the {@link hudson.model.UpdateSite.Plugin} object, or null.
     */
    public UpdateSite.Plugin getInfo() {
        UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
        UpdateSite.Plugin p = uc.getPlugin(getShortName(), getVersionNumber());
        if (p != null) return p;
        return uc.getPlugin(getShortName());
    }

    /**
     * Returns true if this plugin has update in the update center.
     *
     * <p>
     * This method is conservative in the sense that if the version number is incomprehensible,
     * it always returns false.
     */
    @Exported
    public boolean hasUpdate() {
        return getUpdateInfo()!=null;
    }
    
    @Exported
    @Deprecated // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
    public boolean isPinned() {
        return false;
    }

    /**
     * Returns true if this plugin is deleted.
     *
     * The plugin continues to function in this session, but in the next session it'll disappear.
     */
    @Exported
    public boolean isDeleted() {
        return !archive.exists();
    }

    /**
     * Sort by short name.
     */
    public int compareTo(PluginWrapper pw) {
        return shortName.compareToIgnoreCase(pw.shortName);
    }

    /**
     * returns true if backup of previous version of plugin exists
     */
    @Exported
    public boolean isDowngradable() {
        return getBackupFile().exists();
    }

    /**
     * Where is the backup file?
     */
    public File getBackupFile() {
        return new File(Jenkins.getInstance().getRootDir(),"plugins/"+getShortName() + ".bak");
    }

    /**
     * returns the version of the backed up plugin,
     * or null if there's no back up.
     */
    @Exported
    public String getBackupVersion() {
        File backup = getBackupFile();
        if (backup.exists()) {
            try {
                try (JarFile backupPlugin = new JarFile(backup)) {
                    return backupPlugin.getManifest().getMainAttributes().getValue("Plugin-Version");
                }
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to get backup version from " + backup, e);
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Checks if this plugin is pinned and that's forcing us to use an older version than the bundled one.
     */
    @Deprecated // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
    public boolean isPinningForcingOldVersion() {
        return false;
    }

    @Extension
    public final static PluginWrapperAdministrativeMonitor NOTICE = new PluginWrapperAdministrativeMonitor();

    /**
     * Administrative Monitor for failed plugins
     */
    public static final class PluginWrapperAdministrativeMonitor extends AdministrativeMonitor {
        private final Map<String, PluginWrapper> plugins = new HashMap<>();

        void addPlugin(PluginWrapper plugin) {
            plugins.put(plugin.shortName, plugin);
        }

        public boolean isActivated() {
            return !plugins.isEmpty();
        }

        @Restricted(DoNotUse.class) // Jelly
        public boolean hasAnyDerivedDependencyErrors() {
            return plugins.values().stream().anyMatch(PluginWrapper::hasDerivedDependencyErrors);
        }

        @Override
        public String getDisplayName() {
            return Messages.PluginWrapper_PluginWrapperAdministrativeMonitor_DisplayName();
        }

        public Collection<PluginWrapper> getPlugins() {
            return plugins.values();
        }

        public PluginWrapper getPlugin(String shortName) {
            return plugins.get(shortName);
        }

        /**
         * Depending on whether the user said "dismiss" or "correct", send him to the right place.
         */
        public void doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
            if(req.hasParameter("correct")) {
                rsp.sendRedirect(req.getContextPath()+"/pluginManager");

            }
        }

        public static PluginWrapperAdministrativeMonitor get() {
            return AdministrativeMonitor.all().get(PluginWrapperAdministrativeMonitor.class);
        }
    }

    /**
     * The result of the disablement of a plugin and its dependants plugins.
     */
    public static class PluginDisableResult {
        private String plugin;
        private PluginDisableStatus status;
        private String message;
        private Set<PluginDisableResult> dependantsDisableStatus = new HashSet<>();

        public PluginDisableResult(String plugin) {
            this.plugin = plugin;
        }

        public PluginDisableResult(String plugin, PluginDisableStatus status, String message) {
            this.plugin = plugin;
            this.status = status;
            this.message = message;
        }

        public String getPlugin() {
            return plugin;
        }

        public PluginDisableStatus getStatus() {
            return status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PluginDisableResult that = (PluginDisableResult) o;
            return Objects.equals(plugin, that.plugin);
        }

        @Override
        public int hashCode() {
            return Objects.hash(plugin);
        }

        public void setStatus(PluginDisableStatus status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Set<PluginDisableResult> getDependantsDisableStatus() {
            return dependantsDisableStatus;
        }

        public void addDependantDisableStatus(PluginDisableResult dependantDisableStatus) {
            dependantsDisableStatus.add(dependantDisableStatus);
        }

    }

    /**
     * An enum to hold the status of a disabling action against a plugin. To do it more reader-friendly.
     */
    public enum PluginDisableStatus {
        NO_SUCH_PLUGIN,
        DISABLED,
        ALREADY_DISABLED,
        NOT_DISABLED_DEPENDANTS,
        ERROR_DISABLING
    }

    /**
     * The strategies defined for disabling a plugin.
     */
    public enum PluginDisableStrategy {
        NONE,
        MANDATORY,
        ALL;

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }

//
//
// Action methods
//
//
    @RequirePOST
    public HttpResponse doMakeEnabled() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        enable();
        return HttpResponses.ok();
    }

    @RequirePOST
    public HttpResponse doMakeDisabled() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        disable();
        return HttpResponses.ok();
    }

    @RequirePOST
    @Deprecated
    public HttpResponse doPin() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
        LOGGER.log(WARNING, "Call to pin plugin has been ignored. Plugin name: " + shortName);
        return HttpResponses.ok();
    }

    @RequirePOST
    @Deprecated
    public HttpResponse doUnpin() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
        LOGGER.log(WARNING, "Call to unpin plugin has been ignored. Plugin name: " + shortName);
        return HttpResponses.ok();
    }

    @RequirePOST
    public HttpResponse doDoUninstall() throws IOException {
        Jenkins jenkins = Jenkins.getActiveInstance();
        
        jenkins.checkPermission(Jenkins.ADMINISTER);
        archive.delete();

        // Redo who depends on who.
        jenkins.getPluginManager().resolveDependantPlugins();

        return HttpResponses.redirectViaContextPath("/pluginManager/installed");   // send back to plugin manager
    }


    private static final Logger LOGGER = Logger.getLogger(PluginWrapper.class.getName());

    /**
     * Name of the plugin manifest file (to help find where we parse them.)
     */
    public static final String MANIFEST_FILENAME = "META-INF/MANIFEST.MF";
}
