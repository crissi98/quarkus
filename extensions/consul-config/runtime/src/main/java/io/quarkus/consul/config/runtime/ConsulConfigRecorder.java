package io.quarkus.consul.config.runtime;

import java.util.Collections;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.jboss.logging.Logger;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class ConsulConfigRecorder {

    private static final Logger log = Logger.getLogger(ConsulConfigRecorder.class);

    public RuntimeValue<ConfigSourceProvider> configSources(ConsulConfig consulConfig,
            ConsulConfig.ConsulRefreshConfig consulRefreshConfig) {
        if (!consulConfig.enabled) {
            log.debug(
                    "No attempt will be made to obtain configuration from Consul because the functionality has been disabled via configuration");
            return emptyRuntimeValue();
        }

        return new RuntimeValue<>(
                new ConsulConfigSourceProvider(consulConfig, consulRefreshConfig));
    }

    private RuntimeValue<ConfigSourceProvider> emptyRuntimeValue() {
        return new RuntimeValue<>(new EmptyConfigSourceProvider());
    }

    public Handler<RoutingContext> handler() {
        return new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                ConfigRefreshUtil.INSTANCE.refreshConfig();
                routingContext.response().setStatusCode(204);
                routingContext.response().end();
            }
        };
    }

    private static class EmptyConfigSourceProvider implements ConfigSourceProvider {

        @Override
        public Iterable<ConfigSource> getConfigSources(ClassLoader forClassLoader) {
            return Collections.emptyList();
        }
    }
}
