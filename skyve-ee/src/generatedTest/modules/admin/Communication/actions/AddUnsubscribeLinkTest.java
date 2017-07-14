package modules.admin.Communication.actions;

import modules.admin.domain.Communication;
import modules.admin.util.CommunicationFactory;
import modules.admin.util.CommunicationFactoryExtension;
import util.AbstractActionTest;

/**
 * Generated - local changes will be overwritten.
 * Extend {@link AbstractActionTest} to create your own tests for this action.
 */
public class AddUnsubscribeLinkTest extends AbstractActionTest<Communication, AddUnsubscribeLink> {

	private CommunicationFactory factory;

	@Override
	public void setUp() throws Exception {
		factory = new CommunicationFactoryExtension();
	}

	@Override
	protected AddUnsubscribeLink getAction() {
		return new AddUnsubscribeLink();
	}
	@Override
	protected Communication getBean() throws Exception {
		return factory.getInstance();
	}
}