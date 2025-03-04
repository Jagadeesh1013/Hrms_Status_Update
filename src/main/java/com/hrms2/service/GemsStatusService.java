package com.hrms2.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hrms2.config.SftpFileService;
import com.hrms2.entity.HrmsStatusUpdate;
import com.hrms2.repository.GemsStatusRepository;
import com.jcraft.jsch.ChannelSftp;

@Service
public class GemsStatusService {

	private static final Logger logger = LoggerFactory.getLogger(GemsStatusService.class);

	@Autowired
	private GemsStatusRepository repository;

	@Autowired
	private SftpFileService sftpFileService;

	public ResponseEntity<String> processAndSaveGemsData(String requestBody) {
		ChannelSftp channelSftp = null;
		Map<String, List<JsonNode>> groupedJsonData = new HashMap<>();
		Map<String, InputStream> matchedPdfFiles = new HashMap<>();
		String transactionId = null;
		String agSftpPdfPath = null;
		String agSftpJsonPath = null;
		String hrmsSftpPdfPath = null;
		String hrmsSftpJsonPath = null;

		String gemsId = null;
		int matchedCount = 0;
		int processingLimit = 10;

		try {
			ObjectMapper objectMapper = new ObjectMapper();
			JsonFactory jsonFactory = new JsonFactory();
			JsonParser parser = jsonFactory.createParser(requestBody);
			JsonNode rootNodeMain = objectMapper.readTree(parser);

			String jsonData = rootNodeMain.get("jsonData").asText();
			JsonParser jsonDataParser = jsonFactory.createParser(jsonData);
			JsonNode rootNode = objectMapper.readTree(jsonDataParser);

			agSftpPdfPath = rootNodeMain.get("agSftpPdfPath").asText();
			agSftpJsonPath = rootNodeMain.get("agSftpJsonPath").asText();
			hrmsSftpPdfPath = rootNodeMain.get("hrmsSftpPdfPath").asText();
			hrmsSftpJsonPath = rootNodeMain.get("hrmsSftpJsonPath").asText();

			gemsId = rootNode.path("gemsId").asText(null);
			JsonParser employeeParser = jsonFactory.createParser(rootNode.get("employeeDetails").toString());
			JsonNode employeeDetailsNode = objectMapper.readTree(employeeParser);

			if (employeeDetailsNode == null || !employeeDetailsNode.isArray() || employeeDetailsNode.isEmpty()) {
				logger.error("employeeDetails array is missing or empty in JSON!");
				return ResponseEntity.badRequest().body("Error: employeeDetails array is missing or empty.");
			}

			// Open SFTP session before processing employees
			channelSftp = sftpFileService.openAgSftpSession();
			if (channelSftp == null) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.body("Failed to establish SFTP connection.");
			}

			for (JsonNode employee : employeeDetailsNode) {
				if (matchedCount >= processingLimit) {
					logger.info("Limit reached: {} matching PDFs and JSONs found. Stopping further processing.",
							processingLimit);
					break;
				}

				String geNumber = employee.path("gerNo").asText(null);
				String fileId = employee.path("fileId").asText(null);
				String fileName = employee.path("fileName").asText(null);
				JsonNode eventDetailsNode = employee.path("eventDetails");

				if (!eventDetailsNode.isArray() || eventDetailsNode.isEmpty()) {
					logger.warn("Skipping record due to missing event details for geNumber: {}", geNumber);
					continue;
				}

				boolean alreadyProcessed = false;
				String eventId = null;
				for (JsonNode event : eventDetailsNode) {
					eventId = event.path("eventId").asText(null);
					String eventName = event.path("eventName").asText(null);

					if (geNumber == null || eventId == null || eventName == null || fileId == null
							|| fileName == null) {
						logger.warn("Skipping record due to missing fields.");
						continue;
					}

					if (repository.existsByGeNumberAndEventIdAndFileIdAndPdfFileNameAndPdfFileNameStatus(geNumber,
							eventId, fileId, fileName, "Y")) {
						logger.warn("Skipping record (Already processed) - File ID: {}, Event ID: {}", fileId, eventId);
						alreadyProcessed = true;
						break;
					}
				}

				if (alreadyProcessed) {
					continue;
				}

				// Fetch PDF file from SFTP inside a try-with-resources block to avoid memory
				// leaks
				try (InputStream exactPdfContent = sftpFileService.getExactPdfFileAsStream(channelSftp, fileName)) {
					if (exactPdfContent == null || exactPdfContent.available() == 0) {
						logger.warn("⚠️ Skipping record - No matching PDF found for: {}", fileName);
						continue;
					}

					// Store the InputStream directly in the map (used once per iteration)
					matchedPdfFiles.put(fileName, exactPdfContent);

					// Fetch oldTransactionId from DB based on eventId
					String oldTransactionId = repository.findTransactionIdByGeNumberAndEventId(geNumber, eventId);

					// Create a new JSON Object for the employee
					ObjectNode employeeJson = (ObjectNode) objectMapper.readTree(employee.toString());
					employeeJson.put("oldTransactionId", oldTransactionId);
					employeeJson.put("comments",
							"Reprocessed due to data corrections identified by the user. A new PDF has been generated; please use the updated PDF.");

					for (JsonNode event : eventDetailsNode) {
						eventId = event.path("eventId").asText(null);
						String eventName = event.path("eventName").asText(null);

						DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSS");
						transactionId = LocalDateTime.now().format(date);

						HrmsStatusUpdate newRecord = new HrmsStatusUpdate();
						newRecord.setTransactionId(transactionId);
						newRecord.setGeNumber(geNumber);
						newRecord.setEventId(eventId);
						newRecord.setEventName(eventName);
						newRecord.setFileId(fileId);
						newRecord.setPdfFileName(fileName);
						newRecord.setPdfFileNameStatus("Y");
						newRecord.setHrmsReceivedStatus("N");
						newRecord.setHrmsRejectedStatus("N");
						newRecord.setDdoReceivedStatus("N");
						newRecord.setDdoRejectedStatus("N");
						newRecord.setCreatedAt(LocalDateTime.now());

						repository.save(newRecord);
						logger.info("Inserted new record with status 'Y' - File ID: {}, Event ID: {}", fileId, eventId);
					}

					groupedJsonData.computeIfAbsent(fileName, k -> new ArrayList<>()).add(employee);
					matchedCount++;
				} catch (IOException ioException) {
					logger.error("Error processing PDF file for {}: {}", fileName, ioException.getMessage());
				}

				if (matchedCount >= processingLimit) {
					logger.info("Reached {} matching PDFs and JSONs.", processingLimit);
					break;
				}
			}
		} catch (Exception e) {
			logger.error("❌ Error processing JSON: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
		} finally {
			// **Ensure SFTP session is closed before batch upload**
			if (channelSftp != null) {
				try {
					sftpFileService.closeAgSftpSession(channelSftp);
					logger.info("🔒 SFTP session closed.");
				} catch (Exception closeException) {
					logger.error("❌ Error closing SFTP session: {}", closeException.getMessage(), closeException);
				}
			}
		}

		// **Upload JSON and PDFs in batch AFTER closing SFTP session**
		try {
			batchUploadJsonAndPdfs(groupedJsonData, transactionId, gemsId, agSftpJsonPath, agSftpPdfPath,
					hrmsSftpJsonPath, hrmsSftpPdfPath, matchedPdfFiles);
			logger.info("✅ JSON and matched PDFs successfully uploaded.");
			return ResponseEntity.ok("Processed successfully. JSON and matched PDFs uploaded.");
		} catch (Exception uploadException) {
			logger.error("❌ Error uploading JSON and PDFs: {}", uploadException.getMessage(), uploadException);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error during file upload: " + uploadException.getMessage());
		}
	}

