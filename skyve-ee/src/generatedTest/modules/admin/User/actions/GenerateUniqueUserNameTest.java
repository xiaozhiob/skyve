package modules.admin.User.actions;

import modules.admin.domain.User;
import modules.admin.util.UserFactory;
import modules.admin.util.UserFactoryExtension;
import util.AbstractActionTest;

/**
 * Generated - local changes will be overwritten.
 * Extend {@link AbstractActionTest} to create your own tests for this action.
 */
public class GenerateUniqueUserNameTest extends AbstractActionTest<User, GenerateUniqueUserName> {

	private UserFactory factory;

	@Override
	public void setUp() throws Exception {
		factory = new UserFactoryExtension();
	}

	@Override
	protected GenerateUniqueUserName getAction() {
		return new GenerateUniqueUserName();
	}
	@Override
	protected User getBean() throws Exception {
		return factory.getInstance();
	}
}