package io.quarkus.consul.config.runtime;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import io.smallrye.config.common.AbstractConfigSource;

class ResponseConfigSourceUtil {

    private static final Logger log = Logger.getLogger(ResponseConfigSourceUtil.class);

    private static final int ORDINAL = 270; // this is higher than the file system or jar ordinals, but lower than env vars

    public ConfigSource toConfigSource(Response response, ValueType valueType, Optional<String> prefix,
            ConsulConfigGateway consulConfigGateway, Optional<Duration> ttl, boolean refreshEnabled) {
        if (log.isDebugEnabled()) {
            log.debug("Attempting to convert data of key " + " '" + response.getKey()
                    + "' to a list of ConfigSource objects");
        }

        String keyWithoutPrefix = keyWithoutPrefix(response, prefix);

        ConfigSource result;
        if (valueType == ValueType.RAW) {
            result = new ConsulSingleValueConfigSource(keyWithoutPrefix, response, ORDINAL);
        } else if (valueType == ValueType.PROPERTIES) {
            result = new ConsulPropertiesConfigSource(keyWithoutPrefix, response, ORDINAL);
        } else {
            throw new IllegalArgumentException("Consul config value type '" + valueType + "' not supported");
        }

        if (ttl.isPresent()) {
            result = new ConsulTTLConfigSource(response.getKey(), ORDINAL, ((ConsulMapConfigSource) result),
                    consulConfigGateway, ttl.get());
        }
        if (refreshEnabled) {
            result = new ConsulRefreshableConfigSource(response.getKey(), ORDINAL, ((ConsulMapConfigSource) result),
                    consulConfigGateway);
            ConfigRefreshUtil.INSTANCE.addConfigSource(((ConsulRefreshableConfigSource) result));
        }

        log.debug("Done converting data of key '" + response.getKey() + "' into a ConfigSource");
        return result;
    }

    private String keyWithoutPrefix(Response response, Optional<String> prefix) {
        return prefix.isPresent() ? response.getKey().replace(prefix.get() + "/", "") : response.getKey();
    }

    private static abstract class ConsulMapConfigSource extends AbstractConfigSource {

        protected Map<String, String> properties;
        protected final String keyWithoutPrefix;

        public ConsulMapConfigSource(String name, String keyWithoutPrefix, Response response, int ordinal) {
            super(name, ordinal);
            this.keyWithoutPrefix = keyWithoutPrefix;
            loadPropertiesFromResponse(response);
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public Set<String> getPropertyNames() {
            return properties.keySet();
        }

        @Override
        public String getValue(String propertyName) {
            return properties.get(propertyName);
        }

        public String getKeyWithoutPrefix() {
            return keyWithoutPrefix;
        }

        public abstract void loadPropertiesFromResponse(Response response);
    }

    private static final class ConsulSingleValueConfigSource extends ConsulMapConfigSource {

        private static final String NAME_PREFIX = "ConsulSingleValueConfigSource[key=";

        public ConsulSingleValueConfigSource(String key, Response response, int ordinal) {
            super(NAME_PREFIX + key + "]", key, response, ordinal);
        }

        private static String effectiveKey(String key) {
            return key.replace('/', '.');
        }

        @Override
        public void loadPropertiesFromResponse(Response response) {
            properties = Collections.singletonMap(effectiveKey(keyWithoutPrefix), response.getDecodedValue());
        }
    }

    private static class ConsulPropertiesConfigSource extends ConsulMapConfigSource {

        private static final String NAME_FORMAT = "ConsulPropertiesConfigSource[key=%s]";

        ConsulPropertiesConfigSource(String key, Response response, int ordinal) {
            super(String.format(NAME_FORMAT, key), key, response, ordinal);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public void loadPropertiesFromResponse(Response response) {
            try (StringReader br = new StringReader(response.getDecodedValue())) {
                final Properties properties = new Properties();
                properties.load(br);
                this.properties = (Map<String, String>) (Map) properties;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static class ConsulRefreshableConfigSource extends AbstractConfigSource {

        private static final String NAME_FORMAT = "ConsulRefreshableConfigSource[key=%s]";

        protected ConsulMapConfigSource configSource;
        private ConsulConfigGateway consulConfigGateway;
        private String key;

        public ConsulRefreshableConfigSource(String key, int ordinal, ConsulMapConfigSource configSource,
                ConsulConfigGateway consulConfigGateway) {
            super(String.format(NAME_FORMAT, configSource.getKeyWithoutPrefix()), ordinal);
            this.configSource = configSource;
            this.consulConfigGateway = consulConfigGateway;
            this.key = key;
        }

        protected Optional<Response> getResponse() throws IOException {
            return consulConfigGateway.getValue(key);
        }

        @Override
        public Set<String> getPropertyNames() {
            return configSource.getPropertyNames();
        }

        @Override
        public Map<String, String> getProperties() {
            return configSource.getProperties();
        }

        @Override
        public String getValue(String propertyName) {
            return configSource.getValue(propertyName);
        }

        public void refresh() {
            try {
                configSource.loadPropertiesFromResponse(getResponse().orElseThrow(RuntimeException::new));
            } catch (IOException | RuntimeException e) {
                e.printStackTrace();
                log.warn("An error occurred refreshing config from consul. Continue to use old config");
            }
        }
    }

    private static class ConsulTTLConfigSource extends ConsulRefreshableConfigSource {

        private final Duration ttl;
        private LocalDateTime lastUpdated;

        public ConsulTTLConfigSource(String key, int ordinal, ConsulMapConfigSource configSource,
                ConsulConfigGateway consulConfigGateway, Duration ttl) {
            super(key, ordinal, configSource, consulConfigGateway);
            this.ttl = ttl;
            lastUpdated = LocalDateTime.now();
        }

        @Override
        public Set<String> getPropertyNames() {
            refresh();
            return super.getPropertyNames();
        }

        @Override
        public Map<String, String> getProperties() {
            refresh();
            return super.getProperties();
        }

        @Override
        public String getValue(String propertyName) {
            refresh();
            return super.getValue(propertyName);
        }

        @Override
        public void refresh() {
            if (Duration.between(LocalDateTime.now(), lastUpdated).plus(ttl).isNegative()) {
                super.refresh();
                lastUpdated = LocalDateTime.now();
            }
        }
    }

}
