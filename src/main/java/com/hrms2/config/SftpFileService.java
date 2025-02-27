package com.hrms2.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

@Component
public class SftpFileService {

	private static final Logger logger = LoggerFactory.getLogger(SftpFileService.class);

	@Value("${sftp.ag.host}")
	private String sftpHost;

	@Value("${sftp.ag.port}")
	private int sftpPort;

	@Value("${sftp.ag.username}")
	private String sftpUser;

	@Value("${sftp.ag.password}")
	private String sftpPassword;

	@Value("${sftp.ag.remote.directory}")
	private String sftpRemoteDirectory;

	@Value("${sftp.hrms.host}")
	private String hrmsSftpHost;

	@Value("${sftp.hrms.port}")
	private int hrmsSftpPort;

	@Value("${sftp.hrms.username}")
	private String hrmsSftpUser;

	@Value("${sftp.hrms.password}")
	private String hrmsSftpPassword;

	private Session session;
	private ChannelSftp channelSftp;

	private void connect() throws JSchException {
		if (session == null || !session.isConnected()) {
			JSch jsch = new JSch();
			session = jsch.getSession(sftpUser, sftpHost, sftpPort);
			session.setPassword(sftpPassword);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();
		}
		if (channelSftp == null || !channelSftp.isConnected()) {
			channelSftp = (ChannelSftp) session.openChannel("sftp");
			channelSftp.connect();
			logger.info("SFTP session established successfully.");
		}
	}

	public void disconnect() {
		if (channelSftp != null && channelSftp.isConnected()) {
			channelSftp.disconnect();
		}
		if (session != null && session.isConnected()) {
			session.disconnect();
		}
		logger.info("SFTP session closed.");
	}

	public byte[] getExactPdfFile(String fileName) throws IOException {
		byte[] fileContent = null;
		try {
			connect(); // Ensure SFTP connection is established

			String currentYear = String.valueOf(java.time.Year.now().getValue());
			String currentMonth = java.time.Month.JANUARY.getDisplayName(java.time.format.TextStyle.SHORT,
					java.util.Locale.ENGLISH);
			String targetPath = sftpRemoteDirectory + "/" + currentYear + "/" + currentMonth + "/";

			if (!channelSftp.pwd().equals(targetPath)) {
				channelSftp.cd(targetPath);
				logger.info("📂 Navigated to SFTP directory: {}", targetPath);
			}

			// Check if the file exists before attempting to read
			try {
				SftpATTRS attrs = channelSftp.lstat(fileName);
				if (attrs != null) {
					logger.info("✅ File found: {} (Size: {} bytes)", fileName, attrs.getSize());

					// Read file content
					try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
						channelSftp.get(fileName, outputStream);
						fileContent = outputStream.toByteArray();
						logger.info("📄 Successfully retrieved PDF file: {}", fileName);
					}
				}
			} catch (SftpException e) {
				if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
					logger.warn("⚠️ File does not exist, skipping: {}", fileName);
				} else {
					logger.error("❌ Error retrieving file: {}", fileName, e);
				}
			}

		} catch (SftpException e) {
			logger.error("❌ Error accessing SFTP directory: {}", e.getMessage(), e);
		} catch (JSchException e) {
			logger.error("❌ SFTP connection error: {}", e.getMessage(), e);
		}
		return fileContent; // Returns null if file is missing
	}

	/**
	 * Upload multiple files in a batch to the SFTP server.
	 */
	public boolean batchUploadFiles(String host, int port, String user, String password, String remotePath,
			Map<String, byte[]> files) throws IOException {
		boolean allFilesUploaded = true;

		try {
			connect(); // Establish SFTP connection

			ensureDirectoryExists(remotePath);

			for (Map.Entry<String, byte[]> entry : files.entrySet()) {
				String fileName = entry.getKey();
				byte[] fileData = entry.getValue();
				try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileData)) {
					channelSftp.put(inputStream, remotePath + "/" + fileName);
					logger.info("✅ File uploaded: {}", fileName);
				} catch (SftpException e) {
					logger.error("❌ Failed to upload file: {}", fileName, e);
					allFilesUploaded = false;
				}
			}
		} catch (JSchException | SftpException e) {
			logger.error("❌ Batch upload failed: {}", e.getMessage(), e);
			allFilesUploaded = false;
		}

		return allFilesUploaded;
	}

	/**
	 * Upload files to AG SFTP.
	 */
	public boolean batchUploadFilesToAg(String remotePath, Map<String, byte[]> files) throws IOException {
		return batchUploadFiles(sftpHost, sftpPort, sftpUser, sftpPassword, remotePath, files);
	}

	/**
	 * Upload files to HRMS SFTP.
	 */
	public boolean batchUploadFilesToHrms(String remotePath, Map<String, byte[]> files) throws IOException {
		return batchUploadFiles(hrmsSftpHost, hrmsSftpPort, hrmsSftpUser, hrmsSftpPassword, remotePath, files);
	}

	/**
	 * Create an SFTP session.
	 */
	@SuppressWarnings("unused")
	private Session createSftpSession(String host, int port, String user, String password) throws JSchException {
		JSch jsch = new JSch();
		Session session = jsch.getSession(user, host, port);
		session.setPassword(password);
		session.setConfig("StrictHostKeyChecking", "no");
		logger.info("🔌 Establishing SFTP session with {}:{}...", host, port);
		return session;
	}

	/**
	 * Ensure that the directory exists on the SFTP server.
	 */
	private void ensureDirectoryExists(String remotePath) throws SftpException {
		try {
			channelSftp.cd(remotePath);
		} catch (SftpException e) {
			channelSftp.mkdir(remotePath);
			channelSftp.cd(remotePath);
		}
	}
}
