package fi.csc.chipster.auth.rest;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import fi.csc.chipster.auth.model.Token;
import fi.csc.chipster.rest.Hibernate;
import fi.csc.chipster.rest.Server;
import fi.csc.chipster.rest.provider.RollbackingExceptionMapper;
import fi.csc.microarray.util.Strings;

/**
 * Main class.
 *
 */
public class AuthenticationService implements Server {
	
	private static Logger logger = Logger.getLogger(AuthenticationService.class.getName());
	
    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://localhost:8081/auth/";

	private static Hibernate hibernate;

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public HttpServer startServer() {
    	
    	// show jersey logs in console
    	Logger l = Logger.getLogger(HttpHandler.class.getName());
    	l.setLevel(Level.FINE);
    	l.setUseParentHandlers(false);
    	ConsoleHandler ch = new ConsoleHandler();
    	ch.setLevel(Level.ALL);
    	l.addHandler(ch);
    	
    	List<Class<?>> hibernateClasses = Arrays.asList(new Class<?>[] {
    			Token.class,
    	});
    	
    	// init Hibernate
    	hibernate = new Hibernate();
    	hibernate.buildSessionFactory(hibernateClasses, "chipster-auth-db");
    	
        // create a resource config that scans for JAX-RS resources and providers
    	String[] jaxPackages = new String [] {
    			AuthenticationResource.class.getPackage().getName(),
    			RollbackingExceptionMapper.class.getPackage().getName() };    	
    	
    	logger.info("scanning JAX-RS resources from " + Strings.delimit(Arrays.asList(jaxPackages), ", "));
        final ResourceConfig rc = new ResourceConfig()
        	.packages(jaxPackages)
        	.register(RolesAllowedDynamicFeature.class); // enable the RolesAllowed annotation

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
    }

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
    	
        final HttpServer server = new AuthenticationService().startServer();
        System.out.println(String.format("Jersey app started with WADL available at "
                + "%sapplication.wadl\nHit enter to stop it...", BASE_URI));
        System.in.read();
        GrizzlyFuture<HttpServer> future = server.shutdown();
        try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			logger.log(Level.WARNING, "server shutdown failed", e);
		}
        
        hibernate.getSessionFactory().close();
    }

	@Override
	public String getBaseUri() {
		return BASE_URI;
	}
	
	public static Hibernate getHibernate() {
		return hibernate;
	}
}

