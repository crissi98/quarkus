package io.quarkus.consul.config.runtime;

import java.util.ArrayList;
import java.util.List;

public class ConfigRefreshUtil {

    static final ConfigRefreshUtil INSTANCE = new ConfigRefreshUtil();

    private final List<ResponseConfigSourceUtil.ConsulRefreshableConfigSource> refreshableConfigSources;

    private ConfigRefreshUtil() {
        refreshableConfigSources = new ArrayList<>();
    }

    void addConfigSource(ResponseConfigSourceUtil.ConsulRefreshableConfigSource configSource) {
        refreshableConfigSources.add(configSource);
    }

    public void refreshConfig() {
        for (ResponseConfigSourceUtil.ConsulRefreshableConfigSource configSource : refreshableConfigSources) {
            configSource.refresh();
        }
    }
}
