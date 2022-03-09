package fi.csc.chipster.sessiondb.resource;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fi.csc.chipster.auth.model.Role;
import fi.csc.chipster.comp.JobState;
import fi.csc.chipster.rest.RestUtils;
import fi.csc.chipster.rest.hibernate.HibernateUtil;
import fi.csc.chipster.rest.hibernate.Transaction;
import fi.csc.chipster.sessiondb.model.Dataset;
import fi.csc.chipster.sessiondb.model.Input;
import fi.csc.chipster.sessiondb.model.Job;
import fi.csc.chipster.sessiondb.model.JobIdPair;
import fi.csc.chipster.sessiondb.model.Session;
import fi.csc.chipster.sessiondb.model.SessionEvent;
import fi.csc.chipster.sessiondb.model.SessionEvent.EventType;
import fi.csc.chipster.sessiondb.model.SessionEvent.ResourceType;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

public class SessionJobResource {
	
	private static Logger logger = LogManager.getLogger();
	
	final private UUID sessionId;

	private SessionResource sessionResource;
	
	public SessionJobResource() {
		sessionId = null;
	}
	
	public SessionJobResource(SessionResource sessionResource, UUID id) {
		this.sessionResource = sessionResource;
		this.sessionId = id;
	}
	
    // CRUD
    @GET
    @Path("{id}")    
    @RolesAllowed({ Role.CLIENT, Role.SERVER, Role.SESSION_TOKEN }) // don't allow Role.UNAUTHENTICATED
    @Produces(MediaType.APPLICATION_JSON)
    @Transaction
    public Response get(@PathParam("id") UUID jobId, @Context SecurityContext sc) {
    	
//    	logger.info(sc.getUserPrincipal().getName());
    	
    	// checks authorization
    	Session session = sessionResource.getRuleTable().checkSessionReadAuthorization(sc, sessionId, true);    	
    	
    	Job result = getJob(sessionId, jobId, getHibernate().session());
    	    	
    	if (result == null || !result.getSessionId().equals(session.getSessionId())) {
    		throw new NotFoundException();
    	}
    	
   		return Response.ok(result).build();
    }
    
    public static Job getJob(UUID sessionId, UUID jobId, org.hibernate.Session hibernateSession) {
    	
    	return hibernateSession.get(Job.class, new JobIdPair(sessionId, jobId));    	
    }
    
	@GET
	@RolesAllowed({ Role.CLIENT, Role.SERVER, Role.SESSION_TOKEN})
    @Produces(MediaType.APPLICATION_JSON)
	@Transaction
    public Response getAll(@Context SecurityContext sc) {
		
		// checks authorization
		Session session = sessionResource.getRuleTable().checkSessionReadAuthorization(sc, sessionId);
		
		List<Job> result = getJobs(getHibernate().session(), session);

		// if nothing is found, just return 200 (OK) and an empty list
		return Response.ok(toJaxbList(result)).build();
    }
	
	public static List<Job> getJobs(org.hibernate.Session hibernateSession, Session session) {
		
		CriteriaBuilder cb = hibernateSession.getCriteriaBuilder();
		CriteriaQuery<Job> c = cb.createQuery(Job.class);
		Root<Job> r = c.from(Job.class);
		c.select(r);		
		c.where(cb.equal(r.get("jobIdPair").get("sessionId"), session.getSessionId()));		
		List<Job> datasets = HibernateUtil.getEntityManager(hibernateSession).createQuery(c).getResultList();	
				
		return datasets;
	}
	
	@POST
	@Path(RestUtils.PATH_ARRAY)
	@RolesAllowed({ Role.CLIENT, Role.SERVER }) // don't allow Role.UNAUTHENTICATED
    @Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Transaction
    public Response postArray(Job[] jobs, @Context UriInfo uriInfo, @Context SecurityContext sc) {
		List<UUID> ids = postList(Arrays.asList(jobs), uriInfo, sc);		
		
		ObjectNode json = RestUtils.getArrayResponse("jobs", "jobId", ids);		
		
		return Response.created(uriInfo.getRequestUri()).entity(json).build();
	}

	@POST
	@RolesAllowed({ Role.CLIENT, Role.SERVER }) // don't allow Role.UNAUTHENTICATED
    @Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Transaction
    public Response post(Job job, @Context UriInfo uriInfo, @Context SecurityContext sc) {
		
		List<UUID> ids = postList(Arrays.asList(job), uriInfo, sc);
		UUID id = ids.get(0);
		
		URI uri = uriInfo.getAbsolutePathBuilder().path(id.toString()).build();
		
		ObjectNode json = new JsonNodeFactory(false).objectNode();
		json.put("jobId", id.toString());		
		
		return Response.created(uri).entity(json).build();
	}
		
