package org.tomasdavid.deployer;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

public class App {

    private static final String
            command = System.getProperty("deployer.command"),
            url = System.getProperty("deployer.url"),
            user = System.getProperty("deployer.user"),
            password = System.getProperty("deployer.password"),
            project = System.getProperty("deployer.project");

    public static void main(String[] args) {
        checkProperties();

        try (
                final OpenShiftClient openShiftClient = new DefaultOpenShiftClient(
                        new ConfigBuilder()
                                .withMasterUrl(url)
                                .withUsername(user)
                                .withPassword(password)
                                .build()
                )
        ) {
            KieToolsDeployer kieToolsDeployer = new KieToolsDeployer(openShiftClient);

            if (command.equalsIgnoreCase(CommandType.DEPLOY.name())) {
                kieToolsDeployer.deploy(project);
            } else if (command.equalsIgnoreCase(CommandType.UNDEPLOY.name())) {
                kieToolsDeployer.undeploy(project);
            } else {
                throw new RuntimeException("Unsupported command: " + command);
            }
        }
    }

    private static void checkProperties() {
        if (isBlank(url)) {
            throw new RuntimeException("Please specify OpenShift server url: -Ddeployer.url=https://...");
        }

        if (isBlank(user) || isBlank(password)) {
            throw new RuntimeException("Please specify OpenShift credentials: -Ddeployer.user=admin -Ddeployer.password=admin");
        }

        if (isBlank(project)) {
            throw new RuntimeException("Please specify OpenShift project: -Ddeployer.project=projectName");
        }

        if (isBlank(command) || !(command.equals("deploy") || command.equals("undeploy"))) {
            throw new RuntimeException("Please specify command: -Ddeployer.command=deploy|undeploy");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
