package fi.csc.chipster.rest.hibernate;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.server.ContainerResponse;

@Provider
@Transaction
public class HibernateResponseFilter implements ContainerResponseFilter {

	private HibernateUtil hibernate;

	public HibernateResponseFilter(HibernateUtil hibernate) {
		this.hibernate = hibernate;
	}

	@Override
	public void filter(ContainerRequestContext requestContext,
			ContainerResponseContext responseContext) {

		if (requestContext.getProperty(HibernateRequestFilter.PROP_HIBERNATE_SESSION) == null) {
			// nothing to do if HibernateRequestFilter didn't run, e.g. on AuthenticationRequestFilter errors
			return;
		}
		
		if (responseContext instanceof ContainerResponse) {
			ContainerResponse response = (ContainerResponse) responseContext;
			
			if (response.isMappedFromException()) {
				hibernate.rollbackAndUnbind();
				return;
			}
		}
		hibernate.commitAndUnbind();		
	}
}