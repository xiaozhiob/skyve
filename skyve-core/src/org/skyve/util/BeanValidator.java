package org.skyve.util;

import org.skyve.domain.Bean;
import org.skyve.domain.messages.DomainException;
import org.skyve.domain.messages.UniqueConstraintViolationException;
import org.skyve.domain.messages.ValidationException;
import org.skyve.impl.util.ValidationUtil;
import org.skyve.domain.messages.Message;
import org.skyve.metadata.MetaDataException;
import org.skyve.metadata.customer.Customer;
import org.skyve.metadata.model.document.Bizlet;
import org.skyve.metadata.model.document.Document;

/**
 * 
 */
public class BeanValidator {
	/**
	 * Disallow instantiation
	 */
	private BeanValidator() {
		// nothing to see here
	}
	
	/**
	 * Validate a document instance against its metadata.
	 * 
	 * @param document The document to validate.
	 * @param bean The bean to validate.
	 * @param e The exception to populate.
	 * @throws ValidationException Thrown if validation or access errors are encountered.
	 * @throws MetaDataException	Thrown if the error message cannot access the attributes converter.
	 */
	public static void validateBeanAgainstDocument(Document document, Bean bean) 
	throws ValidationException, MetaDataException {
		ValidationUtil.validateBeanAgainstDocument(document, bean);
	}

	/**
	 * Validate a bean against its bizlet .validate().
	 * NB This validation method does NOT recursively validate using bizlets through
	 * the base document hierarchy as the bizlet class should be arranged in such a way as to extend the
	 * bizlet methods required of the base bizlet classes through the standard java extension mechanism.
	 * 
	 * @param bizlet	To validate against
	 * @param bean	To validate
	 * @throws ValidationException
	 */
	public static <T extends Bean> void validateBeanAgainstBizlet(Bizlet<T> bizlet, T bean) 
	throws ValidationException {
		ValidationUtil.validateBeanAgainstBizlet(bizlet, bean);
	}

	/**
	 * Updates the bindings in the error message (and its children) 
	 * based on the binding of the validatedBean in relation to the masterBean.
	 * 
	 * @param customer
	 * @param e
	 * @param masterBean
	 * @param validatedBean
	 * @throws DomainException
	 * @throws MetaDataException
	 */
	public static void processErrorMessageBindings(Customer customer,
													final Message e,
													Bean masterBean,
													final Bean validatedBean) 
	throws DomainException, MetaDataException {
		ValidationUtil.processErrorMessageBindings(customer, e, masterBean, validatedBean);
	}

	/**
	 * 
	 * @param customer
	 * @param document
	 * @param bean
	 * @throws UniqueConstraintViolationException
	 * @throws ValidationException
	 */
	public static void checkCollectionUniqueConstraints(Customer customer, Document document, Bean bean)
	throws UniqueConstraintViolationException, ValidationException {
		ValidationUtil.checkCollectionUniqueConstraints(customer, document, bean);
	}
}