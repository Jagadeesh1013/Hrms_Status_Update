package com.hrms2.dto;

import java.util.List;

public class LeaveCreditEmployeeDetailsDTO {

	private String gerNo;
	private String kgidNo;
	private String fileId;
	private String hrmsEventRequestNo;
	private String orderDate;
	private String leaveColumnStatus;
	private String fileName;
	private List<LeaveCreditDetailsDTO> leaveDetails;

	public String getGerNo() {
		return gerNo;
	}

	public void setGerNo(String gerNo) {
		this.gerNo = gerNo;
	}

	public String getKgidNo() {
		return kgidNo;
	}

	public void setKgidNo(String kgidNo) {
		this.kgidNo = kgidNo;
	}

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
	}

	public String getHrmsEventRequestNo() {
		return hrmsEventRequestNo;
	}

	public void setHrmsEventRequestNo(String hrmsEventRequestNo) {
		this.hrmsEventRequestNo = hrmsEventRequestNo;
	}

	public String getOrderDate() {
		return orderDate;
	}

	public void setOrderDate(String orderDate) {
		this.orderDate = orderDate;
	}

	public String getLeaveColumnStatus() {
		return leaveColumnStatus;
	}

	public void setLeaveColumnStatus(String leaveColumnStatus) {
		this.leaveColumnStatus = leaveColumnStatus;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public List<LeaveCreditDetailsDTO> getLeaveDetails() {
		return leaveDetails;
	}

	public void setLeaveDetails(List<LeaveCreditDetailsDTO> leaveDetails) {
		this.leaveDetails = leaveDetails;
	}

}
