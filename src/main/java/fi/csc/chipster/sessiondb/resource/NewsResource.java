package fi.csc.chipster.sessiondb.resource;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fi.csc.chipster.auth.model.Role;
import fi.csc.chipster.rest.hibernate.Transaction;
import fi.csc.chipster.sessiondb.model.News;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("news")
public class NewsResource {
	
	@SuppressWarnings("unused")
	private static Logger logger = LogManager.getLogger();
	private NewsApi newsApi;
	
	public NewsResource(NewsApi newsApi) {
		this.newsApi = newsApi;
	}
    
	@GET
	@RolesAllowed({ Role.UNAUTHENTICATED, Role.CLIENT }) // anyone
    @Produces(MediaType.APPLICATION_JSON)	
	@Transaction
    public List<News> getAll() {
		
		// curl 'http://127.0.0.1:8004/news' -H "Authorization: Basic $TOKEN"
			
		return this.newsApi.getAll();
	}
}
