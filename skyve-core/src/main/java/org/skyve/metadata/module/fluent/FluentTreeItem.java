package org.skyve.metadata.module.fluent;

import org.skyve.impl.metadata.repository.module.TreeItemMetaData;

public class FluentTreeItem extends FluentMenuItem<FluentTreeItem> {
	private TreeItemMetaData item = null;
	
	public FluentTreeItem() {
		item = new TreeItemMetaData();
	}

	public FluentTreeItem(TreeItemMetaData item) {
		this.item = item;
	}

	public FluentTreeItem from(@SuppressWarnings("hiding") org.skyve.impl.metadata.module.menu.TreeItem item) {
		super.from(item);
		documentName(item.getDocumentName());
		queryName(item.getQueryName());
		modelName(item.getModelName());
		autoPopulate(item.isAutoPopulate());
		return this;
	}
	
	public FluentTreeItem documentName(String documentName) {
		item.setDocumentName(documentName);
		return this;
	}
	
	public FluentTreeItem queryName(String queryName) {
		item.setQueryName(queryName);
		return this;
	}

	public FluentTreeItem modelName(String modelName) {
		item.setModelName(modelName);
		return this;
	}
	
	public FluentTreeItem autoPopulate(boolean autoPopulate) {
		item.setAutoPopulate(autoPopulate ? Boolean.TRUE : Boolean.FALSE);
		return this;
	}

	@Override
	public TreeItemMetaData get() {
		return item;
	}
}
