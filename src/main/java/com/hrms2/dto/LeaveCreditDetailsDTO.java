package com.hrms2.dto;

public class LeaveCreditDetailsDTO {

	// Leave Credit
	private String eventCode;
	private Long eventId;
	private String eventName;
	private String eventDate;
	private String fromDate;
	private String toDate;
	private Integer elCredit;
	private Integer elBalance;
	private Integer hplCredit;
	private Integer hplBalance;
	private String vacation;
	private String remarks;

	public Long getEventId() {
		return eventId;
	}

	public void setEventId(Long eventId) {
		this.eventId = eventId;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public String getEventDate() {
		return eventDate;
	}

	public void setEventDate(String eventDate) {
		this.eventDate = eventDate;
	}

	public String getFromDate() {
		return fromDate;
	}

	public void setFromDate(String fromDate) {
		this.fromDate = fromDate;
	}

	public String getToDate() {
		return toDate;
	}

	public void setToDate(String toDate) {
		this.toDate = toDate;
	}

	public Integer getElCredit() {
		return elCredit;
	}

	public void setElCredit(Integer elCredit) {
		this.elCredit = elCredit;
	}

	public Integer getElBalance() {
		return elBalance;
	}

	public void setElBalance(Integer elBalance) {
		this.elBalance = elBalance;
	}

	public Integer getHplCredit() {
		return hplCredit;
	}

	public void setHplCredit(Integer hplCredit) {
		this.hplCredit = hplCredit;
	}

	public Integer getHplBalance() {
		return hplBalance;
	}

	public void setHplBalance(Integer hplBalance) {
		this.hplBalance = hplBalance;
	}

	public String getVacation() {
		return vacation;
	}

	public void setVacation(String vacation) {
		this.vacation = vacation;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}

	public String getEventCode() {
		return eventCode;
	}

	public void setEventCode(String eventCode) {
		this.eventCode = eventCode;
	}

}
