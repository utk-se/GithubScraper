package hudson.model;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Extension;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.xml.sax.SAXException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class DescriptorVisibilityFilterTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Rule public LoggerRule logger = new LoggerRule();

    @Test @Issue("JENKINS-40545")
    public void jenkins40545() throws Exception {
        logger.record("hudson.ExpressionFactory2$JexlExpression", Level.WARNING);
        logger.record("hudson.model.DescriptorVisibilityFilter", Level.WARNING);
        logger.capture(10);
        HtmlPage page = j.createWebClient().goTo("jenkins40545");
        assertThat(logger.getRecords(), not(emptyIterable()));
        for (LogRecord record : logger.getRecords()) {
            String message = record.getMessage();
            assertThat(message, allOf(
                    containsString("Descriptor list is null for context 'class hudson.model.DescriptorVisibilityFilterTest$Jenkins40545'"),
                    containsString("DescriptorVisibilityFilterTest/Jenkins40545/index.jelly"),
                    not(endsWith("NullPointerException"))
            ));
        }

        assertThat(page.getWebResponse().getContentAsString(), containsString("descriptors found: .")); // No output written from expression
    }

    @Test @Issue("JENKINS-49044")
    public void securityRealmAndAuthStrategyHidden() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);
        HtmlPage page = j.createWebClient().goTo("configureSecurity");
        String response = page.getWebResponse().getContentAsString();
        assertThat(response, not(containsString("TestSecurityRealm")));
        assertThat(response, not(containsString("TestAuthStrategy")));
    }

    public static final class TestSecurityRealm extends SecurityRealm {

        @Override
        public SecurityComponents createSecurityComponents() { return null; }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
            @Nonnull
            @Override
            public String getDisplayName() {
                return "TestSecurityRealm";
            }
        }

        @TestExtension
        public static final class HideDescriptor extends DescriptorVisibilityFilter {
            @Override
            public boolean filter(@CheckForNull Object context, @Nonnull Descriptor descriptor) {
                return !(descriptor instanceof DescriptorImpl);
            }
        }
    }

    public static final class TestAuthStrategy extends AuthorizationStrategy {

        @Nonnull
        @Override
        public ACL getRootACL() { return null; }

        @Nonnull
        @Override
        public Collection<String> getGroups() { return null; }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
            @Nonnull
            @Override
            public String getDisplayName() {
                return "TestAuthStrategy";
            }
        }

        @TestExtension
        public static final class HideDescriptor extends DescriptorVisibilityFilter {
            @Override
            public boolean filter(@CheckForNull Object context, @Nonnull Descriptor descriptor) {
                return !(descriptor instanceof DescriptorImpl);
            }
        }
    }

    @TestExtension("jenkins40545")
    public static final class Jenkins40545 implements UnprotectedRootAction {

        @Override public String getIconFileName() {
            return "notepad.png";
        }

        @Override public String getDisplayName() {
            return "jenkins40545";
        }

        @Override public String getUrlName() {
            return "jenkins40545";
        }
    }
}
