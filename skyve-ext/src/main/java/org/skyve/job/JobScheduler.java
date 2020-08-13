package org.skyve.job;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerUtils;
import org.quartz.impl.StdSchedulerFactory;
import org.skyve.EXT;
import org.skyve.dataaccess.sql.SQLDataAccess;
import org.skyve.domain.Bean;
import org.skyve.domain.MapBean;
import org.skyve.domain.messages.Message;
import org.skyve.domain.messages.ValidationException;
import org.skyve.domain.types.DateTime;
import org.skyve.impl.bind.BindUtil;
import org.skyve.impl.job.AbstractSkyveJob;
import org.skyve.impl.job.ContentGarbageCollectionJob;
import org.skyve.impl.job.ContentInitJob;
import org.skyve.impl.job.SkyveTriggerListener;
import org.skyve.impl.metadata.repository.AbstractRepository;
import org.skyve.impl.persistence.AbstractPersistence;
import org.skyve.impl.util.SQLMetaDataUtil;
import org.skyve.impl.util.UtilImpl;
import org.skyve.impl.web.AbstractWebContext;
import org.skyve.metadata.MetaDataException;
import org.skyve.metadata.customer.Customer;
import org.skyve.metadata.module.JobMetaData;
import org.skyve.metadata.module.Module;
import org.skyve.metadata.user.User;
import org.skyve.util.Util;
import org.skyve.web.BackgroundTask;

public class JobScheduler {
	private static Scheduler JOB_SCHEDULER = null;
	private static final SkyveTriggerListener SKYVE_TRIGGER_LISTENER = new SkyveTriggerListener();

	public static final String REPORTS_GROUP = "REPORTS GROUP";
	private static final String REPORT_JOB_CLASS = "modules.admin.ReportTemplate.jobs.ReportJob";

	public static void init() {
		SchedulerFactory sf = new StdSchedulerFactory();
		try {
			JOB_SCHEDULER = sf.getScheduler();
			JOB_SCHEDULER.addGlobalTriggerListener(SKYVE_TRIGGER_LISTENER);
			JOB_SCHEDULER.start();
		}
		catch (SchedulerException e) {
			throw new IllegalStateException("Could not start scheduler", e);
		}

		try {
			if (UtilImpl.JOB_SCHEDULER) {
				// Add metadata jobs
				AbstractRepository repository = AbstractRepository.get();
				for (String moduleName : repository.getAllVanillaModuleNames()) {
					Module module = repository.getModule(null, moduleName);
					addJobs(module);
				}

				// Add triggers
				List<Bean> jobSchedules = SQLMetaDataUtil.retrieveAllJobSchedulesForAllCustomers().stream()
						.filter(js -> !Boolean.TRUE.equals(BindUtil.get(js, "disabled")))
						.collect(Collectors.toList());
				for (Bean jobSchedule : jobSchedules) {
					scheduleJob(jobSchedule, (User) BindUtil.get(jobSchedule, "user"));
				}

				// Add report triggers
				if (isReportsAvailable()) {
					final List<Bean> reportSchedules = retrieveAllReportSchedulesForAllCustomers();
					for (Bean reportSchedule : reportSchedules) {
						addReportJob((String) BindUtil.get(reportSchedule, "name"));
						if (Boolean.TRUE.equals(BindUtil.get(reportSchedule, "scheduled"))) {
							scheduleReport(reportSchedule, (User) BindUtil.get(reportSchedule, "user"));
						}
					}
				}
			}

			scheduleInternalJobs();
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not schedule jobs", e);
		}
	}

	public static void dispose() {
		try {
			JOB_SCHEDULER.shutdown();
		}
		catch (SchedulerException e) {
			e.printStackTrace();
		}
	}

	private static void addJobs(Module module)
	throws Exception {
		for (JobMetaData job : module.getJobs()) {
			Class<?> jobClass = Thread.currentThread().getContextClassLoader().loadClass(job.getClassName());
			JobDetail detail = new JobDetail(job.getName(), module.getName(), jobClass);
			detail.setDurability(true); // remain in store even when no triggers are using it

			JOB_SCHEDULER.addJob(detail, false);
		}
	}

