package io.quarkus.consul.config;

import javax.inject.Inject;

import io.quarkus.consul.config.runtime.ConsulConfig;
import io.quarkus.consul.config.runtime.ConsulConfigRecorder;
import io.quarkus.consul.config.runtime.Response;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationSourceValueBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class ConsulConfigProcessor {

    @Inject
    LaunchModeBuildItem launch;

    @BuildStep
    public void feature(BuildProducer<FeatureBuildItem> feature) {
        feature.produce(new FeatureBuildItem(Feature.CONSUL_CONFIG));
    }

    @BuildStep
    public void enableSsl(BuildProducer<ExtensionSslNativeSupportBuildItem> extensionSslNativeSupport) {
        extensionSslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(Feature.CONSUL_CONFIG));
    }

    @BuildStep
    public void registerForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, false, false, Response.class));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public RunTimeConfigurationSourceValueBuildItem configure(ConsulConfigRecorder recorder,
            ConsulConfig consulConfig, ConsulConfig.ConsulRefreshConfig consulRefreshConfig) {
        return new RunTimeConfigurationSourceValueBuildItem(
                recorder.configSources(consulConfig, consulRefreshConfig));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void enableRefresh(ConsulConfigRecorder recorder,
            ConsulConfig.ConsulRefreshConfig consulDeploymentConfig,
            BuildProducer<RouteBuildItem> routes,
            BuildProducer<NotFoundPageDisplayableEndpointBuildItem> displayableEndpoints) {
        if (consulDeploymentConfig.enabled) {
            Handler<RoutingContext> handler = recorder.handler();
            if (!consulDeploymentConfig.path.startsWith("/")) {
                throw new IllegalArgumentException("Path must start with /");
            }
            routes.produce(new RouteBuildItem(consulDeploymentConfig.path, handler));
            if (launch.getLaunchMode().isDevOrTest()) {
                displayableEndpoints.produce(new NotFoundPageDisplayableEndpointBuildItem(consulDeploymentConfig.path));
            }
        }

    }
}
