/*
 * The MIT License
 *
 * Copyright 2015 Johannes Ernst http://upon2020.com/
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
package jenkins.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import jenkins.util.io.OnMaster;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Centralizes calls to {@link System#getProperty(String)} and related calls.
 * This allows us to get values not just from environment variables but also from
 * the {@link ServletContext}, so properties like {@code hudson.DNSMultiCast.disabled}
 * can be set in {@code context.xml} and the app server's boot script does not
 * have to be changed.
 *
 * <p>This should be used to obtain hudson/jenkins "app"-level parameters
 * (e.g. {@code hudson.DNSMultiCast.disabled}), but not for system parameters
 * (e.g. {@code os.name}).
 *
 * <p>If you run multiple instances of Jenkins in the same virtual machine and wish
 * to obtain properties from {@code context.xml}, make sure these Jenkins instances use
 * different ClassLoaders. Tomcat, for example, does this automatically. If you do
 * not use different ClassLoaders, the values of properties specified in
 * {@code context.xml} is undefined.
 *
 * <p>Property access is logged on {@link Level#CONFIG}. Note that some properties
 * may be accessed by Jenkins before logging is configured properly, so early access to
 * some properties may not be logged.
 *
 * <p>While it looks like it on first glance, this cannot be mapped to {@link EnvVars},
 * because {@link EnvVars} is only for build variables, not Jenkins itself variables.
 *
 * @author Johannes Ernst
 * @since 2.4
 */
//TODO: Define a correct design of this engine later. Should be accessible in libs (remoting, stapler) and Jenkins modules too
@Restricted(NoExternalUse.class)
public class SystemProperties {

    // declared in WEB-INF/web.xml
    public static final class Listener implements ServletContextListener, OnMaster {

        /**
         * The ServletContext to get the "init" parameters from.
         */
        @CheckForNull
        private static ServletContext theContext;

        /**
         * Called by the servlet container to initialize the {@link ServletContext}.
         */
        @Override
        @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
                justification = "Currently Jenkins instance may have one ond only one context")
        public void contextInitialized(ServletContextEvent event) {
            theContext = event.getServletContext();
        }

