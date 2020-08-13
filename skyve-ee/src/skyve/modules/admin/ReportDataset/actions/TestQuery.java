package modules.admin.ReportDataset.actions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.DynaClass;
import org.apache.commons.beanutils.DynaProperty;
import org.apache.commons.beanutils.LazyDynaBean;
import org.apache.commons.beanutils.LazyDynaMap;
import org.apache.commons.lang3.StringUtils;
import org.skyve.CORE;
import org.skyve.domain.Bean;
import org.skyve.domain.messages.DomainException;
import org.skyve.domain.messages.Message;
import org.skyve.domain.messages.ValidationException;
import org.skyve.domain.types.DateOnly;
import org.skyve.metadata.controller.ServerSideAction;
import org.skyve.metadata.controller.ServerSideActionResult;
import org.skyve.persistence.BizQL;
import org.skyve.web.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import modules.admin.ReportDataset.ReportDatasetExtension;
import modules.admin.ReportDataset.ReportDatasetExtension.SubstitutedQueryResult;
import modules.admin.ReportParameter.ReportParameterExtension;
import modules.admin.domain.ReportDataset;
import modules.admin.domain.ReportDataset.DatasetType;
import services.report.BeanReportDataset;
import services.report.SqlQueryService;

public class TestQuery implements ServerSideAction<ReportDatasetExtension> {

	private static final long serialVersionUID = 8850955374877208367L;
	private static final Logger LOG = LoggerFactory.getLogger(TestQuery.class);

	@Inject
	private transient SqlQueryService sqlService;

	@Override
	public ServerSideActionResult<ReportDatasetExtension> execute(ReportDatasetExtension bean, WebContext webContext)
			throws Exception {
		// clear any previous results
		bean.setResults(null);

		validate(bean);

		if (DatasetType.bizQL == bean.getDatasetType()) {
			StringBuilder queryResults = new StringBuilder(5120);
			try {

				SubstitutedQueryResult sQR = bean.getSubstitutedQuery();
				BizQL bql = CORE.getPersistence().newBizQL(sQR.getQuery()).setMaxResults(20);

				// put any parameters
				for (ReportParameterExtension param : bean.getParent().getParameters()) {
					if (bean.containsParameter(param)) {
						switch (param.getType()) {
							case date:
								bql.putParameter(param.getName(), param.getDateTestValue());
								break;
							case integer:
								bql.putParameter(param.getName(),
										(param.getNumericalTestValue() == null ? null : param.getNumericalTestValue().intValue()));
								break;
							case longInteger:
								bql.putParameter(param.getName(), param.getNumericalTestValue());
								break;
							default:
								bql.putParameter(param.getName(), param.getTextTestValue());
						}
					}
				}

				// put any substituted date parameters
				for (Map.Entry<String, DateOnly> entry : sQR.getParameters().entrySet()) {
					bql.putParameter(entry.getKey(), entry.getValue());
				}

				List<Bean> results = bql.beanResults();
				for (Bean result : results) {
					queryResults.append(result.getBizKey()).append('\n');
				}
				bean.setResults(queryResults.toString());
			} catch (Exception e) {
				trapException(bean, e);
			}
		} else if (DatasetType.SQL == bean.getDatasetType()) {
			StringBuilder queryResults = new StringBuilder(5120);
			try {
				Map<String, Object> sqlParams = new HashMap<>();

				for (ReportParameterExtension param : bean.getParent().getParameters()) {
					if (bean.containsParameter(param)) {
						switch (param.getType()) {
							case date:
								sqlParams.put(param.getName(), param.getDateTestValue());
								break;
							case integer:
								sqlParams.put(param.getName(),
										(param.getNumericalTestValue() == null ? null : param.getNumericalTestValue().intValue()));
								break;
							case longInteger:
								sqlParams.put(param.getName(), param.getNumericalTestValue());
								break;
							default:
								sqlParams.put(param.getName(), param.getTextTestValue());
						}
					}
				}

				ReportParameterExtension.logParameterValues(sqlParams);

				List<DynaBean> results = sqlService.retrieveDynamicResults(bean.getQuery(), sqlParams);
				LOG.info("Returned {} results", results.size());

				for (DynaBean result : results) {
					LazyDynaMap map = (LazyDynaMap) result;
					queryResults.append(StringUtils.join(map.getMap(), ", ")).append('\n');
				}
				bean.setResults(queryResults.toString());
			} catch (Exception e) {
				trapException(bean, e);
			}
		} else if (DatasetType.classValue == bean.getDatasetType()) {
			try {
				@SuppressWarnings("unchecked")
				Class<BeanReportDataset> reportClass = (Class<BeanReportDataset>) Class.forName(bean.getQuery());
				if (reportClass != null) {
					BeanReportDataset dataset = CDI.current().select(reportClass).get();
					StringBuilder queryResults = new StringBuilder(5120);
					for (DynaBean result : dataset.getResults(bean.getParent().getParameters())) {
						if (result instanceof DynaClass) {
							DynaClass dynaClass = (DynaClass) result;
							printDynaClass(queryResults, result, dynaClass);
						} else if (result instanceof LazyDynaBean) {
							LazyDynaBean ldb = (LazyDynaBean) result;
							DynaClass dynaClass = (DynaClass) ldb.getDynaClass();
							printDynaClass(queryResults, result, dynaClass);
						} else {
							queryResults.append(result.toString());
						}
						queryResults.append(result.toString()).append('\n');
					}
					bean.setResults(queryResults.toString());
				}
			} catch (Exception e) {
				throw new DomainException("Unable to create an instance of " + bean.getQuery());
			}
		}

		return new ServerSideActionResult<>(bean);
	}

	private void trapException(ReportDataset bean, Exception e) throws Exception {
		if (e.getClass() == ValidationException.class) {
			throw e;
		}
		StringWriter sw = new StringWriter(512);
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		bean.setResults(sw.toString());
	}

	private void printDynaClass(StringBuilder queryResults, DynaBean result, DynaClass dynaClass) {
		for (DynaProperty prop : dynaClass.getDynaProperties()) {
			queryResults.append(prop.getName() + " : " + result.get(prop.getName()))
					.append(", ");
		}
	}

	private void validate(ReportDataset bean) {
		// check if the query has an parameters
		if (DatasetType.bizQL == bean.getDatasetType() || DatasetType.SQL == bean.getDatasetType()) {
			if (bean.getQuery().matches(":\\w+")) {
				// make sure at least one parameter is defined
				if (bean.getParent().getParameters().size() == 0) {
					throw new ValidationException(new Message(ReportDataset.queryPropertyName,
							"A query parameter has been defined but not declared in the parameter list."));
				}
			}
		}
	}
}
