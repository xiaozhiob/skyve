package modules.admin.Tag.actions;

import modules.admin.domain.Tag;
import modules.admin.util.TagFactory;
import modules.admin.util.TagFactoryExtension;
import util.AbstractActionTest;

/**
 * Generated - local changes will be overwritten.
 * Extend {@link AbstractActionTest} to create your own tests for this action.
 */
public class UnionTagTest extends AbstractActionTest<Tag, UnionTag> {

	private TagFactory factory;

	@Override
	public void setUp() throws Exception {
		factory = new TagFactoryExtension();
	}

	@Override
	protected UnionTag getAction() {
		return new UnionTag();
	}
	@Override
	protected Tag getBean() throws Exception {
		return factory.getInstance();
	}
}