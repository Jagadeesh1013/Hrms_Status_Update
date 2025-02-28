package com.hrms2.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hrms2.config.SftpFileService;
import com.hrms2.entity.HrmsStatusUpdate;
import com.hrms2.repository.GemsStatusRepository;

@Service
public class GemsStatusService {

	private static final Logger logger = LoggerFactory.getLogger(GemsStatusService.class);

	@Autowired
	private GemsStatusRepository repository;

	@Autowired
	private SftpFileService sftpFileService;

	public ResponseEntity<String> processAndSaveGemsData(String requestBody) {
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode rootNodeMain = objectMapper.readTree(requestBody);

			String jsonData = rootNodeMain.get("jsonData").asText();
			JsonNode rootNode = objectMapper.readTree(jsonData);

			String agSftpPdfPath = rootNodeMain.get("agSftpPdfPath").asText();
			String agSftpJsonPath = rootNodeMain.get("agSftpJsonPath").asText();
			String hrmsSftpPdfPath = rootNodeMain.get("hrmsSftpPdfPath").asText();
			String hrmsSftpJsonPath = rootNodeMain.get("hrmsSftpJsonPath").asText();

			// Extract transactionId and gemsId
//			String transactionId = rootNode.path("transactionId").asText(null);
			String gemsId = rootNode.path("gemsId").asText(null);

			JsonNode employeeDetailsNode = rootNode.get("employeeDetails");
			if (employeeDetailsNode == null || !employeeDetailsNode.isArray() || employeeDetailsNode.size() == 0) {
				logger.error("employeeDetails array is missing or empty in JSON!");
				return ResponseEntity.badRequest().body("Error: employeeDetails array is missing or empty.");
			}

			Map<String, List<JsonNode>> groupedJsonData = new HashMap<>();
			Map<String, byte[]> matchedPdfFiles = new HashMap<>();
			String transactionId = null;
			int matchedCount = 0;

			for (JsonNode employee : employeeDetailsNode) {
				if (matchedCount >= 10) {
					logger.info("Limit reached: 10 matching PDFs and JSONs found. Stopping further processing.");
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
				for (JsonNode event : eventDetailsNode) {
					String eventId = event.path("eventId").asText(null);
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

				byte[] exactPdfContent = sftpFileService.getExactPdfFile(fileName);
				if (exactPdfContent == null) {
					logger.warn("Skipping record - No matching PDF found on SFTP for File Name: {}", fileName);
					continue;
				}

				matchedPdfFiles.put(fileName, exactPdfContent);

				for (JsonNode event : eventDetailsNode) {
					String eventId = event.path("eventId").asText(null);
					String eventName = event.path("eventName").asText(null);

					DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
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

				if (matchedCount >= 10) {
					logger.info("Reached 10 matching PDFs and JSONs.");
					break;
				}
			}
			batchUploadJsonToSftp(groupedJsonData, transactionId, gemsId, agSftpJsonPath, agSftpPdfPath,
					hrmsSftpJsonPath, hrmsSftpPdfPath, matchedPdfFiles);

			return ResponseEntity.ok("Processed successfully. JSON and matched PDFs uploaded.");

		} catch (Exception e) {
			logger.error("Error processing JSON: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
		}
	}

	public void batchUploadJsonToSftp(Map<String, List<JsonNode>> groupedJsonData, String transactionId, String gemsId,
			String agSftpJsonPath, String agSftpPdfPath, String hrmsSftpJsonPath, String hrmsSftpPdfPath,
			Map<String, byte[]> pdfFilesMap) {
		try {
			ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

			// Prepare files for batch upload
			Map<String, byte[]> jsonFilesMap = new HashMap<>();

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
				jsonFilesMap.put(jsonFileName, formattedJson.getBytes(StandardCharsets.UTF_8));

				// Placeholder PDF data (replace this with actual PDF bytes)
//				byte[] exactPdfContent = pdfFilesFromSFTP.get(fileName);
//				logger.info("✅ Exact PDF file retrieved: {}", fileName);
//				pdfFilesMap.put(fileName, exactPdfContent);
			}

			// Upload JSON and PDF files in batches
			batchUploadFiles(jsonFilesMap, agSftpJsonPath, hrmsSftpJsonPath, "JSON");
			batchUploadFiles(pdfFilesMap, agSftpPdfPath, hrmsSftpPdfPath, "PDF");

		} catch (IOException e) {
			logger.error("Error processing and uploading JSON to SFTP: {}", e.getMessage(), e);
		}
	}

	private void batchUploadFiles(Map<String, byte[]> filesMap, String agSftpPath, String hrmsSftpPath, String fileType)
			throws IOException {
		if (!filesMap.isEmpty()) {
			// Upload to AG SFTP
			boolean agUploadSuccess = sftpFileService.batchUploadFilesToAg(agSftpPath, filesMap);
			logBatchUploadStatus(agUploadSuccess, "AG SFTP", fileType);

			// Upload to HRMS SFTP
			boolean hrmsUploadSuccess = sftpFileService.batchUploadFilesToHrms(hrmsSftpPath, filesMap);
			logBatchUploadStatus(hrmsUploadSuccess, "HRMS SFTP", fileType);

			// If JSON files were successfully uploaded, update the json_sent_date
			if (fileType.equals("JSON") && (agUploadSuccess || hrmsUploadSuccess)) {
				List<String> fileIds = extractFileIdsFromJson(filesMap.keySet());
				if (!fileIds.isEmpty()) {
					repository.updateJsonSentDate(fileIds, LocalDateTime.now());
					logger.info("Updated json_sent_date in the database for {} files.", fileIds.size());
				}
			}
		}
	}

	private List<String> extractFileIdsFromJson(Set<String> jsonFileNames) {
		List<String> fileIds = new ArrayList<>();

		for (String jsonFileName : jsonFileNames) {
			// Remove ".json" extension
			String baseName = jsonFileName.replace(".json", "");

			// Find the last underscore `_`
			int lastIndex = baseName.lastIndexOf("_");

			// Extract only the numeric part after the last `_`
			if (lastIndex != -1) {
				String fileId = baseName.substring(lastIndex + 1).replaceAll("[^0-9]", "");
				fileIds.add(fileId);
			}
		}
		return fileIds;
	}

	private void logBatchUploadStatus(boolean success, String server, String fileType) {
		if (success) {
			logger.info("{}: All {} files uploaded successfully.", server, fileType);
		} else {
			logger.error("{}: Failed to upload {} files.", server, fileType);
		}
	}
}
