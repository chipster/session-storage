package fi.csc.chipster.rest;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.servlet.ServletException;
import javax.websocket.DeploymentException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fi.csc.chipster.auth.AuthenticationService;
import fi.csc.chipster.filebroker.FileBroker;
import fi.csc.chipster.proxy.ChipsterProxyServer;
import fi.csc.chipster.scheduler.Scheduler;
import fi.csc.chipster.servicelocator.ServiceLocator;
import fi.csc.chipster.sessiondb.RestException;
import fi.csc.chipster.sessiondb.SessionDb;
import fi.csc.chipster.toolbox.ToolboxService;

public class ServerLauncher {
	
	// this must not be static, otherwise logging configuration fails
	private final Logger logger = LogManager.getLogger();

	private AuthenticationService auth;
	private ServiceLocator serviceLocator;
	private SessionDb sessionDb;
	private Scheduler scheduler;
	private ToolboxService toolbox;
	private ChipsterProxyServer proxy;
	private FileBroker fileBroker;
	
	
	public ServerLauncher(Config config, boolean verbose) throws ServletException, DeploymentException, RestException, InterruptedException, IOException, URISyntaxException {
		if (verbose) {
			logger.info("starting authentication-service");
		}		
		auth = new AuthenticationService(config);
		auth.startServer();
		
		if (verbose) {
			logger.info("starting service-locator");
		}		
		serviceLocator = new ServiceLocator(config);
		serviceLocator.startServer();
		
		if (verbose) {
			logger.info("starting session-db");
		}		
		sessionDb = new SessionDb(config);
		sessionDb.startServer();
		
		if (verbose) {
			logger.info("starting file-broker");
		}		
		fileBroker = new FileBroker(config);
		fileBroker.startServer();
		
		if (verbose) {
			logger.info("starting scheduler");
		}		
		scheduler = new Scheduler(config);
		scheduler.startServer();

		if (verbose) {
			logger.info("starting toolbox");
		}		
		toolbox = new ToolboxService(config);
		toolbox.startServer();

		
		if (verbose) {
			logger.info("starting proxy");
		}		
		proxy = new ChipsterProxyServer(config);
		proxy.startServer();
		
		if (verbose) {
			logger.info("up and running");
		}
	}		

	public void stop() {
		
		if (proxy != null) {
			proxy.close();
		}
		if (toolbox != null) {
			toolbox.close();			
		}
		if (scheduler != null) {
			scheduler.close();			
		}
		if (fileBroker != null) {
			fileBroker.close();
		}
		if (sessionDb != null) {
			sessionDb.close();
		}
		if (serviceLocator != null) {
			serviceLocator.close();
		}
		if (auth != null) {
			auth.close();			
		}
	}	
		
	public static void main(String[] args) throws ServletException, DeploymentException, InterruptedException, RestException, IOException, URISyntaxException {
		Config config = new Config();
		new ServerLauncher(config, true);
	}

	public SessionDb getSessionDb() {
		return sessionDb;
	}
}
