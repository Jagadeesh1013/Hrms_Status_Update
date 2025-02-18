package com.hrms2.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "HRMS_STATUS_UPDATE", schema = "hrms2")
public class HrmsStatusUpdate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String transactionId;
	private String geNumber;
	private String eventId;
	private String eventName;
	private String fileId;
	private String pdfFileName;
	private String pdfFileNameStatus;
	private String jsonGenerationStatus;
	private LocalDateTime jsonSentDate;
	private String hrmsReceivedStatus;
	private LocalDateTime hrmsReceivedDate;
	private String hrmsRejectedStatus;
	private LocalDateTime hrmsRejectedDate;
	private String ddoReceivedStatus;
	private LocalDateTime ddoReceivedDate;
	private String ddoRejectedStatus;
	private LocalDateTime ddoRejectedDate;
	private String ddoRejectedComments;

	@Column(updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public String getGeNumber() {
		return geNumber;
	}

	public void setGeNumber(String geNumber) {
		this.geNumber = geNumber;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
	}

	public String getPdfFileName() {
		return pdfFileName;
	}

	public void setPdfFileName(String pdfFileName) {
		this.pdfFileName = pdfFileName;
	}

	public String getPdfFileNameStatus() {
		return pdfFileNameStatus;
	}

	public void setPdfFileNameStatus(String pdfFileNameStatus) {
		this.pdfFileNameStatus = pdfFileNameStatus;
	}

	public String getJsonGenerationStatus() {
		return jsonGenerationStatus;
	}

	public void setJsonGenerationStatus(String jsonGenerationStatus) {
		this.jsonGenerationStatus = jsonGenerationStatus;
	}

	public LocalDateTime getJsonSentDate() {
		return jsonSentDate;
	}

	public void setJsonSentDate(LocalDateTime jsonSentDate) {
		this.jsonSentDate = jsonSentDate;
	}

	public String getHrmsReceivedStatus() {
		return hrmsReceivedStatus;
	}

	public void setHrmsReceivedStatus(String hrmsReceivedStatus) {
		this.hrmsReceivedStatus = hrmsReceivedStatus;
	}

	public LocalDateTime getHrmsReceivedDate() {
		return hrmsReceivedDate;
	}

	public void setHrmsReceivedDate(LocalDateTime hrmsReceivedDate) {
		this.hrmsReceivedDate = hrmsReceivedDate;
	}

	public String getHrmsRejectedStatus() {
		return hrmsRejectedStatus;
	}

	public void setHrmsRejectedStatus(String hrmsRejectedStatus) {
		this.hrmsRejectedStatus = hrmsRejectedStatus;
	}

	public LocalDateTime getHrmsRejectedDate() {
		return hrmsRejectedDate;
	}

	public void setHrmsRejectedDate(LocalDateTime hrmsRejectedDate) {
		this.hrmsRejectedDate = hrmsRejectedDate;
	}

	public String getDdoReceivedStatus() {
		return ddoReceivedStatus;
	}

	public void setDdoReceivedStatus(String ddoReceivedStatus) {
		this.ddoReceivedStatus = ddoReceivedStatus;
	}

	public LocalDateTime getDdoReceivedDate() {
		return ddoReceivedDate;
	}

	public void setDdoReceivedDate(LocalDateTime ddoReceivedDate) {
		this.ddoReceivedDate = ddoReceivedDate;
	}

	public String getDdoRejectedStatus() {
		return ddoRejectedStatus;
	}

	public void setDdoRejectedStatus(String ddoRejectedStatus) {
		this.ddoRejectedStatus = ddoRejectedStatus;
	}

	public LocalDateTime getDdoRejectedDate() {
		return ddoRejectedDate;
	}

	public void setDdoRejectedDate(LocalDateTime ddoRejectedDate) {
		this.ddoRejectedDate = ddoRejectedDate;
	}

	public String getDdoRejectedComments() {
		return ddoRejectedComments;
	}

	public void setDdoRejectedComments(String ddoRejectedComments) {
		this.ddoRejectedComments = ddoRejectedComments;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
