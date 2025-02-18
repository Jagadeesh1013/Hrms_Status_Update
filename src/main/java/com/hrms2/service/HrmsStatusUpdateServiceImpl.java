package com.hrms2.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms2.config.SftpFileService;
import com.hrms2.dto.LeaveCreditDetailsDTO;
import com.hrms2.dto.LeaveCreditEmployeeDetailsDTO;
import com.hrms2.dto.LeaveCreditResponseDTO;
import com.hrms2.entity.HrmsStatusUpdate;
import com.hrms2.repository.HrmsStatusUpdateRepository;

@Service
public class HrmsStatusUpdateServiceImpl {

	private static final Logger logger = LoggerFactory.getLogger(HrmsStatusUpdateServiceImpl.class);

	@Autowired
	private HrmsStatusUpdateRepository hrmsStatusUpdateRepository;

	@Autowired
	private SftpFileService sftpFileService;

	@Value("${sftp.ag.pdf.remote.directory}")
	private String agSftpPdfPath;

	@Value("${sftp.ag.json.remote.directory}")
	private String agSftpJsonPath;

	@Value("${sftp.hrms.pdf.remote.directory}")
	private String hrmsSftpPdfPath;

	@Value("${sftp.hrms.json.remote.directory}")
	private String hrmsSftpJsonPath;

	private static final int MAX_PDF_PER_RUN = 50;

	public void processAndSaveLeaveCreditData(LeaveCreditResponseDTO response) {
		Map<String, List<LeaveCreditEmployeeDetailsDTO>> pdfToEmployeeMap = new HashMap<>();
		String transactionId = response.getTransactionId();
		String gemsId = response.getGemsId();

		// Fetch PDF files from SFTP just once
		List<String> pdfFileNamesFromSFTP = sftpFileService.getCurrentMonthPdfFileNames();
		logger.info("Fetched PDF file names from SFTP: {}", pdfFileNamesFromSFTP);

		Set<String> processedPdfSet = new HashSet<>();

		// Use a Set for quicker lookup of existing records
		Set<String> uniquePdfFilesToProcess = new HashSet<>();

		// Process each employee and their leave details
		for (LeaveCreditEmployeeDetailsDTO employee : response.getEmployeeDetails()) {
			for (LeaveCreditDetailsDTO leaveDetail : employee.getLeaveDetails()) {
				String expectedPdfFileName = employee.getFileName();
				logger.info("Checking PDF existence for: {}", expectedPdfFileName);

				if (processedPdfSet.size() >= MAX_PDF_PER_RUN) {
					logger.info("Reached limit of {} PDFs. Stopping processing.", MAX_PDF_PER_RUN);
					break;
				}

				// Check if the record has already been processed
				Optional<HrmsStatusUpdate> existingRecordOpt = hrmsStatusUpdateRepository
						.findByGeNumberAndEventIdAndFileIdAndPdfFileNameAndPdfFileNameStatus(employee.getGerNo(),
								String.valueOf(leaveDetail.getEventId()), employee.getFileId(), expectedPdfFileName,
								"Y");

				if (existingRecordOpt.isPresent()) {
					logger.warn("Skipping record (Already processed with status 'Y') - File ID: {}",
							employee.getFileId());
					continue;
				}

				// Only process if the PDF file exists on the SFTP server
				if (!pdfFileNamesFromSFTP.contains(expectedPdfFileName)) {
					logger.warn("Skipping record - No matching PDF found on SFTP for File ID: {}",
							employee.getFileId());
					continue;
				}

				logger.info("Matching PDF found on SFTP: {}", expectedPdfFileName);

				HrmsStatusUpdate newRecord = new HrmsStatusUpdate();
				newRecord.setTransactionId(transactionId);
				newRecord.setGeNumber(employee.getGerNo());
				newRecord.setEventId(String.valueOf(leaveDetail.getEventId()));
				newRecord.setEventName(leaveDetail.getEventName());
				newRecord.setFileId(employee.getFileId());
				newRecord.setPdfFileName(expectedPdfFileName);
				newRecord.setPdfFileNameStatus("Y");
				newRecord.setJsonGenerationStatus("N");
				newRecord.setHrmsReceivedStatus("N");
				newRecord.setHrmsRejectedStatus("N");
				newRecord.setDdoReceivedStatus("N");
				newRecord.setDdoRejectedStatus("N");
				newRecord.setCreatedAt(LocalDateTime.now());

				hrmsStatusUpdateRepository.save(newRecord);
				logger.info("Inserted new record into database with status 'Y' - File ID: {}", employee.getFileId());

				pdfToEmployeeMap.computeIfAbsent(expectedPdfFileName, k -> new ArrayList<>()).add(employee);
				processedPdfSet.add(expectedPdfFileName);
				uniquePdfFilesToProcess.add(expectedPdfFileName);
			}

			if (processedPdfSet.size() >= MAX_PDF_PER_RUN) {
				logger.info("Completed processing for {} matching PDFs.", MAX_PDF_PER_RUN);
				break;
			}
		}

		// Remove duplicates in the employee list just once before uploading
		List<LeaveCreditEmployeeDetailsDTO> uniqueEmployeeDetails = removeDuplicates(response.getEmployeeDetails());

		if (!pdfToEmployeeMap.isEmpty()) {
			uploadJsonAndPdfToBothSftpServers(pdfToEmployeeMap, uniqueEmployeeDetails, transactionId, gemsId);
		}
	}

