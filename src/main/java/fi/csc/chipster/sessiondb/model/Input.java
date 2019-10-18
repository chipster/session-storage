package fi.csc.chipster.sessiondb.model;

import javax.persistence.Lob;

import fi.csc.chipster.rest.hibernate.DeepCopyable;

public class Input implements DeepCopyable {
	
	public static final String INPUT_LIST_JSON_TYPE = "InputListJsonType";

	private String inputId;
	private String displayName;
	@Lob
	private String description;
	private String type;
	private String datasetId;
		
	public String getInputId() {
		return inputId;
	}
	public void setInputId(String id) {
		this.inputId = id;
	}
	public String getDisplayName() {
		return displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getDatasetId() {
		return datasetId;
	}
	public void setDatasetId(String datasetId) {
		this.datasetId = datasetId;
	}
	@Override
	public Object deepCopy() {
		return deepCopy(new Input());
		
	}
	public <T extends Input> T deepCopy(T i) {
		i.setInputId(inputId);
		i.setDisplayName(displayName);
		i.setDescription(description);
		i.setType(type);
		i.setDatasetId(datasetId);
		return i;
	}
}
