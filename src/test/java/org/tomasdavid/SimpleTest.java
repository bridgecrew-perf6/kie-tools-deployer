package org.tomasdavid;

import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.api.model.ProjectRequestBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.Test;

public class SimpleTest {

    private OpenShiftClient openShiftClient;

    @Test
    public void justPass() {

        final String projectName = "tdavid-test";
        final String openShiftUrl = System.getProperty("openshift.url");
        final String openShiftUser = System.getProperty("openshift.user");
        final String openShiftPassword = System.getProperty("openshift.password");

        openShiftClient = createOpenShiftClient(openShiftUrl, openShiftUser, openShiftPassword);

        createProject(projectName);

        final String corsProxyAppName = "cors-proxy";
        final String corsProxyServiceName = "cors-proxy-service";
        final String corsProxyRouteName = "cors-proxy-route";
        final String corsProxyImageUrl = "quay.io/kogito_tooling_bot/cors-proxy-image:latest";
        final int corsProxyPort = 8080;

        createApp(corsProxyAppName, projectName, createContainer(corsProxyAppName, corsProxyImageUrl));
        createService(corsProxyServiceName, projectName, corsProxyAppName, corsProxyPort);
        Route corsProxyRoute = createRoute(corsProxyRouteName, projectName, corsProxyServiceName);

        final String extendedServicesAppName = "extended-services";
        final String extendedServicesServiceName = "extended-services-service";
        final String extendedServicesRouteName = "extended-services-route";
        final String extendedServicesImageUrl = "quay.io/kogito_tooling_bot/kie-sandbox-extended-services-image:latest";
        final int extendedServicesPort = 21345;

        createApp(extendedServicesAppName, projectName, createContainer(extendedServicesAppName, extendedServicesImageUrl));
        createService(extendedServicesServiceName, projectName, extendedServicesAppName, extendedServicesPort);
        Route extendedServicesRoute = createRoute(extendedServicesRouteName, projectName, extendedServicesServiceName);

        final String kieSanboxAppName = "kie-sandbox";
        final String kieSanboxServiceName = "kie-sandbox-service";
        final String kieSanboxRouteName = "kie-sandbox-route";
        final String kieSanboxImageUrl = "quay.io/kogito_tooling_bot/kie-sandbox-image:latest";
        final int kieSanboxPort = 8080;

        SimpleEntry corsProxyUrl = new SimpleEntry<>("CORS_PROXY_URL", "https://" + corsProxyRoute.getSpec().getHost());
        SimpleEntry extendedServicesUrl = new SimpleEntry<>("KIE_SANDBOX_EXTENDED_SERVICES_URL", "https://" + extendedServicesRoute.getSpec().getHost());

        createApp(kieSanboxAppName, projectName, createContainer(kieSanboxAppName, kieSanboxImageUrl, corsProxyUrl, extendedServicesUrl));
        createService(kieSanboxServiceName, projectName, kieSanboxAppName, kieSanboxPort);
        Route kieSandboxRoute = createRoute(kieSanboxRouteName, projectName, kieSanboxServiceName);

        String kieSandboxUrl = kieSandboxRoute.getSpec().getHost();
        System.out.println("https://" + kieSandboxUrl);
    }

    public OpenShiftClient createOpenShiftClient(String openShiftUrl, String openShiftUser, String openShiftPassword) {
        return new DefaultOpenShiftClient(
                new ConfigBuilder()
                        .withMasterUrl(openShiftUrl)
                        .withUsername(openShiftUser)
                        .withPassword(openShiftPassword)
                        .build()
        );
    }

    public void createProject(String projectName) {
        if (openShiftClient.projects().list().getItems().stream().noneMatch(p -> p.getMetadata().getName().equals(projectName))) {
            openShiftClient.projectrequests().create(
                    new ProjectRequestBuilder()
                            .withNewMetadata()
                            .withName(projectName)
                            .endMetadata()
                            .build()
            );
        } else {
            System.out.format("Project %s already exists.", projectName);
        }
    }

    public Container createContainer(String appName, String imageUrl, SimpleEntry<String, String>... envVars) {
        ContainerBuilder containerBuilder = new ContainerBuilder()
                .withName(appName)
                .withImage(imageUrl);

        Stream.of(envVars).forEach(env -> containerBuilder.addNewEnv().withName(env.getKey()).withValue(env.getValue()).endEnv());

        return containerBuilder.build();
    }

    public void createApp(String appName, String projectName, Container container) {
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(appName)
                .addToLabels("app", appName)
                .endMetadata()
                .withNewSpec()
                .withNewSelector()
                .addToMatchLabels("app", appName)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("app", appName)
                .endMetadata()
                .withNewSpec()
                .withContainers(container)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        openShiftClient.apps()
                .deployments()
                .inNamespace(projectName)
                .createOrReplace(deployment);
    }

    public void createService(String serviceName, String projectName, String appName, int port) {
        Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(serviceName)
                .endMetadata()
                .withNewSpec()
                .addToSelector("app", appName)
                .addNewPort()
                .withProtocol("TCP")
                .withPort(port)
                .withTargetPort(new IntOrString(port))
                .endPort()
                .endSpec()
                .build();

        openShiftClient.services()
                .inNamespace(projectName)
                .createOrReplace(service);
    }

    public Route createRoute(String routeName, String projectName, String serviceName) {
        Route route = new RouteBuilder()
                .withNewMetadata()
                .withName(routeName)
                .endMetadata()
                .withNewSpec()
                .withNewTo()
                .withName(serviceName)
                .endTo()
                .withNewTls()
                .withTermination("Edge")
                .withInsecureEdgeTerminationPolicy("None")
                .endTls()
                .endSpec()
                .build();

        return openShiftClient.routes()
                .inNamespace(projectName)
                .createOrReplace(route);
    }
}
