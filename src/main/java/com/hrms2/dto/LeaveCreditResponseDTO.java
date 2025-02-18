package com.hrms2.dto;

import java.util.List;

public class LeaveCreditResponseDTO {

	private String transactionId;
	private String gemsId;
	private List<LeaveCreditEmployeeDetailsDTO> employeeDetails;

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public String getGemsId() {
		return gemsId;
	}

	public void setGemsId(String gemsId) {
		this.gemsId = gemsId;
	}

	public List<LeaveCreditEmployeeDetailsDTO> getEmployeeDetails() {
		return employeeDetails;
	}

	public void setEmployeeDetails(List<LeaveCreditEmployeeDetailsDTO> employeeDetails) {
		this.employeeDetails = employeeDetails;
	}

}