	private List<LeaveCreditEmployeeDetailsDTO> removeDuplicates(
			List<LeaveCreditEmployeeDetailsDTO> employeeDetailsList) {
		Set<String> seenKeys = new HashSet<>();
		List<LeaveCreditEmployeeDetailsDTO> uniqueEmployeeDetails = new ArrayList<>();

		for (LeaveCreditEmployeeDetailsDTO employeeDetails : employeeDetailsList) {
			String uniqueKey = employeeDetails.getGerNo() + "-" + employeeDetails.getFileId() + "-"
					+ employeeDetails.getLeaveDetails().stream().map(ld -> String.valueOf(ld.getEventId()))
							.reduce((first, second) -> first + "," + second).orElse("");

			if (!seenKeys.contains(uniqueKey)) {
				seenKeys.add(uniqueKey);
				uniqueEmployeeDetails.add(employeeDetails);
			}
		}
		return uniqueEmployeeDetails;
	}

	private void uploadJsonAndPdfToBothSftpServers(Map<String, List<LeaveCreditEmployeeDetailsDTO>> pdfToEmployeeMap,
			List<LeaveCreditEmployeeDetailsDTO> uniqueEmployeeDetails, String transactionId, String gemsId) {
		try {
			ObjectMapper objectMapper = new ObjectMapper();

			// Prepare files for batch upload
			Map<String, byte[]> jsonFilesMap = new HashMap<>();
			Map<String, byte[]> pdfFilesMap = new HashMap<>();

			// Generate files for upload
			for (Map.Entry<String, List<LeaveCreditEmployeeDetailsDTO>> entry : pdfToEmployeeMap.entrySet()) {
				String pdfFileName = entry.getKey();
				List<LeaveCreditEmployeeDetailsDTO> employeeDetails = entry.getValue();

				String jsonFileName = pdfFileName.replace(".pdf", ".json");

				// Prepare JSON data
				LeaveCreditResponseDTO leaveCreditJsonDTO = new LeaveCreditResponseDTO();
				leaveCreditJsonDTO.setTransactionId(transactionId);
				leaveCreditJsonDTO.setGemsId(gemsId);
				leaveCreditJsonDTO.setEmployeeDetails(removeDuplicates(employeeDetails)); // Remove duplicates once

				String jsonString = objectMapper.writerWithDefaultPrettyPrinter()
						.writeValueAsString(leaveCreditJsonDTO);

				// Convert to byte array for batch upload
				jsonFilesMap.put(jsonFileName, jsonString.getBytes());

				// Placeholder PDF data (replace this with actual PDF bytes)
				pdfFilesMap.put(pdfFileName, new byte[0]);
			}

			// Upload JSON and PDF files in batches
			batchUploadFiles(jsonFilesMap, agSftpJsonPath, hrmsSftpJsonPath, "JSON");
			batchUploadFiles(pdfFilesMap, agSftpPdfPath, hrmsSftpPdfPath, "PDF");

		} catch (IOException e) {
			logger.error("Error processing and uploading JSON/PDF to SFTP: {}", e.getMessage(), e);
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
		}
	}

	private void logBatchUploadStatus(boolean success, String server, String fileType) {
		if (success) {
			logger.info("{}: All {} files uploaded successfully.", server, fileType);
		} else {
			logger.error("{}: Failed to upload {} files.", server, fileType);
		}
	}
}
