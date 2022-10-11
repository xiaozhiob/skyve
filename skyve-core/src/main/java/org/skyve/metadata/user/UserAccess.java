package org.skyve.metadata.user;

import org.skyve.CORE;
import org.skyve.metadata.customer.Customer;
import org.skyve.metadata.model.Extends;
import org.skyve.metadata.model.document.Document;
import org.skyve.metadata.module.Module;

public class UserAccess implements Comparable<UserAccess> {
	private char type;
	private String moduleName;
	private String documentName;
	private String component;
	
	private UserAccess(char type, String moduleName, String documentName, String component) {
		this.type = type;
		this.moduleName = moduleName;
		this.documentName = documentName;
		this.component = component;
	}

	public static UserAccess singular(String moduleName, String documentName) {
		return new UserAccess('S', moduleName, documentName, null);
	}
	
	public static UserAccess documentAggregate(String moduleName, String documentName) {
		return new UserAccess('A', moduleName, null, documentName);
	}

	public static UserAccess queryAggregate(String moduleName, String queryName) {
		return new UserAccess('A', moduleName, null, queryName);
	}

	public static UserAccess modelAggregate(String moduleName, String documentName, String modelName) {
		return new UserAccess('A', moduleName, documentName, modelName);
	}

	public static UserAccess previousComplete(String moduleName, String documentName, String binding) {
		return new UserAccess('C', moduleName, documentName, binding);
	}
	
	public boolean isSingular() {
		return (type == 'S');
	}
	
	public String getModuleName() {
		return moduleName;
	}

	public String getDocumentName() {
		return documentName;
	}
	
	public UserAccess determineSingularBaseDocument() {
		Customer c = CORE.getCustomer();
		Module m = c.getModule(moduleName);
		Document d = m.getDocument(c, documentName);
		Extends e = d.getExtends();
		if (e == null) {
			return null;
		}
		d = m.getDocument(c, e.getDocumentName());
		return UserAccess.singular(d.getOwningModuleName(), documentName);
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == null) {
			return false;
		}
		return toString().equals(other.toString());
	}
	
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder(64);
		result.append(type);
		result.append(moduleName);
		if (documentName != null) {
			result.append('.');
			result.append(documentName);
		}
		if (component != null) {
			result.append('^');
			result.append(component);
		}		
		return result.toString();
	}
	
	@Override
	public int compareTo(UserAccess other) {
		return toString().compareTo(other.toString());
	}
}