package org.skyve.domain.types.converters.datetime;

import org.skyve.domain.messages.ConversionException;

public class MMM_DD_YYYY extends AbstractDateTimeConverter {
	public static final String PATTERN = "MMM-dd-yyyy";

	@Override
	protected String getPattern() {
		return PATTERN;
	}

	@Override
	protected String getI18nKey() {
		return ConversionException.MMM_DD_YYYY_DATETIME_KEY;
	}
}
