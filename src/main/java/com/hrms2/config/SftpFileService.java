package com.hrms2.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
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
	 * Get the list of current month's PDF files.
	 */
	public List<String> getCurrentMonthPdfFileNames() {
		List<String> pdfFiles = new ArrayList<>();
		Session session = null;
		ChannelSftp channelSftp = null;

		try {
			String currentYear = String.valueOf(java.time.Year.now().getValue());
			String currentMonth = Month.JANUARY.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
			String targetPath = sftpRemoteDirectory + "/" + currentYear + "/" + currentMonth + "/";

			session = createSftpSession(sftpHost, sftpPort, sftpUser, sftpPassword);
			channelSftp = (ChannelSftp) session.openChannel("sftp");
			channelSftp.connect();

			try {
				channelSftp.cd(targetPath);
			} catch (SftpException e) {
				logger.warn("Directory not found: {}", targetPath);
				return pdfFiles;
			}

			@SuppressWarnings("unchecked")
			Vector<ChannelSftp.LsEntry> fileList = channelSftp.ls("*.pdf");
			for (ChannelSftp.LsEntry entry : fileList) {
				pdfFiles.add(entry.getFilename());
			}
		} catch (JSchException | SftpException e) {
			logger.error("Error while accessing SFTP server: {}", e.getMessage(), e);
		} finally {
			closeConnections(channelSftp, session);
		}
		return pdfFiles;
	}

	/**
	 * Upload multiple files in a batch to the SFTP server.
	 * 
	 * @throws IOException
	 */
	public boolean batchUploadFiles(String host, int port, String user, String password, String remotePath,
			Map<String, byte[]> files) throws IOException {
		Session session = null;
		ChannelSftp channelSftp = null;
		boolean allFilesUploaded = true;

		try {
			session = createSftpSession(host, port, user, password);
			channelSftp = (ChannelSftp) session.openChannel("sftp");
			channelSftp.connect();

			ensureDirectoryExists(channelSftp, remotePath);

			for (Map.Entry<String, byte[]> entry : files.entrySet()) {
				String fileName = entry.getKey();
				byte[] fileData = entry.getValue();
				try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileData)) {
					channelSftp.put(inputStream, fileName);
					logger.info("File uploaded: {}", fileName);
				} catch (SftpException e) {
					logger.error("Failed to upload file: {}", fileName, e);
					allFilesUploaded = false;
				}
			}
		} catch (JSchException | SftpException e) {
			logger.error("Batch upload failed: {}", e.getMessage(), e);
			allFilesUploaded = false;
		} finally {
			closeConnections(channelSftp, session);
		}
		return allFilesUploaded;
	}

	/**
	 * Upload files to AG SFTP.
	 * 
	 * @throws IOException
	 */
	public boolean batchUploadFilesToAg(String remotePath, Map<String, byte[]> files) throws IOException {
		return batchUploadFiles(sftpHost, sftpPort, sftpUser, sftpPassword, remotePath, files);
	}

	/**
	 * Upload files to HRMS SFTP.
	 * 
	 * @throws IOException
	 */
	public boolean batchUploadFilesToHrms(String remotePath, Map<String, byte[]> files) throws IOException {
		return batchUploadFiles(hrmsSftpHost, hrmsSftpPort, hrmsSftpUser, hrmsSftpPassword, remotePath, files);
	}

	/**
	 * Create an SFTP session.
	 */
	private Session createSftpSession(String host, int port, String user, String password) throws JSchException {
		JSch jsch = new JSch();
		Session session = jsch.getSession(user, host, port);
		session.setPassword(password);
		session.setConfig("StrictHostKeyChecking", "no");
		session.connect();
		logger.info("SFTP session established successfully with {}:{}", host, port);
		return session;
	}

	/**
	 * Ensure that the directory exists on the SFTP server.
	 */
	private void ensureDirectoryExists(ChannelSftp channelSftp, String remotePath) throws SftpException {
		try {
			channelSftp.cd(remotePath);
		} catch (SftpException e) {
			channelSftp.mkdir(remotePath);
			channelSftp.cd(remotePath);
		}
	}

	/**
	 * Close SFTP connections.
	 */
	private void closeConnections(ChannelSftp channelSftp, Session session) {
		if (channelSftp != null) {
			channelSftp.disconnect();
		}
		if (session != null) {
			session.disconnect();
		}
	}
}
