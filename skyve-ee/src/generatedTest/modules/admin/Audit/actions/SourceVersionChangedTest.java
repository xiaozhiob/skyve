package modules.admin.Audit.actions;

import modules.admin.domain.Audit;
import modules.admin.util.AuditFactory;
import modules.admin.util.AuditFactoryExtension;
import util.AbstractActionTest;

/**
 * Generated - local changes will be overwritten.
 * Extend {@link AbstractActionTest} to create your own tests for this action.
 */
public class SourceVersionChangedTest extends AbstractActionTest<Audit, SourceVersionChanged> {

	private AuditFactory factory;

	@Override
	public void setUp() throws Exception {
		factory = new AuditFactoryExtension();
	}

	@Override
	protected SourceVersionChanged getAction() {
		return new SourceVersionChanged();
	}
	@Override
	protected Audit getBean() throws Exception {
		return factory.getInstance();
	}
}