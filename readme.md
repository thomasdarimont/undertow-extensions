# Undertow extensions

## Service Availability HTTP Handler

Special `HttpHandler` Filter which returns HTTP Status Code `503 Service Unvailable`
until a given deployment unit is successfully deployed. 
The current approach is to listen for deployment-deployed JMX Notifications. 

### Context
The undertow servlet-container is started pretty early during the startup of the wildfly application server.  
However the initialization of the actual web application might take a while to complete. 
Requests that are sent to any application endpoints within this period result in responses with HTTP Status Code `404 File Not Found`.
Since a load-balancer relies on the information provider by the downstream server it might assume that the application 
is ready when in fact it is not.

### Build

### Deploy

Connect to jboss-cli via `$WILDFLY_HOME/bin/jboss-cli.sh`
```
# connect to the currently running wildfly instance
connect 

# deploy the module
module add 
--name=de.tdlabs.undertow.extensions.serviceavailability
--resources=/path/to/service-availability-http-handler-1.0.0.BUILD-SNAPSHOT.jar
--dependencies=io.undertow.core,org.jboss.logging,javax.api
```

### Configure
The following fragment shows an example configuration of the `serviceavailability` handler for undertow.
```xml
<subsystem xmlns="urn:jboss:domain:undertow:3.0">
    <buffer-cache name="default"/>
    <server name="default-server">
        <ajp-listener name="ajp" socket-binding="ajp"/>
        <http-listener name="default" socket-binding="http" redirect-socket="https"/>
        <host name="default-host" alias="localhost">
            <location name="/" handler="welcome-content"/>
            <filter-ref name="server-header"/>
            <filter-ref name="x-powered-by-header"/>
            <filter-ref name="service-availability"/> <!-- add a reference to the filter here -->
        </host>
    </server>
    <servlet-container name="default">
        <jsp-config/>
        <websockets/>
    </servlet-container>
    <handlers>
        <file name="welcome-content" path="${jboss.home.dir}/welcome-content"/>
    </handlers>
    <filters>
        <response-header name="server-header" header-name="Server" header-value="WildFly/10"/>
        <response-header name="x-powered-by-header" header-name="X-Powered-By" header-value="Undertow/1"/>
        
        <!-- configure custom filter configuration -->
        <filter name="service-availability"  
                module="de.tdlabs.undertow.extensions.serviceavailability" 
                class-name="de.tdlabs.undertow.handler.sa.ServiceAvailabilityHandler">
                <param name="deploymentName" value="keycloak-server.war"/>
                <param name="pathPrefixPattern" value="^/auth/.*$"/>
        </filter>
    </filters>
</subsystem>

```