	public List<UUID> postList(List<Job> jobs, @Context UriInfo uriInfo, @Context SecurityContext sc) {
	
		for (Job job : jobs) {			
			// client's are allowed to set the datasetId to preserve the object references within the session
			UUID jobId = job.getJobId();
			if (jobId == null) {
				jobId = RestUtils.createUUID();
			}
			
			// make sure a hostile client doesn't set the session
			if (job.getSessionId() != null && !sessionId.equals(job.getSessionId())) {
				throw new BadRequestException("different sessionId in the job object and in the url");
			}
			job.setJobIdPair(sessionId, jobId);
			
			// we'll set the job.created field for new jobs so that we can trust those for accounting. Client can set the timestamp 
			// for old jobs to preserve the session history
			
			if (job.getState() == null) {
				throw new BadRequestException("job state is null, job ID " + job.getJobId());
				
			} else if (job.getState().isFinished()) {
				if (job.getCreated() == null) {
					logger.warn("a finished job doesn't have creation time. The current time will be used. Username " + job.getCreatedBy() + " toolId " + job.getToolId());
					job.setCreated(Instant.now());
				} else {
					// allow client to post old jobs with correct timestamps (e.g. when importing a zip session)		
				}
			} else {
				if (job.getCreated() == null) {
					// new job, the created time will be set now
					job.setCreated(Instant.now());
				} else {
					throw new BadRequestException("setting the creation time for non-finished jobs is not allowed");
				}
			}
		}		
		
		Session session = sessionResource.getRuleTable().checkSessionReadWriteAuthorization(sc, sessionId);
		
		for (Job job : jobs) {
			
			job.setCreatedBy(sc.getUserPrincipal().getName());
			
			this.checkNewInputs(session, job);
	
			create(job, getHibernate().session());
		}
		
		sessionResource.sessionModified(session, getHibernate().session());
		
		return jobs.stream()
				.map(j -> j.getJobId())
				.collect(Collectors.toList());
    }

	/**
	 * Check that inputs are not modified
	 * 
	 * We'll assume that the inputs were checked when the Job was created. There
	 * should be no reason to modify those later, so we'll just check that those were
	 * not modified. We are interested only about the datasetId, see checkNewInputs() 
	 * for longer explanation.
	 *  
	 */
	private void checkOldInputs(Job requestJob, Job dbJob) {
			
		// if this a job modification, check that inputs were not modified
		Set<String> requestInputIds = requestJob.getInputs().stream()
				.map(i -> i.getDatasetId()).collect(Collectors.toSet()); 

		Set<String> dbInputIds = dbJob.getInputs().stream()
				.map(i -> i.getDatasetId()).collect(Collectors.toSet());

		if (requestInputIds.containsAll(dbInputIds)
				&& dbInputIds.containsAll(requestInputIds)) {

			return;
		} else {

			throw new ForbiddenException("modification of job inputs is not allowed");
		}
	}
	
	/**
	 * Check that the user is allowed to access all input datasets
	 * 
	 * Comp is allowed to get any datasetId with its credentials. This check makes sure that
	 * user can't use others' dataset as a job input even if she is able to find out its datasetId. Throws 
	 * ForbiddenException if the check fails.
	 * 
	 * The current implementation assumes that all the input datasets are in the same session. If user wants
	 * to use a dataset from a different session, she would have to import the dataset to this session first. If this use
	 * case needs a better support in the future, we could either: 
	 * - implement more thorough access right checks here
	 * - implement the user interface for making it easier to copy individual datasets between sessions without 
	 * really copying or moving the file content (server side implementation should be there already)
	 * 
	 * This check may not be strictly needed now when only BashJobScheduler is used.
	 * It uses SessionTokens which effectively prevents the use of datasets from other
	 * sessions. Nevertheless, let's keep this check here at least as long the RestCompServer 
	 * remains in the code base. 
	 * 
	 * @param session All datasets in this session are assumed to be ok
	 * @param requestJob Job to test
	 * @param dbJob 
	 */
	private void checkNewInputs(Session session, Job requestJob) {
		
		// if this a new job, check that all datasets are in the session
		for (Input input : requestJob.getInputs()) {
			UUID datasetId = UUID.fromString(input.getDatasetId());
			Dataset dataset = SessionDatasetResource.getDataset(session.getSessionId(), datasetId, getHibernate().session());
						
			// check that the requested dataset is in the session
			// otherwise anyone with a session can access any dataset
			if (dataset == null || !dataset.getSessionId().equals(session.getSessionId())) {
				throw new ForbiddenException("dataset not found from this session "
						+ "(input: " + input.getInputId() + ", datasetId:" + input.getDatasetId() + ")");
			}
		}
	}

