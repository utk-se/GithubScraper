package jenkins.mvn;

import hudson.Extension;
import hudson.model.PersistentDescriptor;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.tools.ToolConfigurationCategory;

import org.jenkinsci.Symbol;

import javax.annotation.Nonnull;

//as close as it gets to the global Maven Project configuration
@Extension(ordinal = 50) @Symbol("maven")
public class GlobalMavenConfig extends GlobalConfiguration  implements PersistentDescriptor {
    private SettingsProvider settingsProvider;
    private GlobalSettingsProvider globalSettingsProvider;

    @Override
    public @Nonnull ToolConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(ToolConfigurationCategory.class);
    }

    public void setGlobalSettingsProvider(GlobalSettingsProvider globalSettingsProvider) {
        this.globalSettingsProvider = globalSettingsProvider;
        save();
    }

    public void setSettingsProvider(SettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
        save();
    }

    public GlobalSettingsProvider getGlobalSettingsProvider() {
        return globalSettingsProvider != null ? globalSettingsProvider : new DefaultGlobalSettingsProvider();
    }

    public SettingsProvider getSettingsProvider() {
        return settingsProvider != null ? settingsProvider : new DefaultSettingsProvider();
    }

    public static @Nonnull GlobalMavenConfig get() {
        return GlobalConfiguration.all().getInstance(GlobalMavenConfig.class);
    }

}
