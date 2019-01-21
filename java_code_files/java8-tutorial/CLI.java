package jenkins;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import java.io.IOException;
import javax.annotation.Nonnull;

import hudson.model.PersistentDescriptor;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Kill switch to disable the CLI-over-Remoting system.
 *
 * Marked as no external use because the CLI subsystem is nearing EOL.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
@Extension @Symbol("remotingCLI")
public class CLI extends GlobalConfiguration implements PersistentDescriptor {

    /**
     * Supersedes {@link #isEnabled} if set.
     * @deprecated Use {@link #setEnabled} instead.
     */
    @Deprecated
    public static boolean DISABLED = Boolean.getBoolean(CLI.class.getName()+".disabled");

    @Nonnull
    public static CLI get() {
        return GlobalConfiguration.all().getInstance(CLI.class);
    }
    
    private boolean enabled = true; // historical default, but overridden in SetupWizard

    @Override
    public @Nonnull GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public boolean isEnabled() {
        return enabled && !DISABLED;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    @Extension @Symbol("remotingCLI")
    public static class WarnWhenEnabled extends AdministrativeMonitor {

        public WarnWhenEnabled() {
            super(CLI.class.getName());
        }

        @Override
        public String getDisplayName() {
            return "Remoting over CLI";
        }

        @Override
        public boolean isActivated() {
            return CLI.get().isEnabled();
        }

        @RequirePOST
        public HttpResponse doAct(@QueryParameter String no) throws IOException {
            if (no == null) {
                CLI.get().setEnabled(false);
            } else {
                disable(true);
            }
            return HttpResponses.redirectViaContextPath("manage");
        }

    }

}
