package typeservice;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import fi.csc.chipster.auth.model.Role;
import fi.csc.chipster.filebroker.FileResourceTest;
import fi.csc.chipster.rest.Config;
import fi.csc.chipster.rest.RestUtils;
import fi.csc.chipster.rest.TestServerLauncher;
import fi.csc.chipster.sessiondb.RestException;
import fi.csc.chipster.sessiondb.SessionDbClient;
import fi.csc.chipster.sessiondb.model.Dataset;

public class TypeTagResourceTest {
	
	private static TestServerLauncher launcher;
	private static WebTarget fileBrokerTarget;
	private static SessionDbClient sessionDbClient;
	private static UUID sessionId;
	private static WebTarget typeServiceTarget1;
	private static UUID tsvDatasetId;
	private static UUID pngDatasetId;
	private static WebTarget typeServiceTarget2;

    @BeforeClass
    public static void setUp() throws Exception {
    	Config config = new Config();
    	launcher = new TestServerLauncher(config);

    	sessionDbClient = new SessionDbClient(launcher.getServiceLocator(), launcher.getUser1Token());
		
        fileBrokerTarget = launcher.getUser1Target(Role.FILE_BROKER);
        typeServiceTarget1 = launcher.getUser1Target(Role.TYPE_SERVICE);
        typeServiceTarget2 = launcher.getUser2Target(Role.TYPE_SERVICE);
        
        sessionId = sessionDbClient.createSession(RestUtils.getRandomSession());   
        
        Dataset tsv = RestUtils.getRandomDataset();
        tsv.setName("file.tsv");
        Dataset png = RestUtils.getRandomDataset();
        png.setName("file.png");
        
        tsvDatasetId = sessionDbClient.createDataset(sessionId, tsv);
        pngDatasetId = sessionDbClient.createDataset(sessionId, png);
        
        FileResourceTest.uploadInputStream(fileBrokerTarget, sessionId, tsvDatasetId, IOUtils.toInputStream("chip.1	chip.2"));
        FileResourceTest.uploadInputStream(fileBrokerTarget, sessionId, pngDatasetId, IOUtils.toInputStream("abc"));
    }

    @AfterClass
    public static void tearDown() throws Exception {
    	launcher.stop();
    }
    
	@Test
    public void getAll() throws RestException, JsonParseException, JsonMappingException, IOException {
						
		Response resp = typeServiceTarget1.path("sessions").path(sessionId.toString()).request().get();
		assertEquals(200, resp.getStatus());
		String json = IOUtils.toString((InputStream) resp.getEntity());
		
		// test that both datasets were typed
		assertEquals(true, json.contains(tsvDatasetId.toString()));
		assertEquals(true, json.contains(pngDatasetId.toString()));
		
		// check fast tags
		assertEquals(true, json.contains("TSV"));
		assertEquals(true, json.contains("PNG"));						
		
		// check slow tags
		assertEquals(true, json.contains("GENE_EXPRS"));
    }
	
	@Test
    public void getOne() throws RestException, JsonParseException, JsonMappingException, IOException {
						
		Response resp = typeServiceTarget1
				.path("sessions").path(sessionId.toString())
				.path("datasets").path(pngDatasetId.toString())
				.request().get();
		
		assertEquals(200, resp.getStatus());
		String json = IOUtils.toString((InputStream) resp.getEntity());
		
		assertEquals(true, json.contains(pngDatasetId.toString()));
		assertEquals(true, json.contains("PNG"));						
    }
	
	@Test
    public void udpate() throws RestException, JsonParseException, JsonMappingException, IOException {
						
        Dataset dataset = RestUtils.getRandomDataset();
        dataset.setName("file.png");     
        UUID datasetId = sessionDbClient.createDataset(sessionId, dataset);
                
		Response resp = typeServiceTarget1
				.path("sessions").path(sessionId.toString())
				.path("datasets").path(datasetId.toString())
				.request().get();
		
		String json = IOUtils.toString((InputStream) resp.getEntity());		
		assertEquals(true, json.contains("PNG"));
		
		// rename the file name and check that the file type is changed
		dataset.setName("file.txt");
		sessionDbClient.updateDataset(sessionId, dataset);
		
		resp = typeServiceTarget1
				.path("sessions").path(sessionId.toString())
				.path("datasets").path(datasetId.toString())
				.request().get();
		
		json = IOUtils.toString((InputStream) resp.getEntity());		
		assertEquals(false, json.contains("PNG"));
		assertEquals(true, json.contains("TEXT"));
    }
	
	@Test
    public void getWrongUser() throws FileNotFoundException, RestException {
		Response resp = typeServiceTarget2
				.path("sessions").path(sessionId.toString())
				.path("datasets").path(pngDatasetId.toString())
				.request().get();
		
		assertEquals(403, resp.getStatus());
    }
	
	@Test
    public void getAuthFail() throws RestException, IOException {
		
		Response resp = launcher.getAuthFailTarget(Role.TYPE_SERVICE)
				.path("sessions").path(sessionId.toString())
				.path("datasets").path(pngDatasetId.toString())
				.request().get();
				
		System.out.println(IOUtils.toString((InputStream) resp.getEntity()));
		assertEquals(401, resp.getStatus());
		
    }
	
	@Test
    public void getTokenFail() throws FileNotFoundException, RestException {
		
		Response resp = launcher.getTokenFailTarget(Role.TYPE_SERVICE)
				.path("sessions").path(sessionId.toString())
				.path("datasets").path(pngDatasetId.toString())
				.request().get();
		
		assertEquals(403, resp.getStatus());				
    }
	
	@Test
    public void getUnparseableToken() throws FileNotFoundException, RestException {
		Response resp = launcher.getUnparseableTokenTarget(Role.TYPE_SERVICE)
				.path("sessions").path(sessionId.toString())
				.path("datasets").path(pngDatasetId.toString())
				.request().get();
		
		assertEquals(401, resp.getStatus());				
    }
}
