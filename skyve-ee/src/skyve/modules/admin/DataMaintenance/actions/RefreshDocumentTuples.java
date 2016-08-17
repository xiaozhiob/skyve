package modules.admin.DataMaintenance.actions;

import org.skyve.CORE;
import org.skyve.EXT;
import org.skyve.metadata.controller.ServerSideAction;
import org.skyve.metadata.controller.ServerSideActionResult;
import org.skyve.metadata.customer.Customer;
import org.skyve.metadata.module.JobMetaData;
import org.skyve.metadata.module.Module;
import org.skyve.metadata.user.User;
import org.skyve.persistence.Persistence;
import org.skyve.web.WebContext;

import modules.admin.domain.DataMaintenance;

public class RefreshDocumentTuples implements ServerSideAction<DataMaintenance> {
	private static final long serialVersionUID = -8003482363810304078L;

	@Override
	public ServerSideActionResult execute(DataMaintenance bean, WebContext webContext)
			throws Exception {

		Persistence pers = CORE.getPersistence();
		User user = pers.getUser();
		Customer customer = user.getCustomer();
		Module module = customer.getModule(DataMaintenance.MODULE_NAME);
		JobMetaData job = module.getJob("jRefreshDocumentTuples");
		
		EXT.runOneShotJob(job, bean, user);
	
		bean.setAuditResponse("Job commenced.");
		

		return new ServerSideActionResult(bean);
	}
	
}
