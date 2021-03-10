package org.skyve.impl.web.faces.beans;

import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.RequestScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;
import org.skyve.CORE;
import org.skyve.cache.ConversationUtil;
import org.skyve.content.AttachmentContent;
import org.skyve.domain.Bean;
import org.skyve.impl.bind.BindUtil;
import org.skyve.impl.generate.SmartClientGenerateUtils;
import org.skyve.impl.util.UtilImpl;
import org.skyve.impl.web.AbstractWebContext;
import org.skyve.impl.web.faces.FacesAction;
import org.skyve.persistence.Persistence;

@ManagedBean(name = "_skyveContent")
@RequestScoped
public class ContentUpload extends Localisable {
	private static final long serialVersionUID = -6769960348990922565L;

	@ManagedProperty(value = "#{param." + AbstractWebContext.CONTEXT_NAME + "}")
    private String context;
    
    @ManagedProperty(value = "#{param." + AbstractWebContext.BINDING_NAME + "}")
    private String binding;

    @ManagedProperty(value = "#{param." + AbstractWebContext.RESOURCE_FILE_NAME + "}")
    private String contentBinding;

    private String croppedDataUrl;
    private String croppedFileName;
    
	public void preRender() {
		new FacesAction<Void>() {
			@Override
			public Void callback() throws Exception {
				initialise();
				
				return null;
			}
		}.execute();
	}

    public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = UtilImpl.processStringValue(context);
	}

	public String getBinding() {
		return binding;
	}

	public void setBinding(String binding) {
		this.binding = UtilImpl.processStringValue(binding);
	}

	public String getContentBinding() {
		return contentBinding;
	}

	public void setContentBinding(String contentBinding) {
		this.contentBinding = UtilImpl.processStringValue(contentBinding);
	}

	public String getCroppedDataUrl() {
		return croppedDataUrl;
	}

	public void setCroppedDataUrl(String croppedDataUrl) {
		this.croppedDataUrl = croppedDataUrl;
	}

	public String getCroppedFileName() {
		return croppedFileName;
	}

	public void setCroppedFileName(String croppedFileName) {
		this.croppedFileName = croppedFileName;
	}

	/**
	 * Process the file upload directly from the PF file upload component
	 * 
	 * @param event
	 */
	public void handleFileUpload(FileUploadEvent event)
	throws Exception {
		UploadedFile file = event.getFile();
		upload(file.getFileName(), file.getContents());
	}
	
	/**
	 * Process the file upload from the croppie plugin.
	 * For use as a remote command with a hidden populated with a data url
	 */
	public void uploadCropped()
	throws Exception {
		String base64 = croppedDataUrl.substring(croppedDataUrl.indexOf(','));
		Base64 base64Codec = new Base64();
		upload(croppedFileName, base64Codec.decode(base64));
	}

	private void upload(String fileName, byte[] fileContents)
	throws Exception {
		FacesContext fc = FacesContext.getCurrentInstance();

		if ((context == null) || (contentBinding == null)) {
			UtilImpl.LOGGER.warning("FileUpload - Malformed URL on Upload Action - context, binding, or contentBinding is null");
			FacesMessage msg = new FacesMessage("Failure", "Malformed URL");
	        fc.addMessage(null, msg);
	        return;
		}

		ExternalContext ec = fc.getExternalContext();
		HttpServletRequest request = (HttpServletRequest) ec.getRequest();
		HttpServletResponse response = (HttpServletResponse) ec.getResponse();

		AbstractWebContext webContext = ConversationUtil.getCachedConversation(context, request, response);
		if (webContext == null) {
			UtilImpl.LOGGER.warning("FileUpload - Malformed URL on Content Upload - context does not exist");
			FacesMessage msg = new FacesMessage("Failure", "Malformed URL");
	        FacesContext.getCurrentInstance().addMessage(null, msg);
	        return;
		}

		// NB Persistence has been set with the restore processing inside the SkyvePhaseListener
		Persistence persistence = CORE.getPersistence();
		try {
			Bean currentBean = webContext.getCurrentBean();
			Bean bean = currentBean;

			if (binding != null) {
				bean = (Bean) BindUtil.get(bean, binding);
			}
			
			AttachmentContent content = FacesContentUtil.handleFileUpload(fileName, fileContents, bean, BindUtil.unsanitiseBinding(contentBinding));
			String contentId = content.getContentId();

			// only put conversation in cache if we have been successful in executing
			ConversationUtil.cacheConversation(webContext);
			
			// update the content UUID value on the client and popoff the window on the stack
			StringBuilder js = new StringBuilder(128);
			String sanitisedContentBinding = BindUtil.sanitiseBinding(contentBinding);
			// if top.isc is defined then we are using smart client, set the value in the values manager
			js.append("if(top.isc){");
			js.append("top.isc.WindowStack.getOpener()._vm.setValue('").append(sanitisedContentBinding);
			js.append("','").append(contentId).append("');top.isc.WindowStack.popoff(false)");
			// otherwise we are using prime faces, set the hidden input element that ends with "_<binding>"
			js.append("}else if(top.SKYVE){if(top.SKYVE.PF){top.SKYVE.PF.afterContentUpload('").append(sanitisedContentBinding);
			js.append("','").append(contentId).append("','");
			js.append(bean.getBizModule()).append('.').append(bean.getBizDocument()).append("','");
			js.append(SmartClientGenerateUtils.processString(content.getFileName(), false, false)).append("')}}");
			PrimeFaces.current().executeScript(js.toString());
		}
		catch (Exception e) {
			persistence.rollback();
			e.printStackTrace();
			FacesMessage msg = new FacesMessage("Failure", e.getMessage());
	        fc.addMessage(null, msg);
		}
		// NB No need to disconnect Persistence as it is done in the SkyvePhaseListener after the response is rendered.
    }
}
