package org.skyve.metadata.view.fluent;

import org.skyve.impl.metadata.view.widget.bound.tabular.DataRepeater;

public class FluentDataRepeater extends FluentDataWidget<FluentDataRepeater> {
	private DataRepeater data = null;
	
	public FluentDataRepeater() {
		data = new DataRepeater();
	}

	public FluentDataRepeater(DataRepeater data) {
		this.data = data;
	}

	public FluentDataRepeater from(@SuppressWarnings("hiding") DataRepeater data) {
		showColumnHeaders(data.getShowColumnHeaders());
		showGrid(data.getShowGrid());
		super.from(data);
		return this;
	}

	public FluentDataRepeater showColumnHeaders(Boolean showColumnHeaders) {
		data.setShowColumnHeaders(showColumnHeaders);
		return this;
	}

	public FluentDataRepeater showGrid(Boolean showGrid) {
		data.setShowGrid(showGrid);
		return this;
	}

	@Override
	public DataRepeater get() {
		return data;
	}
}