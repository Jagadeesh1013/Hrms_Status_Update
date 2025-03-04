package com.hrms2.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
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

	/**
	 * Opens a new SFTP session and returns an active ChannelSftp instance.
	 */
	public ChannelSftp openAgSftpSession() {
		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(sftpUser, sftpHost, sftpPort);
			session.setPassword(sftpPassword);

			// Set session configurations
			Properties config = new Properties();
			config.put("StrictHostKeyChecking", "no");
			session.setConfig(config);
			session.connect();

			logger.info("✅ AG SFTP Session connected successfully.");

			ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
			channelSftp.connect();
			logger.info("✅ AG SFTP Channel opened successfully.");

			return channelSftp;
		} catch (Exception e) {
			logger.error("❌ Failed to open AG SFTP session: {}", e.getMessage());
			return null;
		}
	}

	public ChannelSftp openHrmsSftpSession() {
		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(hrmsSftpUser, hrmsSftpHost, hrmsSftpPort);
			session.setPassword(hrmsSftpPassword);

			// Set session configurations
			Properties config = new Properties();
			config.put("StrictHostKeyChecking", "no");
			session.setConfig(config);
			session.connect();

			logger.info("✅ HRMS SFTP Session connected successfully.");

			ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
			channelSftp.connect();
			logger.info("✅ HRMS SFTP Channel opened successfully.");

			return channelSftp;
		} catch (Exception e) {
			logger.error("❌ Failed to open HRMS SFTP session: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Closes the SFTP session and channel.
	 */
	public void closeAgSftpSession(ChannelSftp channelSftp) {
		if (channelSftp != null) {
			try {
				if (channelSftp.isConnected()) {
					channelSftp.disconnect();
					logger.info("✅ AG SFTP Channel disconnected.");
				}
				if (channelSftp.getSession().isConnected()) {
					channelSftp.getSession().disconnect();
					logger.info("✅ AG SFTP Session disconnected.");
				}
			} catch (Exception e) {
				logger.error("❌ Error closing AG SFTP session: {}", e.getMessage());
			}
		}
	}

	public void closeHrmsSftpSession(ChannelSftp channelSftp) {
		if (channelSftp != null) {
			try {
				if (channelSftp.isConnected()) {
					channelSftp.disconnect();
					logger.info("✅ HRMS SFTP Channel disconnected.");
				}
				if (channelSftp.getSession().isConnected()) {
					channelSftp.getSession().disconnect();
					logger.info("✅ HRMS SFTP Session disconnected.");
				}
			} catch (Exception e) {
				logger.error("❌ Error closing HRMS SFTP session: {}", e.getMessage());
			}
		}
	}

	/**
	 * Uploads multiple JSON files to the specified SFTP path.
	 */
	public boolean batchUploadJsonToSftp(ChannelSftp channelSftp, String targetPath,
			Map<String, InputStream> jsonFilesStreamMap) {
		try {
			for (Map.Entry<String, InputStream> entry : jsonFilesStreamMap.entrySet()) {
				String fileName = entry.getKey();
				InputStream fileStream = entry.getValue();

				channelSftp.put(fileStream, targetPath + "/" + fileName);
				logger.info("✅ Uploaded JSON: {}", fileName);
			}
			return true;
		} catch (Exception e) {
			logger.error("❌ Failed to upload JSONs: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Uploads multiple PDF files to the specified SFTP path.
	 */
	public boolean batchUploadPdfsToSftp(ChannelSftp channelSftp, String targetPath,
			Map<String, InputStream> pdfFilesMap) {
		try {
			for (Map.Entry<String, InputStream> entry : pdfFilesMap.entrySet()) {
				String fileName = entry.getKey();
				InputStream fileStream = entry.getValue();

				channelSftp.put(fileStream, targetPath + "/" + fileName);
				logger.info("✅ Uploaded PDF: {}", fileName);
			}
			return true;
		} catch (Exception e) {
			logger.error("❌ Failed to upload PDFs: {}", e.getMessage());
			return false;
		}
	}

	public InputStream getExactPdfFileAsStream(ChannelSftp channelSftp, String fileName) {

		byte[] fileBytes = null;
		try {
			// 🔹 Construct the target path dynamically (MM format for month)
			String currentYear = String.valueOf(java.time.Year.now().getValue());
			String currentMonth = java.time.Month.JANUARY.getDisplayName(java.time.format.TextStyle.SHORT,
					java.util.Locale.ENGLISH);
			String targetPath = sftpRemoteDirectory + "/" + currentYear + "/" + currentMonth + "/";

			// 🔹 Change directory only if necessary
			if (!channelSftp.pwd().equals(targetPath)) {
				channelSftp.cd(targetPath);
				logger.info("📂 Navigated to SFTP directory: {}", targetPath);
			}

			// 🔹 Check if file exists
			SftpATTRS attrs;
			try {
				attrs = channelSftp.lstat(fileName);
			} catch (SftpException e) {
				logger.warn("⚠️ File not found: {}", fileName);
				return null; // Simply return null (NO LOOPING)
			}

			// 🔹 Ensure file is NOT empty
			if (attrs.getSize() == 0) {
				logger.warn("⚠️ File exists but is empty: {}", fileName);
				return null;
			}

			// 🔹 Read file into byte[]
			try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
				channelSftp.get(fileName, outputStream);
				fileBytes = outputStream.toByteArray();

				if (fileBytes.length == 0) {
					logger.warn("⚠️ Retrieved file is empty: {}", fileName);
					return null;
				}

				logger.info("✅ Successfully retrieved PDF file: {} (Size: {} bytes)", fileName, fileBytes.length);

				// 🔹 Convert byte[] to InputStream and return
				return new ByteArrayInputStream(fileBytes);
			}

		} catch (SftpException e) {
			logger.error("❌ SFTP error retrieving file {}: {}", fileName, e.getMessage(), e);
		} catch (IOException e) {
			logger.error("❌ IO error while reading file {}: {}", fileName, e.getMessage(), e);
		}

		return null; // Return null if something goes wrong
	}

}
