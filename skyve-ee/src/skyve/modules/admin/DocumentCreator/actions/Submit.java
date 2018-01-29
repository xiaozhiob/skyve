package modules.admin.DocumentCreator.actions;

import org.skyve.domain.messages.Message;
import org.skyve.domain.messages.MessageSeverity;
import org.skyve.domain.messages.ValidationException;
import org.skyve.impl.metadata.repository.document.DocumentMetaData;
import org.skyve.impl.metadata.repository.module.ModuleMetaData;
import org.skyve.impl.util.XMLMetaData;
import org.skyve.metadata.controller.ServerSideAction;
import org.skyve.metadata.controller.ServerSideActionResult;
import org.skyve.web.WebContext;

import modules.admin.DocumentCreator.SkyveScriptInterpreter;
import modules.admin.domain.DocumentCreator;

public class Submit implements ServerSideAction<DocumentCreator> {

	private static final long serialVersionUID = -9077781738031503002L;

	@Override
	public ServerSideActionResult<DocumentCreator> execute(DocumentCreator bean, WebContext webContext) throws Exception {

		if (bean.getOutputLocation() == null) {
			throw new ValidationException(new Message(DocumentCreator.outputLocationPropertyName, "Output location is required"));
		}

		parseScript(bean, webContext);

		return new ServerSideActionResult<>(bean);
	}

	@SuppressWarnings("boxing")
	private static void parseScript(DocumentCreator bean, WebContext webContext) {
		SkyveScriptInterpreter i = new SkyveScriptInterpreter(bean.getScript());
		i.preProcess();
		i.process();

		if (i.getModules().size() > 0) {
			final String sourceDirectory = bean.getOutputLocation();

			for(ModuleMetaData m : i.getModules()) {
				XMLMetaData.marshalModule(m, false, sourceDirectory);
				final String moduleDirectory = String.format("%s/modules/%s", sourceDirectory, m.getName());
						
				if (i.getDocuments().size() > 0) {
					for (DocumentMetaData d : i.getDocuments()) {
						XMLMetaData.marshalDocument(d, false, moduleDirectory);
					}
				}
			}

			webContext.growl(MessageSeverity.info, String.format("Wrote %d modules and %d documents to %s",
					i.getModules().size(),
					i.getDocuments().size(),
					bean.getOutputLocation()));
		} else {
			webContext.growl(MessageSeverity.warn, "No modules were detected, please check your script or refer to the Help tab.");
		}
	}
}