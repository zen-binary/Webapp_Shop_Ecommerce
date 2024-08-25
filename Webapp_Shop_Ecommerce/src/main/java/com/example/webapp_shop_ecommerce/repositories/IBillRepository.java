package com.example.webapp_shop_ecommerce.repositories;

import com.example.webapp_shop_ecommerce.entity.Bill;
import com.example.webapp_shop_ecommerce.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface IBillRepository extends IBaseReporitory<Bill, Long> {
    @Query("SELECT b FROM Bill b WHERE b.customer = :customer and b.status like %:status% AND b.status != :statusNot AND b.deleted = false order by b.createdDate desc ")
    List<Bill> findBillByCustomerAndStatusAndStatusNot(@Param("customer") Customer customer,@Param("status") String status,@Param("statusNot") String statusNot);
    @Query("select b from Bill b where b.billType = ?1 and b.status = ?2  and b.deleted = false order by b.lastModifiedDate desc")
    List<Bill> findAllTypeAndStatus(String type, String status);

    @Query("SELECT COALESCE(COUNT(b), 0) FROM Bill b WHERE b.billType = :type AND b.status = :status and b.deleted = false GROUP BY b.billType, b.status ")
    Integer countBillsByTypeAndStatus(@Param("type") String type, @Param("status") String status);

    @Query("SELECT b FROM Bill b WHERE (b.codeBill like %:#{#keyWork['search']}% or b.receiverPhone like %:#{#keyWork['search']}% ) and b.billType like %:#{#keyWork['billType']}% AND ((:#{#keyWork['startDate']} IS NULL and :#{#keyWork['endDate']} IS NULL) OR b.createdDate BETWEEN :#{#keyWork['startDate']} AND :#{#keyWork['endDate']}) and b.status like %:#{#keyWork['status']}% AND b.status != :statusNot AND b.deleted = false order by b.lastModifiedDate desc ")
    Page<Bill> findAllDeletedFalseAndStatusAndStatusNot(Pageable page,@Param("keyWork") Map<String,Object> keyWork, @Param("statusNot") String statusNot);

    @Query("select b from Bill b where b.codeBill = ?1 and b.deleted = false")
    Optional<Bill> findBillByCode(String codeBill);



    @Transactional
    @Modifying
    @Query("UPDATE Bill b set b.status = :newStatus where b.status = :oldStatus and b.createdDate <= :now")
    void updateBillChoThanhToanToHuy(@Param("now") LocalDateTime now, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);


    @Query("select b from Bill b where b.createdDate <= :now and b.status = :status")
    List<Bill> findAllBillChoThanhToan(@Param("now") LocalDateTime now, @Param("status") String status);
}
