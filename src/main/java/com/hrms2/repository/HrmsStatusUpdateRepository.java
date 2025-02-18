package com.hrms2.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hrms2.entity.HrmsStatusUpdate;

@Repository
public interface HrmsStatusUpdateRepository extends JpaRepository<HrmsStatusUpdate, Long> {

	Optional<HrmsStatusUpdate> findByGeNumberAndEventIdAndFileIdAndPdfFileNameAndPdfFileNameStatus(
			String geNumber,
			String eventId, 
			String fileId, 
			String pdfFileName, 
			String pdfFileNameStatus);
}