	public static void addReportJob(String reportName) throws Exception {
		final Class<?> jobClass = Thread.currentThread().getContextClassLoader().loadClass(REPORT_JOB_CLASS);
		if (jobClass != null) {
			final JobDetail detail = new JobDetail(reportName, REPORTS_GROUP, jobClass);
			detail.setDurability(true); // remain in store even when no triggers are using it

			JOB_SCHEDULER.addJob(detail, true);
		} else {
			Util.LOGGER.warning("ReportJob class could not be found, reports cannot be scheduled");
		}
	}

	private static void scheduleInternalJobs()
	throws Exception {
		// initialise the CMS in a 1 shot immediate job
		JobDetail detail = new JobDetail("CMS Init",
											Scheduler.DEFAULT_GROUP,
											ContentInitJob.class);
		detail.setDurability(false);
		Trigger trigger = new SimpleTrigger("CMS Init Trigger",
												Scheduler.DEFAULT_GROUP);
		JOB_SCHEDULER.scheduleJob(detail, trigger);

		// Do CMS garbage collection as schedule in the CRON expression in the application properties file
		detail = new JobDetail("CMS Garbage Collection",
								Scheduler.DEFAULT_GROUP,
								ContentGarbageCollectionJob.class);
		detail.setDurability(true);
		trigger = new CronTrigger("CMS Garbage Collection Trigger",
									Scheduler.DEFAULT_GROUP,
									"CMS Garbage Collection",
									Scheduler.DEFAULT_GROUP,
				new Date(new Date().getTime() + 300000), // start in 5 minutes once the CMS has settled down
									null,
									UtilImpl.CONTENT_GC_CRON);
		try {
			JOB_SCHEDULER.scheduleJob(detail, trigger);
			Util.LOGGER.info("CMS Garbage Collection Job scheduled for " +trigger.getNextFireTime());
		}
		catch (SchedulerException e) {
			Util.LOGGER.severe("CMS Garbage Collection Job was not scheduled because - " + e.getLocalizedMessage());
		}
	}

	/**
	 * Run a job once.
	 * The job disappears from the Scheduler once it is run and a record of the run in placed in admin.Job.
	 * User must look in admin to see if job was successful.
	 *
	 * @param job The job to run
	 * @param parameter The job parameter - can be null.
	 * @param user The user to run the job as.
	 *
	 * @throws Exception Anything.
	 */
	public static void runOneShotJob(JobMetaData job, Bean parameter, User user)
	throws Exception {
		Trigger trigger = TriggerUtils.makeImmediateTrigger(UUID.randomUUID().toString(), 0, 0);
		trigger.setGroup(user.getCustomer().getName());
		trigger.setJobGroup(job.getOwningModuleName());
		trigger.setJobName(job.getName());

		scheduleJob(job, parameter, user, trigger, null);
	}

	/**
	 * Extra parameter gives polling UIs the chance to display the results of the job.
	 *
	 * @param job The job to run
	 * @param parameter The job parameter - can be null.
	 * @param user The user to run the job as.
	 * @param sleepAtEndInSeconds Set this 5 secs higher than the polling time of the UI
	 * @throws Exception
	 */
	public static void runOneShotJob(JobMetaData job, Bean parameter, User user, int sleepAtEndInSeconds)
	throws Exception {
		Trigger trigger = TriggerUtils.makeImmediateTrigger(UUID.randomUUID().toString(), 0, 0);
		trigger.setGroup(user.getCustomer().getName());
		trigger.setJobGroup(job.getOwningModuleName());
		trigger.setJobName(job.getName());

		scheduleJob(job, parameter, user, trigger, new Integer(sleepAtEndInSeconds));
	}

	/**
	 * Run a Background task.
	 *
	 * @param taskClass The job to run
	 * @param user The current user
	 * @param webId The webId of the conversation to get from the cache
	 * @throws Exception
	 */
	public static <T extends Bean> void runBackgroundTask(Class<? extends BackgroundTask<T>> taskClass, User user, String webId)
	throws Exception {
		Trigger trigger = TriggerUtils.makeImmediateTrigger(UUID.randomUUID().toString(), 0, 0);
		trigger.setVolatility(true);
		JobDataMap map = trigger.getJobDataMap();
		map.put(AbstractSkyveJob.USER_JOB_PARAMETER_KEY, user);
		map.put(AbstractWebContext.CONTEXT_NAME, webId);

		JobDetail jd = new JobDetail(UUID.randomUUID().toString(), taskClass);
		jd.setVolatility(true);
		jd.setDurability(false);

		JOB_SCHEDULER.scheduleJob(jd, trigger);
	}

