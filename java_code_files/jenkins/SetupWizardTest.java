/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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
package jenkins.install;

import com.gargoylesoftware.htmlunit.Page;
import hudson.FilePath;
import hudson.model.UpdateSite;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import jenkins.AgentProtocolTest;
import jenkins.slaves.DeprecatedAgentProtocolMonitor;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.Matchers.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertFalse;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

/**
 * Tests of {@link SetupWizard}.
 * @author Oleg Nenashev
 */
public class SetupWizardTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Rule
    public TemporaryFolder tmpdir = new TemporaryFolder();
    
    @Before 
    public void initSetupWizard() throws IOException, InterruptedException {
        final SetupWizard wizard = j.jenkins.getSetupWizard();
        wizard.init(true);
        
        // Retrieve admin credentials
        final FilePath adminPassFile = wizard.getInitialAdminPasswordFile();
        ByteArrayOutputStream ostream = new ByteArrayOutputStream();
        adminPassFile.copyTo(ostream);
        final String password = ostream.toString();
    }
    
    @Test
    public void shouldReturnPluginListsByDefault() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        // TODO: This is a hack, wc.login does not work with the form
        j.jenkins.setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
        j.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);
        // wc.setCredentialsProvider(adminCredentialsProvider);
        // wc.login("admin");
        
        String response = jsonRequest(wc, "setupWizard/platformPluginList");
        assertThat("Missing plugin is suggestions ", response, containsString("active-directory"));
        assertThat("Missing category is suggestions ", response, containsString("Pipelines and Continuous Delivery"));
    }
    
    @Test
    @Issue("JENKINS-34833")
    public void shouldReturnUpdateSiteJSONIfSpecified() throws Exception {
        // Init the update site
        CustomUpdateSite us = new CustomUpdateSite(tmpdir.getRoot());
        us.init();
        j.jenkins.getUpdateCenter().getSites().add(us);
        
        // Prepare the connection
        JenkinsRule.WebClient wc = j.createWebClient();
        // TODO: This is a hack, wc.login does not work with the form
        j.jenkins.setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
        j.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);
        // wc.setCredentialsProvider(adminCredentialsProvider);
        // wc.login("admin");
        
        String response = jsonRequest(wc, "setupWizard/platformPluginList");
        assertThat("Missing plugin is suggestions ", response, containsString("antisamy-markup-formatter"));
        assertThat("Missing category is suggestions ", response, containsString("Organization and Administration"));
        assertThat("Missing plugin is suggestions ", response, not(containsString("active-directory")));
        assertThat("Missing category is suggestions ", response, not(containsString("Pipelines and Continuous Delivery")));
    }
    
    @Test
    public void shouldProhibitAccessToPluginListWithoutAuth() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.assertFails("setupWizard/platformPluginList", 403);
        wc.assertFails("setupWizard/createAdminUser", 403);
        wc.assertFails("setupWizard/completeInstall", 403);
    }

    //TODO: The test randomly fails on Jenkins CI
    // Oleg Nenashev: I am not able to reproduce it
    @Test
    @Issue("JENKINS-45841")
    @Ignore
    public void shouldDisableUnencryptedProtocolsByDefault() throws Exception {
        AgentProtocolTest.assertProtocols(j.jenkins, true, 
                "Encrypted JNLP4-protocols protocol should be enabled", "JNLP4-connect");
        AgentProtocolTest.assertProtocols(j.jenkins, false, 
                "Non-encrypted JNLP protocols should be disabled by default", 
                "JNLP-connect", "JNLP2-connect", "CLI-connect");
        // The CI test fails here, presumably due to the CLI protocols.
        AgentProtocolTest.assertMonitorNotActive(j);
    }
        
    private String jsonRequest(JenkinsRule.WebClient wc, String path) throws Exception {
        // Try to call the actions method to retrieve the data
        final Page res;
        try {
            res = wc.goTo(path, null);
        } catch (Exception ex) {
            ex.getMessage();
            throw new AssertionError("Cannot get a response from " + path, ex);
        }
        final String responseJSON = res.getWebResponse().getContentAsString();
        return responseJSON;
    }
    
    private static final class CustomUpdateSite extends UpdateSite {
        
        private final File tmpdir;
        
        CustomUpdateSite(File tmpdir) throws MalformedURLException {
            super("custom-uc", tmpdir.toURI().toURL().toString() + "update-center.json");
            this.tmpdir = tmpdir;
        }

        public void init() throws IOException {
            File newFile = new File(tmpdir, "platform-plugins.json");
            FileUtils.write(newFile, "[ { "
                    + "\"category\":\"Organization and Administration\", "
                    + "\"plugins\": [ { \"name\": \"dashboard-view\"}, { \"name\": \"antisamy-markup-formatter\" } ]"
                    + "} ]");
        }
        
    }
}
