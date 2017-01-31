package de.tdlabs.undertow.handler.sa;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.logging.Logger;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Special @link {@link HttpHandler} Filter which returns HTTP Status Code {@code 503 Service Unvailable}
 * until a given deployment unit is successfully deployed, from then on the application will control the HTTP Response.
 */
public class ServiceAvailabilityHandler implements HttpHandler {

  private static final Logger log = Logger.getLogger(ServiceAvailabilityHandler.class);

  private static final String JBOSS_AS_DEPLOYMENT_OBJECT_NAME_TEMPLATE = "jboss.as:deployment=%s";

  private static final int SERVICE_UNVAILABLE = 503;

  private final AtomicBoolean applicationReady = new AtomicBoolean(false);

  private Pattern pathPattern;

  private String pathPrefixPattern;

  private String deploymentName;

  private volatile boolean intialized = false;

  private final HttpHandler next;

  public ServiceAvailabilityHandler(HttpHandler next) {
    this.next = next;
  }

  public void handleRequest(HttpServerExchange exchange) throws Exception {

    // lazy initialize on first request
    //TODO figure out how to initialize an Undertow Handler after construction to get rid of this
    if (!intialized) {
      init();
      intialized = true;
    }

    if (!pathPattern.matcher(exchange.getRequestPath()).matches()) {
      next.handleRequest(exchange);
      return;
    }

    if (applicationReady.get()) {
      next.handleRequest(exchange);
      return;
    }

    exchange.setStatusCode(SERVICE_UNVAILABLE);
  }

  private void init() {

    this.pathPattern = Pattern.compile(this.pathPrefixPattern);

    String deploymentObjectName = String.format(JBOSS_AS_DEPLOYMENT_OBJECT_NAME_TEMPLATE, this.deploymentName);
    ApplicationAvailabilityChangeNotificationListener listener = new ApplicationAvailabilityChangeNotificationListener(deploymentObjectName, applicationReady);

    if (!ApplicationAvailabilityMbeanRegistrar.register(deploymentObjectName, listener)) {
      log.warn("Initialization of application availability detection failed! Marking the application as ready.");
      applicationReady.set(true);
    }
  }

  public String getPathPrefixPattern() {
    return pathPrefixPattern;
  }

  public void setPathPrefixPattern(String pathPrefixPattern) {
    this.pathPrefixPattern = pathPrefixPattern;
  }

  public String getDeploymentName() {
    return deploymentName;
  }

  public void setDeploymentName(String deploymentName) {
    this.deploymentName = deploymentName;
  }


  static class ApplicationAvailabilityMbeanRegistrar {

    static boolean register(String objectName, NotificationListener listener) {

      try {
        ObjectName on = new ObjectName(objectName);

        ManagementFactory
          .getPlatformMBeanServer()
          .addNotificationListener(on, listener, null, null);

        return true;
      } catch (MalformedObjectNameException mone) {
        log.warn("Could not register application-availability ObjectName: {}", objectName, mone);
      } catch (InstanceNotFoundException infe) {
        log.warn("Could not find object instance with ObjectName: {}", objectName, infe);
      }

      return false;
    }
  }

  static class ApplicationAvailabilityChangeNotificationListener implements NotificationListener {

    private static final String DEPLOYMENT_DEPLOYED = "deployment-deployed";

    private final AtomicBoolean applicationReady;

    private final String objectName;

    private ApplicationAvailabilityChangeNotificationListener(String objectName, AtomicBoolean applicationReady) {
      this.objectName = objectName;
      this.applicationReady = applicationReady;
    }

    public void handleNotification(Notification notification, Object handback) {

      boolean matchesObjectName = notification.getSource().toString().equals(objectName);
      if (!matchesObjectName) {
        return;
      }

      boolean deploymentDeployed = DEPLOYMENT_DEPLOYED.equals(notification.getType());
      if (!deploymentDeployed) {
        return;
      }

      log.warn("Detected application availability changed to ready! Marking the application as ready.");
      applicationReady.set(true);
    }
  }
}