	/**
	 * Run a job once at a certain date and time.
	 * The job disappears from the Scheduler once it is run and a record of the run in placed in admin.Job.
	 * User must look in admin to see if job was successful.
	 *
	 * @param job The job to run
	 * @param parameter The job parameter - can be null.
	 * @param user The user to run the job as.
	 * @param when The date/time to run the job at.
	 *
	 * @throws Exception Anything.
	 */
	public static void scheduleOneShotJob(JobMetaData job, Bean parameter, User user, Date when)
	throws Exception {
		SimpleTrigger trigger = new SimpleTrigger(UUID.randomUUID().toString(), user.getCustomer().getName(), when);
		trigger.setJobGroup(job.getOwningModuleName());
		trigger.setJobName(job.getName());

		scheduleJob(job, parameter, user, trigger, null);
	}

	public static void scheduleJob(Bean jobSchedule, User user)
	throws Exception {
		String bizId = (String) BindUtil.get(jobSchedule, Bean.DOCUMENT_ID);
		String jobName = (String) BindUtil.get(jobSchedule, "jobName");

		int dotIndex = jobName.indexOf('.');
		String moduleName = jobName.substring(0, dotIndex);
		jobName = jobName.substring(dotIndex + 1);

		Customer customer = user.getCustomer();
		Module module = customer.getModule(moduleName);
		JobMetaData job = module.getJob(jobName);
		if (job == null) { // no job defined
			throw new MetaDataException(String.format("Job %s.%s in the data store (ADM_JobSchedule) is not defined in the skyve metadata.",
							moduleName, jobName));
		}

		Date sqlStartTime = (Date) BindUtil.get(jobSchedule, "startTime");
		DateTime startTime = (sqlStartTime == null) ? null : new DateTime(sqlStartTime.getTime());
		Date sqlEndTime = (Date) BindUtil.get(jobSchedule, "endTime");
		DateTime endTime = (sqlEndTime == null) ? null : new DateTime(sqlEndTime.getTime());
		String cronExpression = (String) BindUtil.get(jobSchedule, "cronExpression");

		CronTrigger trigger = new CronTrigger();
		trigger.setCronExpression(cronExpression);
		trigger.setGroup(customer.getName());
		trigger.setName(bizId);
		trigger.setJobGroup(moduleName);
		trigger.setJobName(jobName);

		if (startTime != null) {
			trigger.setStartTime(startTime);
		}
		if (endTime != null) {
			trigger.setEndTime(endTime);
		}

		scheduleJob(job, null, user, trigger, null);
	}

	private static void scheduleJob(JobMetaData job,
			Bean parameter,
			User user,
			Trigger trigger,
			Integer sleepAtEndInSeconds)
	throws Exception {
		// Add the job data
		JobDataMap map = trigger.getJobDataMap();
		map.put(AbstractSkyveJob.DISPLAY_NAME_JOB_PARAMETER_KEY, job.getDisplayName());
		map.put(AbstractSkyveJob.BEAN_JOB_PARAMETER_KEY, parameter);
		map.put(AbstractSkyveJob.USER_JOB_PARAMETER_KEY, user);
		if (sleepAtEndInSeconds != null) {
			map.put(AbstractSkyveJob.SLEEP_JOB_PARAMETER_KEY, sleepAtEndInSeconds);
		}

		StringBuilder trace = new StringBuilder(128);

		// check end time
		Date currentTime = new Date();
		Date triggerEndTime = trigger.getEndTime();
		Date firstFireTime = trigger.getFireTimeAfter(currentTime);

		if ((triggerEndTime != null) && triggerEndTime.before(currentTime)) {
			trace.append("No scheduling required (end time = ").append(triggerEndTime).append(" of ");
		}
		else {
			// Set the first fire time (if job is scheduled and recurring)
			if (firstFireTime != null) {
				trace.append("Scheduled execution of ");
				trigger.setStartTime(firstFireTime);
			}
			else {
				trace.append("Immediate execution of ");
			}

			// schedule
			try {
				JOB_SCHEDULER.scheduleJob(trigger);
			}
			catch (@SuppressWarnings("unused") ObjectAlreadyExistsException e) {
				throw new ValidationException(new Message("You are already running job " + job.getDisplayName() +
															".  Look in the jobs list for more information."));
			}
		}

		trace.append(trigger.getJobGroup()).append('.').append(trigger.getJobName());
		trace.append(": ").append(job.getDisplayName()).append(" with trigger ");
		trace.append(trigger.getGroup() + '/' + trigger.getName());
		if (firstFireTime != null) {
			trace.append(" first at ").append(firstFireTime);
		}
		UtilImpl.LOGGER.info(trace.toString());
	}

