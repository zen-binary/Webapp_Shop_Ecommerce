package com.example.webapp_shop_ecommerce.repositories;

import com.example.webapp_shop_ecommerce.entity.Promotion;
import com.example.webapp_shop_ecommerce.entity.Voucher;
import com.example.webapp_shop_ecommerce.entity.VoucherDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface IVoucherRepository extends IBaseReporitory<Voucher, Long> {
    @Query("SELECT b FROM Voucher b WHERE b.name = ?1 and b.deleted = false")
    Optional<Voucher> findByName(String name);

    @Query("SELECT vc FROM Voucher vc WHERE vc.name like %:#{#keyWork['search']}% and vc.status like %:#{#keyWork['status']}% and vc.deleted = false order by vc.createdDate desc ")
    Page<Voucher> findVoucherByKeyWorkAndDeletedFalse(Pageable pageable, Map<String,String> keyWork);
    @Query("select v from Voucher v join v.lstVoucherDetails vd where v.deleted = false and vd.deleted = false and vd.status = false and v.status = :status and vd.customer.id = :idCustomer and v.quantity > 0")
    List<Voucher> findAllByIdCustomer(@Param("idCustomer") Long idCustomer, @Param("status") String status);


    @Transactional
    @Modifying
    @Query("UPDATE Voucher p SET p.status = :newStatus WHERE p.startDate <= :now AND p.status = :oldStatus and p.deleted = false ")
    void updateStatusToDangDienRa(@Param("now") LocalDateTime now, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);

    @Transactional
    @Modifying
    @Query("UPDATE Voucher p SET p.status = :newStatus WHERE p.endDate <= :now AND p.status = :oldStatus and p.deleted = false ")
    void updateStatusToDaKetThuc(@Param("now") LocalDateTime now, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);

    @Transactional
    @Modifying
    @Query("UPDATE Voucher p SET p.status = :status WHERE p.id = :id and p.deleted = false ")
    void disableVoucher(@Param("id") Long id, @Param("status") String status);

    @Query("SELECT p FROM Voucher p WHERE  p.deleted = :type")
    List<Voucher> findAllByDeleted(@Param("type") Boolean type);

    @Query("SELECT p FROM Voucher p WHERE  p.id = :id AND p.deleted = true")
    Voucher findDeletedId(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query("UPDATE Voucher p SET p.deleted = false where p.id = :id")
    void updateRecover(@Param("id") Long id);

}
