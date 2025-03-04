package com.hrms2.repository;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hrms2.entity.HrmsStatusUpdate;

@Repository
public interface GemsStatusRepository extends JpaRepository<HrmsStatusUpdate, Long> {

	boolean existsByGeNumberAndEventIdAndFileIdAndPdfFileNameAndPdfFileNameStatus(String geNumber, String eventId,
			String fileId, String pdfFileName, String pdfFileNameStatus);

	@Modifying
	@Transactional
	@Query("UPDATE HrmsStatusUpdate e SET e.jsonSentDate = CURRENT_TIMESTAMP WHERE e.transactionId = :transactionId")
	int updateJsonSentDate(@Param("transactionId") String transactionId);

	@Query("SELECT h.transactionId FROM HrmsStatusUpdate h WHERE h.geNumber = :geNumber AND h.eventId = :eventId ORDER BY h.createdAt DESC")
	String findTransactionIdByGeNumberAndEventId(@Param("geNumber") String geNumber, @Param("eventId") String eventId);
}
