package fi.csc.chipster.scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fi.csc.chipster.auth.AuthenticationClient;
import fi.csc.chipster.auth.model.Role;
import fi.csc.chipster.rest.Config;
import fi.csc.chipster.rest.RestUtils;
import fi.csc.chipster.rest.StatusSource;
import fi.csc.chipster.rest.websocket.PubSubServer;
import fi.csc.chipster.scheduler.JobCommand.Command;
import jakarta.servlet.ServletException;
import jakarta.websocket.MessageHandler;

public class OfferJobScheduler implements MessageHandler.Whole<String>, JobScheduler {
	
	private static Logger logger = LogManager.getLogger();
	
	private PubSubServer pubSubServer;

	private JobSchedulerCallback scheduler;	
	
	private OfferJobs jobs = new OfferJobs();
	
	private Timer jobTimer;

	private long jobTimerInterval;
	private long heartbeatLostTimeout;

	private long waitTimeout;
	
	public OfferJobScheduler(Config config, AuthenticationClient authService, JobSchedulerCallback scheduler) throws ServletException {
		
		this.scheduler = scheduler;
		
		this.waitTimeout = config.getLong(Config.KEY_SCHEDULER_WAIT_TIMEOUT);
		this.jobTimerInterval = config.getLong(Config.KEY_SCHEDULER_JOB_TIMER_INTERVAL) * 1000;		
		this.heartbeatLostTimeout = config.getLong(Config.KEY_SCHEDULER_HEARTBEAT_LOST_TIMEOUT);
		
		this.jobTimer = new Timer("websocket job timer", true);
		this.jobTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				// catch exceptions to keep the timer running
				try {
					handleJobTimer();
				} catch (Exception e) {
					logger.error("error in job timer", e);
				}
			}
		}, jobTimerInterval, jobTimerInterval);
		
		SchedulerTopicConfig topicConfig = new SchedulerTopicConfig(authService);
		this.pubSubServer = new PubSubServer(config.getBindUrl(Role.SCHEDULER), "events", this, topicConfig,
				"scheduler-events");
		
		this.pubSubServer.setIdleTimeout(config.getLong(Config.KEY_WEBSOCKET_IDLE_TIMEOUT));
		this.pubSubServer.setPingInterval(config.getLong(Config.KEY_WEBSOCKET_PING_INTERVAL));
		this.pubSubServer.start();
	}
	
	@Override
	public void scheduleJob(UUID sessionId, UUID jobId) {
		
		synchronized (jobs) {
						
			IdPair idPair = new IdPair(sessionId, jobId);
			
			OfferJob previousOfferJob = jobs.get(idPair);
					
			// current comp probably doesn't support non-unique jobIds, but this shouldn't
			// be problem
			// in practice when only finished jobs are copied
			if (jobs.containsJobId(jobId)) {
				if (previousOfferJob == null) {					
					IdPair jobIdPair = new IdPair(sessionId, jobId);
					logger.info("received a new job " + jobIdPair + ", but non-unique jobIds are not supported");
					scheduler.expire(jobIdPair, "non-unique jobId");
					return;
				}
				// else the same job is being scheduled again which is fine
			} 
		
			if (previousOfferJob != null && previousOfferJob.getTimeSinceLastScheduled() < waitTimeout) {
				logger.info("don't schedule job " + idPair + " again, because it was just scheduled");
				
			} else {
				logger.info("schedule job " + idPair);
				
				jobs.addScheduledJob(new IdPair(sessionId, jobId));
				
				JobCommand cmd = new JobCommand(sessionId, jobId, null,  Command.SCHEDULE);
				
				pubSubServer.publish(cmd);
			}
		}
	}
	
	/**
	 * The job has been cancelled or deleted
	 * 
	 * Inform the comps to cancel the job and remove it from the scheduler. By doing
	 * this here in the scheduler we can handle both waiting and running jobs.
	 * 
	 * @param jobId
	 */
	@Override
	public void cancelJob(UUID sessionId, UUID jobId) {

		JobCommand cmd = new JobCommand(sessionId, jobId, null, Command.CANCEL);
		pubSubServer.publish(cmd);
		
		IdPair jobIdPair = new IdPair(sessionId, jobId);
		
		synchronized (jobs) {

			logger.info("cancel job " + jobIdPair);
			jobs.remove(jobIdPair);
		}
	}

	private void handleJobTimer() {
		synchronized (jobs) {

			// if the the running job hasn't sent heartbeats for some time, something
			// unexpected has happened for the
			// comp and the job is lost

			for (IdPair jobIdPair : jobs.getHeartbeatJobs().keySet()) {
				if (jobs.get(jobIdPair).getTimeSinceLastHeartbeat() > heartbeatLostTimeout) {
					jobs.remove(jobIdPair);
					scheduler.expire(jobIdPair, "heartbeat lost");
				}
			}
			
			// fast timeout for jobs that are not runnable

			for (IdPair jobIdPair : jobs.getScheduledJobs().keySet()) {
				OfferJob jobState = jobs.get(jobIdPair);

				if (jobState.getTimeSinceLastScheduled() > waitTimeout && !jobState.isRunnable()) {
					jobs.remove(jobIdPair);
					scheduler.expire(jobIdPair,
							"There was no computing server available to run this job, please inform server maintainers");
				}
			}
		}
	}

	
	/*
	 * React to events from comps
	 */
	@Override
	public void onMessage(String message) {
		
		try {
			JobCommand compMsg = RestUtils.parseJson(JobCommand.class, message);
			IdPair jobIdPair = new IdPair(compMsg.getSessionId(), compMsg.getJobId());
			
			this.handleCompMessage(compMsg, jobIdPair);
			
		} catch (Error e) {
			logger.error("failed to handle comp message", e);
		}		
	}
	
	public void handleCompMessage(JobCommand compMsg, IdPair jobIdPair) {		

		switch (compMsg.getCommand()) {
		case OFFER:

			synchronized (jobs) {
				// when comps offer to run a job, pick the first one

				logger.info("received an offer for job " + jobIdPair + " from comp " + Scheduler.asShort(compMsg.getCompId()));
				// respond only to the first offer
				if (jobs.get(jobIdPair) != null) {
					if (!jobs.get(jobIdPair).hasHeartbeat()) {
						jobs.get(jobIdPair).setHeartbeatTimestamp();
						run(compMsg, jobIdPair);
					}
				} else {
					logger.warn("comp " + Scheduler.asShort(compMsg.getCompId()) + " sent a offer of an non-existing job "
							+ Scheduler.asShort(jobIdPair.getJobId()));
				}
			}
			break;
		case BUSY:
			synchronized (jobs) {
				// there is a comp that is able to run this job later
				logger.info("job " + jobIdPair + " is runnable on comp " + Scheduler.asShort(compMsg.getCompId()));
				if (jobs.get(jobIdPair) != null) {
					jobs.get(jobIdPair).setRunnableTimestamp();
				} else {
					logger.warn("comp " + Scheduler.asShort(compMsg.getCompId()) + " sent a busy message of an non-existing job "
							+ Scheduler.asShort(jobIdPair.getJobId()));
				}
			}
			break;

		case AVAILABLE:

			// when a comp has a free slot, try to schedule all waiting jobs
			
			// don't lock this.jobs, because this.scheduleJob() will need it soon
			// in another thread, if there are jobs in the queue

			logger.debug("comp available " + Scheduler.asShort(compMsg.getCompId()));
			scheduler.newResourcesAvailable();
			break;

		case RUNNING:

			synchronized (jobs) {
				// update the heartbeat timestamps of the running jobs

				logger.debug("job running " + jobIdPair);
				if (jobs.get(jobIdPair) != null) {
					jobs.get(jobIdPair).setHeartbeatTimestamp();
				} else {
					logger.warn("comp " + Scheduler.asShort(compMsg.getCompId()) + " sent a heartbeat of an non-existing job "
							+ Scheduler.asShort(jobIdPair.getJobId()));
				}
			}

			break;

		default:
			logger.warn("unknown command: " + compMsg.getCommand());
		}
	}
	
	/**
	 * Move from SCHEDULED to RUNNING
	 * 
	 * @param compMsg
	 * @param jobId
	 */
	private void run(JobCommand compMsg, IdPair jobId) {
		logger.info("offer for job " + jobId + " chosen from comp " + Scheduler.asShort(compMsg.getCompId()));
		pubSubServer.publish(
				new JobCommand(compMsg.getSessionId(), compMsg.getJobId(), compMsg.getCompId(), Command.CHOOSE));
	}
	
	public void close() {
		if (pubSubServer != null) {
			pubSubServer.stop();
		}
	}

	public StatusSource getPubSubServer() {
		return this.pubSubServer;
	}

	public Map<String, Object> getStatus() {
		
		HashMap<String, Object> status = new HashMap<>();
		status.put("scheduledJobCount", jobs.getScheduledJobs().size());
		
		return status;
	}

	@Override
	public void removeFinishedJob(UUID sessionId, UUID jobId) {
		synchronized (jobs) {
			jobs.remove(new IdPair(sessionId, jobId));
		}
	}
}
