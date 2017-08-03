package fi.csc.chipster.sessiondb.resource;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import fi.csc.chipster.auth.model.Role;
import fi.csc.chipster.rest.Config;
import fi.csc.chipster.rest.RestUtils;
import fi.csc.chipster.rest.hibernate.HibernateUtil;
import fi.csc.chipster.rest.hibernate.Transaction;
import fi.csc.chipster.sessiondb.model.Rule;
import fi.csc.chipster.sessiondb.model.Session;

@Path("authorizations")
public class RuleResource {
	private UUID sessionId;
	private RuleTable authorizationTable;
	private HibernateUtil hibernate;
	private Config config;

	public RuleResource(SessionResource sessionResource, UUID id, RuleTable authorizationTable, Config config) {
		this.sessionId = id;
		this.authorizationTable = authorizationTable;
		this.hibernate = sessionResource.getHibernate();
		this.config = config;
	}

	@GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
	@RolesAllowed(Role.SESSION_DB)
    @Transaction
    public Response get(@PathParam("id") UUID authorizationId, @Context SecurityContext sc) throws IOException {
    	    
		Rule result = authorizationTable.getRule(authorizationId, hibernate.session());
    	if (result == null) {
    		throw new NotFoundException();
    	}	
    	return Response.ok(result).build();    	
    }

    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transaction
    public Response getBySession(@Context SecurityContext sc) {
    	    	
		authorizationTable.checkAuthorization(sc.getUserPrincipal().getName(), sessionId, false);    
    	List<Rule> authorizations = authorizationTable.getRules(sessionId);    	
    	return Response.ok(authorizations).build();	       
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transaction
    public Response post(Rule newAuthorization, @Context UriInfo uriInfo, @Context SecurityContext sc) {
    	
		if (newAuthorization.getRuleId() != null) {
			throw new BadRequestException("authorization already has an id, post not allowed");
		}
		
		if (RuleTable.EVERYONE.equals(newAuthorization.getUsername())) {
			String publicSharingUsername = config.getString(Config.KEY_SESSION_DB_RESTRICT_SHARING_TO_EVERYONE);
			if (!publicSharingUsername.isEmpty() && !publicSharingUsername.equals(sc.getUserPrincipal().getName())) {
				throw new ForbiddenException("sharing to everyone is not allowed for this user");
			}
		}
    	
		Session session = authorizationTable.getSessionForWriting(sc, sessionId);

		newAuthorization.setRuleId(RestUtils.createUUID());    	    	
		
		// make sure a hostile client doesn't set the session
    	newAuthorization.setSession(session);
    	newAuthorization.setSharedBy(sc.getUserPrincipal().getName());
    	
    	authorizationTable.save(newAuthorization, hibernate.session());
    
    	URI uri = uriInfo.getAbsolutePathBuilder().path(newAuthorization.getRuleId().toString()).build();
    	
    	return Response.created(uri).build();
    }
    
    @DELETE
    @Path("{id}")
    @Transaction
    public Response delete(@PathParam("id") UUID authorizationId, @Context SecurityContext sc) {
    	
    	Rule authorizationToDelete = authorizationTable.getRule(authorizationId, hibernate.session());
    	
    	// everybody is allowed remove their own rules, even if they are read-only
    	if (!authorizationToDelete.getUsername().equals(sc.getUserPrincipal().getName())) {
    		// others need read-write permissions
    		authorizationTable.checkAuthorization(sc.getUserPrincipal().getName(), sessionId, true);
    	}
    	    	
    	authorizationTable.delete(sessionId, authorizationToDelete, hibernate.session());    	
    
    	return Response.noContent().build();
    }
}