	public static void unscheduleJob(Bean jobSchedule, Customer customer)
	throws Exception {
		JOB_SCHEDULER.unscheduleJob(jobSchedule.getBizId(), customer.getName());
	}

	public static List<JobDescription> getCustomerRunningJobs()
	throws Exception {
		User user = AbstractPersistence.get().getUser();
		List<JobDescription> result = new ArrayList<>();

		String customerName = user.getCustomer().getName();

		for (String triggerName : JOB_SCHEDULER.getTriggerNames(customerName)) {
			Trigger trigger = JOB_SCHEDULER.getTrigger(triggerName, customerName);
			AbstractSkyveJob job = SKYVE_TRIGGER_LISTENER.getRunningJob(customerName, trigger.getName());
			if (job != null) {
				JobDescription jd = new JobDescription();
				jd.setUser((User) trigger.getJobDataMap().get(AbstractSkyveJob.USER_JOB_PARAMETER_KEY));
				jd.setStartTime(job.getStartTime());
				jd.setName(job.getDisplayName());
				jd.setPercentComplete(job.getPercentComplete());
				jd.setLogging(job.createLogDescriptionString());

				result.add(jd);
			}
		}

		return result;
	}

	public static void scheduleReport(Bean reportSchedule, User user) throws Exception {
		if (isReportsAvailable()) {
			String bizId = (String) BindUtil.get(reportSchedule, Bean.DOCUMENT_ID);
			String reportName = (String) BindUtil.get(reportSchedule, "name");

			Customer customer = user.getCustomer();

			Date sqlStartTime = (Date) BindUtil.get(reportSchedule, "startTime");
			DateTime startTime = (sqlStartTime == null) ? null : new DateTime(sqlStartTime.getTime());
			Date sqlEndTime = (Date) BindUtil.get(reportSchedule, "endTime");
			DateTime endTime = (sqlEndTime == null) ? null : new DateTime(sqlEndTime.getTime());
			String cronExpression = (String) BindUtil.get(reportSchedule, "cronExpression");

			CronTrigger trigger = new CronTrigger();
			trigger.setCronExpression(cronExpression);
			trigger.setGroup(customer.getName());
			trigger.setName(bizId);
			trigger.setJobGroup(REPORTS_GROUP);
			trigger.setJobName(reportName);

			if (startTime != null) {
				trigger.setStartTime(startTime);
			}
			if (endTime != null) {
				trigger.setEndTime(endTime);
			}

			scheduleReport((String) BindUtil.get(reportSchedule, "name"), reportSchedule, user, trigger, null);
		}
	}

