package fi.csc.chipster.auth.resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Priority;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;

import fi.csc.chipster.auth.model.Role;
import fi.csc.chipster.auth.model.Token;
import fi.csc.chipster.auth.model.User;
import fi.csc.chipster.auth.model.UserId;
import fi.csc.chipster.rest.Config;
import fi.csc.chipster.rest.hibernate.HibernateUtil;
import fi.csc.chipster.rest.hibernate.HibernateUtil.HibernateRunnable;
import fi.csc.chipster.rest.token.BasicAuthParser;
import fi.csc.chipster.rest.token.TokenRequestFilter;
import fi.csc.microarray.auth.JaasAuthenticationProvider;
import fi.csc.microarray.config.ConfigurationLoader.IllegalConfigurationException;

/**
 * @author klemela
 *
 */
@Provider
@Priority(Priorities.AUTHENTICATION) // execute this filter before others
public class AuthenticationRequestFilter implements ContainerRequestFilter {


	private static final Logger logger = LogManager.getLogger();

	private HibernateUtil hibernate;
	
	@SuppressWarnings("unused")
	private Config config;
	private UserTable userTable;

	private Map<String, String> serviceAccounts;
	private Set<String> adminAccounts;

	private final String jaasPrefix;

	private JaasAuthenticationProvider authenticationProvider;

	private HashMap<String, String> monitoringAccounts;

	public AuthenticationRequestFilter(HibernateUtil hibernate, Config config, UserTable userTable) throws IOException, IllegalConfigurationException {
		this.hibernate = hibernate;
		this.config = config;
		this.userTable = userTable;

		serviceAccounts = config.getServicePasswords();		
		adminAccounts = config.getAdminAccounts();
		jaasPrefix = config.getString(Config.KEY_AUTH_JAAS_PREFIX);
				
		String monitoringPassword = config.getString(Config.KEY_MONITORING_PASSWORD);
		if (config.getDefault(Config.KEY_MONITORING_PASSWORD).equals(monitoringPassword)) {
			logger.warn("default password for username " + Role.MONITORING);
		}
		
		monitoringAccounts = new HashMap<String, String>() {{ put(Role.MONITORING, monitoringPassword); }};
		
		authenticationProvider = new JaasAuthenticationProvider(false);
	}

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {    	
		if ("OPTIONS".equals(requestContext.getMethod())) {

			// CORS preflight checks require unauthenticated OPTIONS
			return;
		}
		String authHeader = requestContext.getHeaderString("authorization");

		if (authHeader == null) {
			
			requestContext.setSecurityContext(new AuthSecurityContext(new AuthPrincipal(null, Role.UNAUTHENTICATED), requestContext.getSecurityContext()));
			return;
//			throw new NotAuthorizedException("no authorization header found");
		}

		if (authHeader.startsWith("Basic ") || authHeader.startsWith("basic ")) {
			BasicAuthParser parser = new BasicAuthParser(authHeader);
	
			AuthPrincipal principal = null;
	
			if (TokenRequestFilter.TOKEN_USER.equals(parser.getUsername())) {
				// throws an exception if fails
				principal = tokenAuthentication(parser.getPassword());
			} else {
				// throws an exception if fails
				principal = passwordAuthentication(parser.getUsername(), parser.getPassword());
			}
	
			// login ok
			AuthSecurityContext sc = new AuthSecurityContext(principal, requestContext.getSecurityContext());
			requestContext.setSecurityContext(sc);
		} else {
			throw new ForbiddenException("unknown authorization header type");
		}
	}

	public AuthPrincipal tokenAuthentication(String tokenKey) {
		// FIXME fail if token expired??		
		UUID uuid;
		try {
			uuid = UUID.fromString(tokenKey);
		} catch (IllegalArgumentException e) {
			throw new ForbiddenException("tokenKey is not a valid UUID");
		}
		
		Token token = getHibernate().runInTransaction(new HibernateRunnable<Token>() {
			@Override
			public Token run(Session hibernateSession) {
				return getHibernate().session().get(Token.class, uuid);
			}			
		});
		
		if (token == null) {
			throw new ForbiddenException();
		}

		return new AuthPrincipal(token.getUsername(), tokenKey, token.getRoles());
	}

	private AuthPrincipal passwordAuthentication(String username, String password) {

		// a small delay to slow down brute force attacks
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			logger.warn(e);
		}

		// check that there is no extra white space in the username, because if authenticationProvider accepts it,
		// it would create a new user in Chipster
		if (!username.trim().equals(username)) {
			throw new ForbiddenException("white space in username");
		}

		if (serviceAccounts.containsKey(username)) { 
			if (serviceAccounts.get(username).equals(password)) {		
				// authenticate with username/password ok
				return new AuthPrincipal(username, getRoles(username));
			}
			// don't let other providers to authenticate internal usernames
			throw new ForbiddenException("wrong password");	
		}
		
		if (monitoringAccounts.containsKey(username)) { 
			if (monitoringAccounts.get(username).equals(password)) {		
				// authenticate with username/password ok
				return new AuthPrincipal(username, getRoles(username));
			}
			// don't let other providers to authenticate internal usernames
			throw new ForbiddenException("wrong password");	
		}
		
		// allow both plain username "jdoe" or userId "jaas/jdoe" 
		String jaasUsername;		
		try {
			// throws if username is not a userId
			UserId userId = new UserId(username);			
			if (userId.getAuth().equals(jaasPrefix)) {
				// jaas userId (e.g. "jaas/jdoe"), login without the prefix
				jaasUsername = userId.getUsername();
			} else {
				// userId, but not from jaas (e.g. "sso/jdoe"), no point to try for jaas
				jaasUsername = null;
			}
		} catch (IllegalArgumentException e) {
			// not a userId but only a username (e.g. "jdoe"), but that's fine
			jaasUsername = username;
		}
		
		if (jaasUsername != null && authenticationProvider.authenticate(jaasUsername, password.toCharArray())) {
			User user = addOrUpdateUser(jaasUsername);
			// authenticate with username/password ok
			return new AuthPrincipal(user.getUserId().toUserIdString(), getRoles(username));
		}
		throw new ForbiddenException("wrong username or password");
	}

	private User addOrUpdateUser(String username) {
		User user = new User(jaasPrefix, username, null, null, username);
		hibernate.runInTransaction(new HibernateRunnable<Void>() {
			@Override
			public Void run(Session hibernateSession) {
				userTable.addOrUpdate(user, hibernateSession);
				return null;
			}
		});
		return user;
	}

	public HashSet<String> getRoles(String username) {

		HashSet<String> roles = new HashSet<>();
		roles.add(Role.PASSWORD);
		
		if (serviceAccounts.keySet().contains(username)) {
			roles.add(Role.SERVER);
			roles.add(username);
			
		} else if (monitoringAccounts.containsKey(username)) {
			roles.add(Role.MONITORING);
			
		} else {
			roles.add(Role.CLIENT);
			
			if (adminAccounts.contains(username)) {
				roles.add(Role.ADMIN);
			}
		}

		return roles;
	}

	private HibernateUtil getHibernate() {
		return hibernate;
	}
}