	public void batchUploadJsonAndPdfs(Map<String, List<JsonNode>> groupedJsonData, String transactionId, String gemsId,
			String agSftpJsonPath, String agSftpPdfPath, String hrmsSftpJsonPath, String hrmsSftpPdfPath,
			Map<String, InputStream> pdfFilesMap) {
		Map<String, InputStream> jsonFilesStreamMap = generateJsonFiles(groupedJsonData, transactionId, gemsId);

		try {
			boolean agJsonUploadSuccess = false;
			boolean agPdfUploadSuccess = false;
			boolean hrmsJsonUploadSuccess = false;
			boolean hrmsPdfUploadSuccess = false;

			// Upload to AG SFTP
			ChannelSftp agSftp = sftpFileService.openAgSftpSession();
			if (agSftp != null) {
				try {
					agJsonUploadSuccess = uploadFilesToSftp(agSftp, agSftpJsonPath, jsonFilesStreamMap, true);
					agPdfUploadSuccess = uploadFilesToSftp(agSftp, agSftpPdfPath, pdfFilesMap, false);
					logBatchUploadStatus(agJsonUploadSuccess, agPdfUploadSuccess, "AG SFTP");
				} finally {
					sftpFileService.closeAgSftpSession(agSftp);
				}
			} else {
				logger.error("Failed to open AG SFTP session.");
			}

			// Upload to HRMS SFTP
			ChannelSftp hrmsSftp = sftpFileService.openHrmsSftpSession();
			if (hrmsSftp != null) {
				try {
					hrmsJsonUploadSuccess = uploadFilesToSftp(hrmsSftp, hrmsSftpJsonPath, jsonFilesStreamMap, true);
					hrmsPdfUploadSuccess = uploadFilesToSftp(hrmsSftp, hrmsSftpPdfPath, pdfFilesMap, false);
					logBatchUploadStatus(hrmsJsonUploadSuccess, hrmsPdfUploadSuccess, "HRMS SFTP");
				} finally {
					sftpFileService.closeHrmsSftpSession(hrmsSftp);
				}
			} else {
				logger.error("Failed to open HRMS SFTP session.");
			}

			// Update database if all uploads succeed
			updateDatabaseAfterUpload(transactionId, agJsonUploadSuccess, agPdfUploadSuccess, hrmsJsonUploadSuccess,
					hrmsPdfUploadSuccess);

		} catch (Exception e) {
			logger.error("Error processing and uploading JSON to SFTP: {}", e.getMessage(), e);
		} finally {
			closeInputStreams(jsonFilesStreamMap);
		}
	}

