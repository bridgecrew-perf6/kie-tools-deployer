# Kie Tools Deployer

To deploy Kie Tools execute use **deploy** command:
```bash
mvn clean install -Ddeployer.command=deploy -Ddeployer.url=url -Ddeployer.user=user -Ddeployer.password=password -Ddeployer.project=project
```
To undeploy Kie Tools execute use **undeploy** command:
```bash
mvn clean install -Ddeployer.command=undeploy -Ddeployer.url=url -Ddeployer.user=user -Ddeployer.password=password -Ddeployer.project=project
```