        @Override
        public void contextDestroyed(ServletContextEvent event) {
            theContext = null;
        }

    }

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SystemProperties.class.getName());

    private SystemProperties() {}

    /**
     * Gets the system property indicated by the specified key.
     * This behaves just like {@link System#getProperty(java.lang.String)}, except that it
     * also consults the {@link ServletContext}'s "init" parameters.
     * 
     * @param      key   the name of the system property.
     * @return     the string value of the system property,
     *             or {@code null} if there is no property with that key.
     *
     * @exception  NullPointerException if {@code key} is {@code null}.
     * @exception  IllegalArgumentException if {@code key} is empty.
     */
    @CheckForNull
    public static String getString(String key) {
        String value = System.getProperty(key); // keep passing on any exceptions
        if (value != null) {
            if (LOGGER.isLoggable(Level.CONFIG)) {
                LOGGER.log(Level.CONFIG, "Property (system): {0} => {1}", new Object[] {key, value});
            }
            return value;
        }
        
        value = tryGetValueFromContext(key);
        if (value != null) {
            if (LOGGER.isLoggable(Level.CONFIG)) {
                LOGGER.log(Level.CONFIG, "Property (context): {0} => {1}", new Object[]{key, value});
            }
            return value;
        }
        
        if (LOGGER.isLoggable(Level.CONFIG)) {
            LOGGER.log(Level.CONFIG, "Property (not found): {0} => {1}", new Object[] {key, value});
        }
        return null;
    }

    /**
     * Gets the system property indicated by the specified key, or a default value.
     * This behaves just like {@link System#getProperty(java.lang.String, java.lang.String)}, except
     * that it also consults the {@link ServletContext}'s "init" parameters.
     * 
     * @param      key   the name of the system property.
     * @param      def   a default value.
     * @return     the string value of the system property,
     *             or {@code null} if the property is missing and the default value is {@code null}.
     *
     * @exception  NullPointerException if {@code key} is {@code null}.
     * @exception  IllegalArgumentException if {@code key} is empty.
     */
    public static String getString(String key, @CheckForNull String def) {
        return getString(key, def, Level.CONFIG);
    }

    /**
     * Gets the system property indicated by the specified key, or a default value.
     * This behaves just like {@link System#getProperty(java.lang.String, java.lang.String)}, except
     * that it also consults the {@link ServletContext}'s "init" parameters.
     *
     * @param      key   the name of the system property.
     * @param      def   a default value.
     * @param      logLevel the level of the log if the provided key is not found.
     * @return     the string value of the system property,
     *             or {@code null} if the property is missing and the default value is {@code null}.
     *
     * @exception  NullPointerException if {@code key} is {@code null}.
     * @exception  IllegalArgumentException if {@code key} is empty.
     */
    public static String getString(String key, @CheckForNull String def, Level logLevel) {
        String value = System.getProperty(key); // keep passing on any exceptions
        if (value != null) {
            if (LOGGER.isLoggable(logLevel)) {
                LOGGER.log(logLevel, "Property (system): {0} => {1}", new Object[] {key, value});
            }
            return value;
        } 
        
        value = tryGetValueFromContext(key);
        if (value != null) {
            if (LOGGER.isLoggable(logLevel)) {
                LOGGER.log(logLevel, "Property (context): {0} => {1}", new Object[]{key, value});
            }
            return value;
        }
        
        value = def;
        if (LOGGER.isLoggable(logLevel)) {
            LOGGER.log(logLevel, "Property (default): {0} => {1}", new Object[] {key, value});
        }
        return value;
    }

    /**
      * Returns {@code true} if the system property
      * named by the argument exists and is equal to the string
      * {@code "true"}. If the system property does not exist, return
      * {@code "false"}. if a property by this name exists in the {@link ServletContext}
      * and is equal to the string {@code "true"}.
      * 
      * This behaves just like {@link Boolean#getBoolean(java.lang.String)}, except that it
      * also consults the {@link ServletContext}'s "init" parameters.
      * 
      * @param   name   the system property name.
      * @return  the {@code boolean} value of the system property.
      */  
    public static boolean getBoolean(String name) {
        return getBoolean(name, false);
    }

    /**
      * Returns {@code true} if the system property
      * named by the argument exists and is equal to the string
      * {@code "true"}, or a default value. If the system property does not exist, return
      * {@code "true"} if a property by this name exists in the {@link ServletContext}
      * and is equal to the string {@code "true"}. If that property does not
      * exist either, return the default value.
      * 
      * This behaves just like {@link Boolean#getBoolean(java.lang.String)} with a default
      * value, except that it also consults the {@link ServletContext}'s "init" parameters.
      * 
      * @param   name   the system property name.
      * @param   def   a default value.
      * @return  the {@code boolean} value of the system property.
      */
    public static boolean getBoolean(String name, boolean def) {
        String v = getString(name);
       
        if (v != null) {
            return Boolean.parseBoolean(v);
        }
        return def;
    }

    /**
     * Returns {@link Boolean#TRUE} if the named system property exists and is equal to the string {@code "true}
     * (ignoring case), returns {@link Boolean#FALSE} if the system property exists and doesn't equal {@code "true}
     * otherwise returns {@code null} if the named system property does not exist.
     *
     * @param name the system property name.
     * @return {@link Boolean#TRUE}, {@link Boolean#FALSE} or {@code null}
     * @since 2.16
     */
    @CheckForNull
    public static Boolean optBoolean(String name) {
        String v = getString(name);
        return v == null ? null : Boolean.parseBoolean(v);
    }
    
    /**
      * Determines the integer value of the system property with the
      * specified name.
      * 
      * This behaves just like {@link Integer#getInteger(java.lang.String)}, except that it
      * also consults the {@link ServletContext}'s "init" parameters.
      * 
      * @param   name property name.
      * @return  the {@code Integer} value of the property.
      */
    @CheckForNull
    public static Integer getInteger(String name) {
        return getInteger(name, null);
    }

    /**
     * Determines the integer value of the system property with the
     * specified name, or a default value.
     *
     * This behaves just like <code>Integer.getInteger(String,Integer)</code>, except that it
     * also consults the <code>ServletContext</code>'s "init" parameters. If neither exist,
     * return the default value.
     *
     * @param   name property name.
     * @param   def   a default value.
     * @return  the {@code Integer} value of the property.
     *          If the property is missing, return the default value.
     *          Result may be {@code null} only if the default value is {@code null}.
     */
    public static Integer getInteger(String name, Integer def) {
        return getInteger(name, def, Level.CONFIG);
    }

    /**
      * Determines the integer value of the system property with the
      * specified name, or a default value.
      * 
      * This behaves just like <code>Integer.getInteger(String,Integer)</code>, except that it
      * also consults the <code>ServletContext</code>'s "init" parameters. If neither exist,
      * return the default value. 
      * 
      * @param   name property name.
      * @param   def   a default value.
      * @param   logLevel the level of the log if the provided system property name cannot be decoded into Integer.
      * @return  the {@code Integer} value of the property.
      *          If the property is missing, return the default value.
      *          Result may be {@code null} only if the default value is {@code null}.
      */
    public static Integer getInteger(String name, Integer def, Level logLevel) {
        String v = getString(name);
       
        if (v != null) {
            try {
                return Integer.decode(v);
            } catch (NumberFormatException e) {
                // Ignore, fallback to default
                if (LOGGER.isLoggable(logLevel)) {
                    LOGGER.log(logLevel, "Property. Value is not integer: {0} => {1}", new Object[] {name, v});
                }
            }
        }
        return def;
    }
    
    /**
      * Determines the long value of the system property with the
      * specified name.
      * 
      * This behaves just like {@link Long#getLong(java.lang.String)}, except that it
      * also consults the {@link ServletContext}'s "init" parameters.
      * 
      * @param   name property name.
      * @return  the {@code Long} value of the property.
      */
    @CheckForNull
    public static Long getLong(String name) {
        return getLong(name, null);
    }

    /**
     * Determines the integer value of the system property with the
     * specified name, or a default value.
     *
     * This behaves just like <code>Long.getLong(String,Long)</code>, except that it
     * also consults the <code>ServletContext</code>'s "init" parameters. If neither exist,
     * return the default value.
     *
     * @param   name property name.
     * @param   def   a default value.
     * @return  the {@code Long} value of the property.
     *          If the property is missing, return the default value.
     *          Result may be {@code null} only if the default value is {@code null}.
     */
    public static Long getLong(String name, Long def) {
        return getLong(name, def, Level.CONFIG);
    }

    /**
      * Determines the integer value of the system property with the
      * specified name, or a default value.
      * 
      * This behaves just like <code>Long.getLong(String,Long)</code>, except that it
      * also consults the <code>ServletContext</code>'s "init" parameters. If neither exist,
      * return the default value. 
      * 
      * @param   name property name.
      * @param   def   a default value.
      * @param   logLevel the level of the log if the provided system property name cannot be decoded into Long.
      * @return  the {@code Long} value of the property.
      *          If the property is missing, return the default value.
      *          Result may be {@code null} only if the default value is {@code null}.
      */
    public static Long getLong(String name, Long def, Level logLevel) {
        String v = getString(name);
       
        if (v != null) {
            try {
                return Long.decode(v);
            } catch (NumberFormatException e) {
                // Ignore, fallback to default
                if (LOGGER.isLoggable(logLevel)) {
                    LOGGER.log(logLevel, "Property. Value is not long: {0} => {1}", new Object[] {name, v});
                }
            }
        }
        return def;
    }

    @CheckForNull
    private static String tryGetValueFromContext(String key) {
        if (!JenkinsJVM.isJenkinsJVM()) {
            return null;
        }
        return doTryGetValueFromContext(key);
    }

    private static String doTryGetValueFromContext(String key) {
        if (StringUtils.isNotBlank(key) && Listener.theContext != null) {
            try {
                String value = Listener.theContext.getInitParameter(key);
                if (value != null) {
                    return value;
                }
            } catch (SecurityException ex) {
                // Log exception and go on
                LOGGER.log(Level.CONFIG, "Access to the property {0} is prohibited", key);
            }
        }
        return null;
    }

}
