package fi.csc.chipster.sessionworker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import fi.csc.chipster.sessiondb.model.Dataset;
import fi.csc.chipster.sessiondb.model.Session;

public class ExtractedSession {

	private Session session;
	private HashMap<UUID, UUID> datasetIdMap;
	private Map<UUID, Dataset> datasetMap;

	public ExtractedSession(Session session, HashMap<UUID, UUID> datasetIdMap, Map<UUID, Dataset> datasetMap) {
		this.setSession(session);
		this.datasetMap = datasetMap;
		this.setDatasetIdMap(datasetIdMap);
	}

	public HashMap<UUID, UUID> getDatasetIdMap() {
		return datasetIdMap;
	}

	public void setDatasetIdMap(HashMap<UUID, UUID> datasetIdMap) {
		this.datasetIdMap = datasetIdMap;
	}

	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	public Map<UUID, Dataset> getDatasetMap() {
		return datasetMap;
	}
}