	private static void scheduleReport(String jobName,
			Bean parameter,
			User user,
			Trigger trigger,
			Integer sleepAtEndInSeconds) throws Exception {
		// Add the job data
		JobDataMap map = trigger.getJobDataMap();
		map.put(AbstractSkyveJob.DISPLAY_NAME_JOB_PARAMETER_KEY, jobName);
		map.put(AbstractSkyveJob.BEAN_JOB_PARAMETER_KEY, parameter);
		map.put(AbstractSkyveJob.USER_JOB_PARAMETER_KEY, user);
		if (sleepAtEndInSeconds != null) {
			map.put(AbstractSkyveJob.SLEEP_JOB_PARAMETER_KEY, sleepAtEndInSeconds);
		}

		StringBuilder trace = new StringBuilder(128);

		// check end time
		Date currentTime = new Date();
		Date triggerEndTime = trigger.getEndTime();
		Date firstFireTime = trigger.getFireTimeAfter(currentTime);

		if ((triggerEndTime != null) && triggerEndTime.before(currentTime)) {
			trace.append("No scheduling required (end time = ").append(triggerEndTime).append(" of ");
		} else {
			// Set the first fire time (if job is scheduled and recurring)
			if (firstFireTime != null) {
				trace.append("Scheduled execution of ");
				trigger.setStartTime(firstFireTime);
			} else {
				trace.append("Immediate execution of ");
			}

			// schedule
			try {
				JOB_SCHEDULER.scheduleJob(trigger);
			} catch (@SuppressWarnings("unused") ObjectAlreadyExistsException e) {
				throw new ValidationException(new Message("You are already running job " + jobName +
						".  Look in the jobs list for more information."));
			}
		}

		trace.append(trigger.getJobGroup()).append('.').append(trigger.getJobName());
		trace.append(": ").append(jobName).append(" with trigger ");
		trace.append(trigger.getGroup() + '/' + trigger.getName());
		if (firstFireTime != null) {
			trace.append(" first at ").append(firstFireTime);
		}
		UtilImpl.LOGGER.info(trace.toString());
	}

	public static void unscheduleReport(Bean reportSchedule, Customer customer) throws Exception {
		if (JOB_SCHEDULER != null) {
			if (isReportsAvailable()) {
				JOB_SCHEDULER.unscheduleJob(reportSchedule.getBizId(), customer.getName());
			}
		}
	}

	public static List<Bean> retrieveAllReportSchedulesForAllCustomers() throws Exception {
		List<Bean> result = new ArrayList<>();

		// Principal -> User
		Map<String, User> users = new TreeMap<>();

		AbstractRepository repository = AbstractRepository.get();
		Module admin = repository.getModule(null, "admin");
		String ADM_ReportTemplate = admin.getDocument(null, "ReportTemplate").getPersistent().getPersistentIdentifier();
		String ADM_SecurityUser = admin.getDocument(null, "User").getPersistent().getPersistentIdentifier();

		StringBuilder sql = new StringBuilder(256);
		sql.append(
				"select s.bizId, s.bizCustomer, s.name, s.startTime, s.endTime, s.cronExpression, s.scheduled,  u.userName from ");
		sql.append(ADM_ReportTemplate).append(" s left join ").append(ADM_SecurityUser).append(" u on s.runAs_id = u.bizId")
				.append(" order by u.bizCustomer");

		try (SQLDataAccess da = EXT.newSQLDataAccess()) {
			List<Object[]> rows = da.newSQL(sql.toString()).tupleResults();
			for (Object[] row : rows) {
				User user = null;
				if (row[7] != null) {
					StringBuilder userPrincipalBuilder = new StringBuilder(128);
					userPrincipalBuilder.append(row[1]); // bizCustomer
					userPrincipalBuilder.append('/').append(row[7]); // userName
					String userPrincipal = userPrincipalBuilder.toString();
					user = users.get(userPrincipal);
					if (user == null) {
						user = repository.retrieveUser(userPrincipal);
						users.put(userPrincipal, user);
					}
				}

				Map<String, Object> properties = new TreeMap<>();
				properties.put(Bean.DOCUMENT_ID, row[0]); // bizId
				properties.put("name", row[2]);
				properties.put("startTime", row[3]);
				properties.put("endTime", row[4]);
				properties.put("cronExpression", row[5]);
				properties.put("scheduled", row[6]);
				properties.put("user", user);

				MapBean reportSchedule = new MapBean("rufis", "ReportTemplate", properties);
				result.add(reportSchedule);
			}
		}

		return result;
	}

	private static boolean isReportsAvailable() {
		try {
			if (Class.forName(REPORT_JOB_CLASS) != null) {
				return true;
			}
		} catch (Exception e) {
			// report job class not found on classpath
		}
		return false;
	}
}