	/**
	 * Generates JSON InputStreams from grouped employee data.
	 */
	private Map<String, InputStream> generateJsonFiles(Map<String, List<JsonNode>> groupedJsonData,
			String transactionId, String gemsId) {
		Map<String, InputStream> jsonFilesStreamMap = new HashMap<>();
		ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

		try {
			for (Map.Entry<String, List<JsonNode>> entry : groupedJsonData.entrySet()) {
				String fileName = entry.getKey();
				List<JsonNode> matchedEmployees = entry.getValue();

				ObjectNode groupedJson = objectMapper.createObjectNode();
				groupedJson.put("transactionId", transactionId);
				groupedJson.put("gemsId", gemsId);
				ArrayNode groupedArrayNode = objectMapper.createArrayNode();
				matchedEmployees.forEach(groupedArrayNode::add);
				groupedJson.set("employeeDetails", groupedArrayNode);

				String jsonFileName = fileName.replace(".pdf", ".json");
				String formattedJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(groupedJson);

				InputStream jsonInputStream = new ByteArrayInputStream(formattedJson.getBytes(StandardCharsets.UTF_8));
				jsonFilesStreamMap.put(jsonFileName, jsonInputStream);
			}
		} catch (IOException e) {
			logger.error("Error generating JSON files: {}", e.getMessage(), e);
		}
		return jsonFilesStreamMap;
	}

	/**
	 * Uploads files to the given SFTP session.
	 */
	private boolean uploadFilesToSftp(ChannelSftp sftpSession, String sftpPath, Map<String, InputStream> filesMap,
			boolean isJson) {
		try {
			if (isJson) {
				return sftpFileService.batchUploadJsonToSftp(sftpSession, sftpPath, filesMap);
			} else {
				return sftpFileService.batchUploadPdfsToSftp(sftpSession, sftpPath, filesMap);
			}
		} catch (Exception e) {
			logger.error("Error uploading files to SFTP: {}", e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Updates the database if all uploads were successful.
	 */
	private void updateDatabaseAfterUpload(String transactionId, boolean agJsonSuccess, boolean agPdfSuccess,
			boolean hrmsJsonSuccess, boolean hrmsPdfSuccess) {
		if (agJsonSuccess && agPdfSuccess && hrmsJsonSuccess && hrmsPdfSuccess) {
			repository.updateJsonSentDate(transactionId);
			logger.info("✅ JSON_SENT_DATE updated for Transaction ID: {}", transactionId);
		} else {
			logger.error("❌ JSON_SENT_DATE NOT updated due to failed uploads.");
		}
	}

	/**
	 * Closes all InputStreams to prevent memory leaks.
	 */
	private void closeInputStreams(Map<String, InputStream> filesMap) {
		filesMap.values().forEach(stream -> {
			try {
				stream.close();
			} catch (IOException e) {
				logger.error("Error closing InputStream: {}", e.getMessage(), e);
			}
		});
	}

	private void logBatchUploadStatus(boolean jsonSuccess, boolean pdfSuccess, String target) {
		if (jsonSuccess && pdfSuccess) {
			logger.info("✅ Successfully uploaded JSON & PDFs to {}", target);
		} else {
			logger.error("❌ Failed to upload some files to {}", target);
		}
	}
}