package com.hrms2.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrms2.dto.LeaveCreditResponseDTO;
import com.hrms2.service.HrmsStatusUpdateServiceImpl;

@RestController
@RequestMapping("/hrms-status")
public class HrmsStatusUpdateController {

	@Autowired
	private HrmsStatusUpdateServiceImpl hrmsStatusUpdateService;

	@PostMapping("/save-json-data")
	public ResponseEntity<String> saveJsonData(@RequestBody LeaveCreditResponseDTO response) {
		try {
			hrmsStatusUpdateService.processAndSaveLeaveCreditData(response);
			return new ResponseEntity<>("Data processed successfully", HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>("Error processing data: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
