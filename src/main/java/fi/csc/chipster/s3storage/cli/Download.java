package fi.csc.chipster.s3storage.cli;

import java.io.File;
import java.io.IOException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;

import fi.csc.chipster.auth.model.Role;
import fi.csc.chipster.filebroker.s3storageclient.S3StorageClient;
import fi.csc.chipster.rest.Config;

public class Download {

	public static void main(String args[]) throws InterruptedException, IOException {

		if (args.length != 3) {
			System.out.println("Usage: Download BUCKET OBJECT_KEY FILE");
			System.exit(1);
		}

		String bucket = args[0];
		String objectKey = args[1];
		File file = new File(args[2]);

		Config config = new Config();
		TransferManager tm = S3StorageClient.initTransferManager(config, Role.FILE_BROKER);

		try {

			download(tm, bucket, file, objectKey);

		} catch (AmazonServiceException e) {
			e.printStackTrace();
			System.err.println(e.getErrorMessage());
			System.exit(1);
		}
		tm.shutdownNow();
	}

	public static void download(TransferManager tm, String bucket, File file, String objectKey)
			throws InterruptedException {

		long t = System.currentTimeMillis();

		Transfer transfer = tm.download(bucket, objectKey, file);

		AmazonClientException exception = transfer.waitForException();
		if (exception != null) {
			throw exception;
		}

		long dt = System.currentTimeMillis() - t;

		long fileSize = file.length();

		System.out.println(
				"download " + file.getPath() + " " + (fileSize * 1000 / dt / 1024 / 1024) + " MiB/s \t" + dt
						+ " ms \t");
	}
}