	public void create(Job job, org.hibernate.Session hibernateSession) {
		HibernateUtil.persist(job, hibernateSession);
		SessionEvent event = new SessionEvent(sessionId, ResourceType.JOB, job.getJobId(), EventType.CREATE, job.getState());
		sessionResource.publish(sessionId.toString(), event, hibernateSession);
	}

	@PUT
	@Path("{id}")
	@RolesAllowed({ Role.CLIENT, Role.SERVER, Role.SESSION_TOKEN }) // don't allow Role.UNAUTHENTICATED
    @Consumes(MediaType.APPLICATION_JSON)
	@Transaction
    public Response put(Job requestJob, @PathParam("id") UUID jobId, @Context SecurityContext sc) {
				    		
		// override the url in json with the id in the url, in case a 
		// malicious client has changed it
		requestJob.setJobIdPair(sessionId, jobId);
		
		// allow admin to cancel jobs
		boolean allowAdmin = JobState.CANCELLED.equals(requestJob.getState());

		// check that user has write authorization for the session
		Session session = sessionResource.getRuleTable().checkSessionReadWriteAuthorization(sc, sessionId, allowAdmin);
		
		Job dbJob = getJob(sessionId, jobId, getHibernate().session());
		if (dbJob == null || !dbJob.getSessionId().equals(session.getSessionId())) {
			throw new NotFoundException("job doesn't exist");
		}
		
		if (dbJob.getState().isFinished()) {
			throw new ForbiddenException("job is already in finished state: " + dbJob.getState());
		}
		
		// make sure a hostile client doesn't change the createdBy username
		requestJob.setCreatedBy(dbJob.getCreatedBy());
		// make sure a hostile client doesn't change the created timestamp
		requestJob.setCreated(dbJob.getCreated());
		
		// check that inputs (datasetIds to be precise) were not modified
		this.checkOldInputs(requestJob, dbJob);
		
		update(requestJob, getHibernate().session());
		
		sessionResource.sessionModified(session, getHibernate().session());
		
		return Response.noContent().build();
    }
	
	public void update(Job job, org.hibernate.Session hibernateSession) {
		HibernateUtil.update(job, job.getJobIdPair(), hibernateSession);
		SessionEvent event = new SessionEvent(sessionId, ResourceType.JOB, job.getJobId(), EventType.UPDATE, job.getState());
		sessionResource.publish(sessionId.toString(), event, hibernateSession);
	}

	@DELETE
    @Path("{id}")
	@RolesAllowed({ Role.CLIENT, Role.SERVER }) // don't allow Role.UNAUTHENTICATED
	@Transaction
    public Response delete(@PathParam("id") UUID jobId, @Context SecurityContext sc) {

		// checks authorization
		Session session = sessionResource.getRuleTable().checkSessionReadWriteAuthorization(sc, sessionId);
		Job dbJob = getJob(sessionId, jobId, getHibernate().session());
		
		if (dbJob == null || !dbJob.getSessionId().equals(session.getSessionId())) {
			throw new NotFoundException("job not found");
		}
		
		deleteJob(dbJob, getHibernate().session());
		
		sessionResource.sessionModified(session, getHibernate().session());
		
		return Response.noContent().build();
    }
	
	public void deleteJob(Job job, org.hibernate.Session hibernateSession) {
		HibernateUtil.delete(job, job.getJobIdPair(), hibernateSession);
		SessionEvent event = new SessionEvent(sessionId, ResourceType.JOB, job.getJobId(), EventType.DELETE, job.getState());
		sessionResource.publish(sessionId.toString(), event, hibernateSession);
	}

    /**
	 * Make a list compatible with JSON conversion
	 * 
	 * Default Java collections don't define the XmlRootElement annotation and 
	 * thus can't be converted directly to JSON. 
	 * @param <T>
	 * 
	 * @param result
	 * @return
	 */
	private GenericEntity<Collection<Job>> toJaxbList(Collection<Job> result) {
		return new GenericEntity<Collection<Job>>(result) {};
	}
	
	private HibernateUtil getHibernate() {
		return sessionResource.getHibernate();
	}
}
