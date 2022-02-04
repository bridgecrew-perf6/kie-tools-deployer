package org.tomasdavid.deployer;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectRequestBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

public class KieToolsDeployer {

    private static final String
            CORS_PROXY_APP_NAME = "cors-proxy",
            CORS_PROXY_IMAGE_URL = "quay.io/kogito_tooling_bot/cors-proxy-image:latest",
            EXTENDED_SERVICES_APP_NAME = "extended-services",
            EXTENDED_SERVICES_IMAGE_URL = "quay.io/kogito_tooling_bot/kie-sandbox-extended-services-image:latest",
            KIE_SANDBOX_APP_NAME = "kie-sandbox",
            KIE_SANDBOX_IMAGE_URL = "quay.io/kogito_tooling_bot/kie-sandbox-image:latest";

    private static final int
            CORS_PROXY_PORT = 8080,
            EXTENDED_SERVICES_PORT = 21345,
            KIE_SANDBOX_PORT = 8080;

    private final OpenShiftClient openShiftClient;

    KieToolsDeployer(OpenShiftClient openShiftClient) {
        this.openShiftClient = openShiftClient;
    }

    void deploy(String projectName) {
        createProject(projectName);

        String corsProxyHost = createApp(projectName, CORS_PROXY_APP_NAME, CORS_PROXY_IMAGE_URL, CORS_PROXY_PORT, Collections.emptyList());
        String extendedServicesHost = createApp(projectName, EXTENDED_SERVICES_APP_NAME, EXTENDED_SERVICES_IMAGE_URL, EXTENDED_SERVICES_PORT, Collections.emptyList());

        SimpleEntry<String, String> corsProxyUrl = new SimpleEntry<>("CORS_PROXY_URL", "https://" + corsProxyHost);
        SimpleEntry<String, String> extendedServicesUrl = new SimpleEntry<>("KIE_SANDBOX_EXTENDED_SERVICES_URL", "https://" + extendedServicesHost);

        String kieSandBoxUrl = createApp(projectName, KIE_SANDBOX_APP_NAME, KIE_SANDBOX_IMAGE_URL, KIE_SANDBOX_PORT, Arrays.asList(corsProxyUrl, extendedServicesUrl));

        System.out.println("Kie SandBox is available on: https://" + kieSandBoxUrl);
    }

    void undeploy(String projectName) {
        openShiftClient.projects()
                .list()
                .getItems()
                .stream()
                .filter(project -> project.getMetadata().getName().equals(projectName))
                .findFirst()
                .ifPresentOrElse(
                        project -> openShiftClient.projects().delete(project),
                        () -> System.out.println("Project cannot be deleted because it does not exists: " + projectName));
    }

    private String createApp(String projectName, String appName, String imageUrl, int port, List<SimpleEntry<String, String>> envVars) {
        final String serviceName = appName + "-service";
        final String routeName = appName + "-route";

        createDeployment(appName, projectName, createContainer(appName, imageUrl, envVars));
        createService(serviceName, projectName, appName, port);
        return createRoute(routeName, projectName, serviceName)
                .getSpec()
                .getHost();
    }

    private void createProject(String projectName) {
        if (isProjectExist(projectName)) {
            throw new RuntimeException("Project already exists: " + projectName);
        } else {
            openShiftClient.projectrequests().create(
                    new ProjectRequestBuilder()
                            .withNewMetadata()
                            .withName(projectName)
                            .endMetadata()
                            .build()
            );
        }
    }

    private boolean isProjectExist(String projectName) {
        return openShiftClient.projects()
                .list()
                .getItems()
                .stream()
                .anyMatch(p -> p.getMetadata().getName().equals(projectName));
    }

    public Container createContainer(String appName, String imageUrl, List<SimpleEntry<String, String>> envVars) {
        ContainerBuilder containerBuilder = new ContainerBuilder()
                .withName(appName)
                .withImage(imageUrl);

        envVars.forEach(env -> containerBuilder.addNewEnv().withName(env.getKey()).withValue(env.getValue()).endEnv());

        return containerBuilder.build();
    }

    private void createDeployment(String appName, String projectName, Container container) {
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

    private void createService(String serviceName, String projectName, String appName, int port) {
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

    private Route createRoute(String routeName, String projectName, String serviceName) {
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
