package com.example.webapp_shop_ecommerce.service;

import com.example.webapp_shop_ecommerce.dto.request.bill.BillRequest;
import com.example.webapp_shop_ecommerce.dto.request.billdetails.BillDetailsRequest;
import com.example.webapp_shop_ecommerce.dto.request.cart.CartRequest;
import com.example.webapp_shop_ecommerce.dto.request.historybill.HistoryBillRequest;
import com.example.webapp_shop_ecommerce.dto.request.paymentHistory.PaymentHistoryRequest;
import com.example.webapp_shop_ecommerce.dto.response.ResponseObject;
import com.example.webapp_shop_ecommerce.entity.Bill;
import com.example.webapp_shop_ecommerce.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IBillService extends IBaseService<Bill, Long> {
    ResponseEntity<ResponseObject> buyBillClient(BillRequest billRequest) throws UnsupportedEncodingException;
    ResponseEntity<ResponseObject> buyBillClientGuest(BillRequest billRequest) throws UnsupportedEncodingException;
    List<Bill> findBillByCustomerAndStatusAndStatusNot(String status, String statusNot);

    List<Bill> findAllTypeAndStatus(String type, String status);



    ResponseEntity<ResponseObject> billCounterNew();
    ResponseEntity<ResponseObject> countersAddProduct(List<BillDetailsRequest> lstBillDetailsDto, Long id);
    ResponseEntity<ResponseObject> billUpdateCustomer(BillRequest billRequest, Long id);
    ResponseEntity<ResponseObject> countersAddProductBarcode( Long id, String barcode );
    ResponseEntity<ResponseObject> chaneQuantityBillDetails(BillDetailsRequest chaneQuantityBillDetails, Long idBillDetail);
    ResponseEntity<ResponseObject> billCounterPay( BillRequest billDto, Long id) throws UnsupportedEncodingException;
    ResponseEntity<ResponseObject> billDeleteBillDetail(Long idBillDetail);
    ResponseEntity<ResponseObject> billDeleteAllBillDetail(Long idBill);
    ResponseEntity<ResponseObject> deleteBillToBillDetailAll(Long idBill);

    Page<Bill> findAllDeletedFalseAndStatusAndStatusNot(Pageable page, Map<String,Object> keyWork, String statusNot);


    //Màn Bill
    Boolean updateChangeMoneyBill(Long idBill);
    ResponseEntity<ResponseObject> billAddProductNew(List<BillDetailsRequest> lstBillDetailsDto, Long id);
    ResponseEntity<ResponseObject> chaneQuantityBillToBillDetails(BillDetailsRequest chaneQuantityBillDetails,Long idBill, Long idBillDetail);
    ResponseEntity<ResponseObject> deleteBillToBillDetail(Long idBill, Long idBillDetail);

    ResponseEntity<ResponseObject> addHistorybill(HistoryBillRequest historyBillRequest ,Long idBill);
    ResponseEntity<ResponseObject> billPaymentHistory(PaymentHistoryRequest paymentHistoryRequest , Long idBill);
    ResponseEntity<ResponseObject> cancellingBill(Long idBill, HistoryBillRequest historyBillRequest);
    Optional<Bill> findBillByCode(String codeBill);



    void autoUpdateBillChoThanhToanToHuy( LocalDateTime now, String choThanhToan, String daHuy);

}
