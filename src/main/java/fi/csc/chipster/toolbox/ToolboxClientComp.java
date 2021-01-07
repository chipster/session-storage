package fi.csc.chipster.toolbox;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import fi.csc.chipster.rest.RestUtils;

public class ToolboxClientComp {

	private String baseUri;
	private Client client;

	private final static String MODULES_ZIP_PATH = "/modules/zip";
	
	// set file mode to 755 for these file types when unzipping modules
	private final static String[] executableExtensions = { "sh", "bash", "py" };
	
	private static Logger logger = LogManager.getLogger();
	
	
	public ToolboxClientComp(String toolboxUri) {
		this.baseUri = toolboxUri;
		this.client = ClientBuilder.newClient();
	}

	public ToolboxTool getTool(String toolId) throws IOException {

		WebTarget serviceTarget = client.target(baseUri).path("tools/" + toolId);

		String json; 
		try {
			json = serviceTarget.request(MediaType.APPLICATION_JSON).get(String.class);
		} catch (NotFoundException nfe) {
			return null;
		}

		ToolboxTool tool = RestUtils.parseJson(ToolboxTool.class, json, false);

		return tool;
	}

	public void close() {
		client.close();
	}

	
	public void getToolboxModules(File jobToolboxDir) throws IOException {
		long startTime = System.currentTimeMillis();

		unzip(baseUri + MODULES_ZIP_PATH, jobToolboxDir);
		fixPermissions(jobToolboxDir);

		logger.info("get toolbox took " + (System.currentTimeMillis() - startTime) + " ms");
	}
	
	
	private void fixPermissions(File jobToolboxDir) throws IOException {
		Files.walkFileTree(jobToolboxDir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				for (String extension : executableExtensions) {
					if (file.getFileName().toString().endsWith("." + extension)) {
						logger.debug("fixing permissions for " + file.getFileName());
						Files.setPosixFilePermissions(file, ToolboxClientComp.get755Permissions());
					}
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void unzip(String zipFilePath, File destDirectory) throws IOException {

		File destDir = destDirectory;
		if (!destDir.exists()) {
			destDir.mkdir();
		}

		URL url = new URL(zipFilePath);

		try (ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(url.openStream(), 1024))) {
			ZipEntry entry = zipIn.getNextEntry();

			// iterates over entries in the zip file
			while (entry != null) {
				String filePath = destDirectory + File.separator + entry.getName();
				if (!entry.isDirectory()) {
					// if the entry is a file, extracts it
					extractFile(zipIn, filePath);
				} else {
					// if the entry is a directory, make the directory
					File dir = new File(filePath);
					dir.mkdir();
				}
				zipIn.closeEntry();
				entry = zipIn.getNextEntry();
			}
			zipIn.close();
		}
	}

	/**
	 * Extracts a zip entry (file entry)
	 * @param zipIn
	 * @param filePath
	 * @throws IOException
	 */
	private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
		try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
			byte[] bytesIn = new byte[4096];
			int read = 0;
			while ((read = zipIn.read(bytesIn)) != -1) {
				bos.write(bytesIn, 0, read);
			}
		}
	}
	
	public static Set<PosixFilePermission> get755Permissions() {
		Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();

		//add owners permission
		perms.add(PosixFilePermission.OWNER_READ);
		perms.add(PosixFilePermission.OWNER_WRITE);
		perms.add(PosixFilePermission.OWNER_EXECUTE);
		
		//add group permissions
		perms.add(PosixFilePermission.GROUP_READ);
		perms.add(PosixFilePermission.GROUP_EXECUTE);

		//add others permissions
		perms.add(PosixFilePermission.OTHERS_READ);
		perms.add(PosixFilePermission.OTHERS_EXECUTE);

		return perms;
	}
	
	public static void main(String args[]) throws JsonParseException, JsonMappingException, IOException {
		
		ToolboxClientComp toolboxClient = new ToolboxClientComp("http://localhost:8008/toolbox");
		try {
			ToolboxTool tool = toolboxClient.getTool("norm-affy.R");
		
			System.out.println(tool.getSadlString());
		} finally {
			toolboxClient.close();
		}
	}

}
