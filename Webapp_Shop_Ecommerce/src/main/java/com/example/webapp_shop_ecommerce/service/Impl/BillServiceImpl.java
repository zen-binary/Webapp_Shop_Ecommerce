package com.example.webapp_shop_ecommerce.service.Impl;

import com.example.webapp_shop_ecommerce.dto.request.bill.BillRequest;
import com.example.webapp_shop_ecommerce.dto.request.billdetails.BillDetailsRequest;
import com.example.webapp_shop_ecommerce.dto.request.historybill.HistoryBillRequest;
import com.example.webapp_shop_ecommerce.dto.request.paymentHistory.PaymentHistoryRequest;
import com.example.webapp_shop_ecommerce.dto.response.ResponseObject;
import com.example.webapp_shop_ecommerce.entity.*;
import com.example.webapp_shop_ecommerce.infrastructure.enums.*;
import com.example.webapp_shop_ecommerce.infrastructure.security.Authentication;
import com.example.webapp_shop_ecommerce.repositories.*;
import com.example.webapp_shop_ecommerce.service.*;
import com.example.webapp_shop_ecommerce.ultiltes.InvoiceGenerator;
import com.example.webapp_shop_ecommerce.ultiltes.RandomStringGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class BillServiceImpl extends BaseServiceImpl<Bill, Long, IBillRepository> implements IBillService {
    @Autowired
    private IProductDetailsRepository productDetailsRepo;
    @Autowired
    private Authentication authentication;
    @Autowired
    private ICartRepository cartRepo;
    @Autowired
    private ICartDetailsRepository cartDetailsRepo;
    @Autowired
    private IBillRepository billRepo;
    @Autowired
    private IBillDetailsRepository billDetailsRepo;
    @Autowired
    private RandomStringGenerator randomStringGenerator;
    @Autowired
    private ModelMapper mapper;
    @Autowired
    private IBillDetailsService billDetailsService;
    @Autowired
    private IProductDetailsService productDetailsService;
    @Autowired
    private ICustomerRepository customerRepo;
    @Autowired
    private IVoucherDetailsRepository voucherDetailsRepo;
    @Autowired
    private InvoiceGenerator invoiceGenerator;
    @Autowired
    private IHistoryBillService historyBillService;
    @Autowired
    private IVoucherRepository voucherRepo;
    @Autowired
    private IVoucherDetailsService voucherDetailsService;
    @Autowired
    private IPaymentHistoryService paymentHistoryService;

    @Autowired
    IHistoryBillRepository historyBillRepo;
    @Autowired
    VnpayService vnpayService;

    @Override
    public ResponseEntity<ResponseObject> buyBillClient(BillRequest billRequest) throws UnsupportedEncodingException {
        Customer customer = authentication.getCustomer();
        List<Long> lstIdCartDetails = billRequest.getLstCartDetails().stream()
                .map(cartDetails -> cartDetails.getId()).collect(Collectors.toList());

        if (lstIdCartDetails.size() == 0) {
            return new ResponseEntity<>(new ResponseObject("error", "Chon it nhat 1 san pham", 1, billRequest), HttpStatus.BAD_REQUEST);
        }
        if (billRequest.getVoucher() != null) {
            Optional<VoucherDetails> voucherDetailsOpt = voucherDetailsService.findVoucherDetailsByCustomerAndVoucher(customer.getId(), billRequest.getVoucher());
            if (voucherDetailsOpt.isEmpty()) {
                return new ResponseEntity<>(new ResponseObject("error", "Không tìm thấy giảm giá có thể khách hàng chưa sở hữu hoặc đã sử dụng", 1, billRequest), HttpStatus.BAD_REQUEST);
            }
            VoucherDetails voucherDetails = voucherDetailsOpt.get();
            Voucher voucher = voucherDetails.getVoucher();

            if (voucher.getQuantity() < 0) {
                return new ResponseEntity<>(new ResponseObject("error", "Số lượng giảm giá đã hết vui lòng chọn giảm giá khác", 1, billRequest), HttpStatus.BAD_REQUEST);
            }
            // compareTo returns âm thì big 1 < big 2
            // compareTo returns bằng 0 thì big -1 = big 2
            // compareTo returns dương thì big -1 > big 2
            if ((billRequest.getTotalMoney().compareTo(voucher.getOrderMinValue()) < 0)) {
                return new ResponseEntity<>(new ResponseObject("error", "Đơn hàng chưa đạt giá trị tối thiểu để dử dụng giảm giá này", 0, billRequest), HttpStatus.BAD_REQUEST);
            }
        }


        List<CartDetails> lstCartDetails = cartDetailsRepo.findAllById(lstIdCartDetails);
        for (CartDetails cartDetails : lstCartDetails) {

            Optional<ProductDetails> productDetailsOtp = productDetailsService.findByProductDetailWhereDeletedAndStatus(cartDetails.getProductDetails().getId(), TrangThai.HOAT_DONG.getLabel());
            if (productDetailsOtp.isEmpty()){
                cartDetailsRepo.delete(cartDetails);
                return new ResponseEntity<>(new ResponseObject("error", "Sản phẩm " + cartDetails.getProductDetails().getProduct().getName() + " đã ngừng bán hoặc đã bị xóa khỏi cửa hàng", 1, cartDetails), HttpStatus.BAD_REQUEST);
            }

            ProductDetails productDetails = productDetailsOtp.get();
            if (cartDetails.getQuantity() > productDetails.getQuantity()) {
                if (productDetails.getQuantity() >0) {
                    cartDetails.setQuantity(productDetails.getQuantity());
                    cartDetailsRepo.save(cartDetails);
                }else {
                    cartDetailsRepo.delete(cartDetails);
                }
                return new ResponseEntity<>(new ResponseObject("error", "Số lượng sản phẩm " + productDetails.getProduct().getName() + " trong kho không đủ", 1, billRequest), HttpStatus.BAD_REQUEST);
            }
        }


        Bill billDto = mapper.map(billRequest, Bill.class);
        billDto.setId(null);
        billDto.setCodeBill(invoiceGenerator.generateInvoiceNumber());
        billDto.setBillType(BillType.ONLINE.getLabel());
        billDto.setBillFormat(BillType.DELIVERY.getLabel());
        billDto.setBookingDate(new Date());
        billDto.setDeleted(false);
        billDto.setCreatedBy("Admin");
        billDto.setCreatedDate(LocalDateTime.now());
        billDto.setLastModifiedDate(LocalDateTime.now());
        billDto.setLastModifiedBy("Admin");
        billDto.setCustomer(customer);
        billDto.setIntoMoney(BigDecimal.ZERO);
        billDto.setTotalMoney(BigDecimal.ZERO);
        billDto.setVoucherMoney(BigDecimal.ZERO);

        billDto.setStatus(TrangThaiBill.CHO_XAC_NHAN.getLabel());
        //Thanh TOna tai khona
        if (billDto.getPaymentMethod().equalsIgnoreCase("1")) {
            billDto.setStatus(TrangThaiBill.CHO_THANH_TOAN.getLabel());
        }
        Bill bill = billRepo.save(billDto);
        for (CartDetails cartDetails : lstCartDetails) {

            BillDetails billDetails = new BillDetails();
            Optional<ProductDetails> productDetailsOtp = productDetailsService.findByProductDetailWhereDeletedAndStatus(cartDetails.getProductDetails().getId(), TrangThai.HOAT_DONG.getLabel());
            ProductDetails productDetails = productDetailsOtp.get();

            // Cần xác định giá của sản phẩm từ đâu
            if (productDetails.getPromotionDetailsActive() != null) {
                BigDecimal price = productDetails.getPrice().subtract(
                        productDetails.getPrice()
                                .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                        .divide(BigDecimal.valueOf(100))));
                billDetails.setUnitPrice(price);
                billDetails.setDiscount(productDetails.getPrice()
                        .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                .divide(BigDecimal.valueOf(100))));
                billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
            } else {
                billDetails.setUnitPrice(productDetails.getPrice());
                billDetails.setDiscount(BigDecimal.ZERO);
                billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
            }
            billDetails.setOriginalPrice(productDetails.getPrice());
            billDetails.setBill(bill);
            billDetails.setProductDetails(productDetails);
            billDetails.setQuantity(cartDetails.getQuantity());
            billDetails.setStatus(TrangThaiBill.DANG_BAN.getLabel());
            billDetails.setId(null);
            billDetails.setDeleted(false);
            billDetails.setCreatedBy("Admin");
            billDetails.setCreatedDate(LocalDateTime.now());
            billDetails.setLastModifiedDate(LocalDateTime.now());
            billDetails.setLastModifiedBy("Admin");
            if (billDto.getPaymentMethod().equalsIgnoreCase(PaymentMethod.CHUYEN_KHOAN.getLabel())) {
                billDetails.setQuantity(cartDetails.getQuantity());
                productDetails.setQuantity(productDetails.getQuantity() - cartDetails.getQuantity());
            } else {
                billDetails.setQuantity(cartDetails.getQuantity());
            }

            productDetailsRepo.save(productDetails);
            billDetailsRepo.save(billDetails);
        }


        List<BillDetails> lstBillDetail = billDetailsRepo.findAllByBill(bill);
        BigDecimal totalMoney = lstBillDetail.stream()
                .map(billDetails -> billDetails.getUnitPrice().multiply(new BigDecimal(billDetails.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal voucherMoney = BigDecimal.ZERO;
        if (billRequest.getVoucher() != null) {
            Optional<VoucherDetails> voucherDetailsOpt = voucherDetailsService.findVoucherDetailsByCustomerAndVoucher(bill.getCustomer().getId(), billRequest.getVoucher());
            if (voucherDetailsOpt.isEmpty()) {
                return new ResponseEntity<>(new ResponseObject("error", "Không tìm thấy giảm giá có thể khách hàng chưa sở hữu hoặc đã sử dụng", 1, billDto), HttpStatus.BAD_REQUEST);
            }
            VoucherDetails voucherDetails = voucherDetailsOpt.get();
            Voucher voucher = voucherDetails.getVoucher();

            if (voucher.getQuantity() < 1) {
                return new ResponseEntity<>(new ResponseObject("error", "Số lượng giảm giá đã hết vui lòng chọn giảm giá khác", 1, billDto), HttpStatus.BAD_REQUEST);
            }
            // compareTo returns âm thì big 1 < big 2
            // compareTo returns bằng 0 thì big -1 = big 2
            // compareTo returns dương thì big -1 > big 2
            if ((totalMoney.compareTo(voucher.getOrderMinValue()) < 0)) {
                return new ResponseEntity<>(new ResponseObject("error", "Đơn hàng chưa đạt giá trị tối thiểu để dử dụng giảm giá này", 0, billRequest), HttpStatus.BAD_REQUEST);
            }
            if (DiscountType.GIAM_TRUC_TIEP.getLabel().equalsIgnoreCase(voucher.getDiscountType())) {
                // compareTo returns âm thì big 1 < big 2
                // compareTo returns bằng 0 thì big -1 = big 2
                // compareTo returns dương thì big -1 > big 2
                if (totalMoney.compareTo(new BigDecimal(Float.toString(voucher.getValue()))) < 0){
                    voucherMoney = totalMoney;
                }else {
                    voucherMoney = new BigDecimal(Float.toString(voucher.getValue()));
                }
            } else {
                voucherMoney = totalMoney.multiply(new BigDecimal(Float.toString(voucher.getValue()))).divide(new BigDecimal("100"));
            }
            // compareTo returns âm thì big 1 < big 2
            // compareTo returns bằng 0 thì big -1 = big 2
            // compareTo returns dương thì big -1 > big 2
            if ((voucherMoney.compareTo(voucher.getMaxDiscountValue()) > 0)) {
                voucherMoney = voucher.getMaxDiscountValue();
            }
            voucherDetails.setStatus(true);
            voucherDetails.setUsedDate(LocalDateTime.now());
            voucherDetails.setBill(bill);
            voucherDetailsRepo.save(voucherDetails);
            voucher.setQuantity(voucher.getQuantity() -1);
            voucherRepo.save(voucher);
        }


        BigDecimal intoMoney = totalMoney
                .subtract(voucherMoney)
                .add(billRequest.getShipMoney());
        Bill billOpt = billRepo.findById(bill.getId()).get();
        billOpt.setTotalMoney(totalMoney);
        billOpt.setShipMoney(billRequest.getShipMoney());
        billOpt.setVoucherMoney(voucherMoney);
        billOpt.setIntoMoney(intoMoney);
        if(intoMoney.compareTo(BigDecimal.ZERO) < 0){
            billOpt.setIntoMoney(BigDecimal.ZERO);
        }
        update(billOpt);
        historyBillService.addHistoryBill(billOpt, TrangThaiBill.TAO_DON_HANG.getLabel(), "");
        cartDetailsRepo.deleteAll(lstCartDetails);

        if (billOpt.getPaymentMethod().equalsIgnoreCase(PaymentMethod.CHUYEN_KHOAN.getLabel())) {
            historyBillService.addHistoryBill(billOpt, TrangThaiBill.CHO_THANH_TOAN.getLabel(), "");
            return vnpayService.createPayment(billOpt, billRequest.getReturnUrl());
        }
        historyBillService.addHistoryBill(billOpt, TrangThaiBill.CHO_XAC_NHAN.getLabel(), "");

        return new ResponseEntity<>(new ResponseObject("success", "Đặt Hàng Thành Công", 0, bill), HttpStatus.CREATED);

    }

    @Override
    public ResponseEntity<ResponseObject> buyBillClientGuest(BillRequest billRequest) throws UnsupportedEncodingException {
        if (billRequest.getLstCartDetails().size() == 0) {
            return new ResponseEntity<>(new ResponseObject("error", "Chon it nhat 1 san pham", 1, billRequest), HttpStatus.BAD_REQUEST);
        }

        if (billRequest.getVoucher() != null) {
            Optional<VoucherDetails> voucherDetailsOpt = voucherDetailsService.findVoucherDetailsByCustomerAndVoucher(null, billRequest.getVoucher());
            if (voucherDetailsOpt.isEmpty()) {
                return new ResponseEntity<>(new ResponseObject("error", "Không tìm thấy giảm giá có thể khách hàng chưa sở hữu hoặc đã sử dụng", 1, billRequest), HttpStatus.BAD_REQUEST);
            }
            VoucherDetails voucherDetails = voucherDetailsOpt.get();
            Voucher voucher = voucherDetails.getVoucher();

            if (voucher.getQuantity() < 1) {
                return new ResponseEntity<>(new ResponseObject("error", "Số lượng giảm giá đã hết vui lòng chọn giảm giá khác", 1, billRequest), HttpStatus.BAD_REQUEST);
            }
            // compareTo returns âm thì big 1 < big 2
            // compareTo returns bằng 0 thì big -1 = big 2
            // compareTo returns dương thì big -1 > big 2
            if ((billRequest.getTotalMoney().compareTo(voucher.getOrderMinValue()) < 0)) {
                return new ResponseEntity<>(new ResponseObject("error", "Đơn hàng chưa đạt giá trị tối thiểu để dử dụng giảm giá này", 0, billRequest), HttpStatus.BAD_REQUEST);
            }
        }
        for (CartDetails cartDetails : billRequest.getLstCartDetails()) {
            Optional<ProductDetails> poductDetailsOpt = productDetailsService.findByProductDetailWhereDeletedAndStatus(cartDetails.getProductDetails().getId(), TrangThai.HOAT_DONG.getLabel());
            if (poductDetailsOpt.isEmpty()) {
                return new ResponseEntity<>(new ResponseObject("error", "Sản phẩm bạn chọn không có trong cửa hàng hoặc đã được bán hết", 001, cartDetails.getProductDetails().getId()), HttpStatus.BAD_REQUEST);

            }

            ProductDetails productDetails = poductDetailsOpt.get();
            if (cartDetails.getQuantity() > productDetails.getQuantity()) {
                return new ResponseEntity<>(new ResponseObject("error", "Số lượng sản phẩm " + productDetails.getProduct().getName() + " trong kho không đủ chỉ còn " + productDetails.getQuantity(), 001, cartDetails), HttpStatus.BAD_REQUEST);
            }
        }


        Bill billDto = mapper.map(billRequest, Bill.class);
        billDto.setId(null);
        billDto.setCodeBill(invoiceGenerator.generateInvoiceNumber());
        billDto.setBillType(BillType.ONLINE.getLabel());
        billDto.setBillFormat(BillType.DELIVERY.getLabel());
        billDto.setBookingDate(new Date());
        billDto.setDeleted(false);
        billDto.setCreatedBy("Admin");
        billDto.setCreatedDate(LocalDateTime.now());
        billDto.setLastModifiedDate(LocalDateTime.now());
        billDto.setLastModifiedBy("Admin");
        billDto.setCustomer(null);
        billDto.setStatus(TrangThaiBill.CHO_XAC_NHAN.getLabel());
        billDto.setIntoMoney(BigDecimal.ZERO);
        billDto.setTotalMoney(BigDecimal.ZERO);
        billDto.setVoucherMoney(BigDecimal.ZERO);

        Bill bill = billRepo.save(billDto);

        for (CartDetails cartDetails : billRequest.getLstCartDetails()) {
            BillDetails billDetails = new BillDetails();
            Optional<ProductDetails> poductDetailsOpt = productDetailsService.findByProductDetailWhereDeletedAndStatus(cartDetails.getProductDetails().getId(), TrangThai.HOAT_DONG.getLabel());
            ProductDetails productDetails = poductDetailsOpt.get();
            billDetails.setBill(bill);
            billDetails.setProductDetails(productDetails);
            // Cần xác định giá của sản phẩm từ đâu
            if (productDetails.getPromotionDetailsActive() != null) {
                BigDecimal price = productDetails.getPrice().subtract(
                        productDetails.getPrice()
                                .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                        .divide(BigDecimal.valueOf(100))));
                billDetails.setUnitPrice(price);
                billDetails.setDiscount(productDetails.getPrice()
                        .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                .divide(BigDecimal.valueOf(100))));
                billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
            } else {
                billDetails.setUnitPrice(productDetails.getPrice());
                billDetails.setDiscount(BigDecimal.ZERO);
                billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
            }
            billDetails.setStatus(TrangThaiBill.DANG_BAN.getLabel());
            billDetails.setOriginalPrice(productDetails.getPrice());
            billDetails.setId(null);
            billDetails.setDeleted(false);
            billDetails.setCreatedBy("Admin");
            billDetails.setCreatedDate(LocalDateTime.now());
            billDetails.setLastModifiedDate(LocalDateTime.now());
            billDetails.setLastModifiedBy("Admin");
            if (billDto.getPaymentMethod().equalsIgnoreCase(PaymentMethod.CHUYEN_KHOAN.getLabel())) {
                billDetails.setQuantity(cartDetails.getQuantity());
                productDetails.setQuantity(productDetails.getQuantity() - cartDetails.getQuantity());
            } else {
                billDetails.setQuantity(cartDetails.getQuantity());
            }

            productDetailsRepo.save(productDetails);
            billDetailsRepo.save(billDetails);
        }

        List<BillDetails> lstBillDetail = billDetailsRepo.findAllByBill(bill);
        BigDecimal totalMoney = lstBillDetail.stream()
                .map(billDetails -> billDetails.getUnitPrice().multiply(new BigDecimal(billDetails.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal voucherMoney = BigDecimal.ZERO;
        if (billRequest.getVoucher() != null) {
            Optional<VoucherDetails> voucherDetailsOpt = voucherDetailsService.findVoucherDetailsByCustomerAndVoucher(null, billRequest.getVoucher());
            if (voucherDetailsOpt.isEmpty()) {
                return new ResponseEntity<>(new ResponseObject("error", "Không tìm thấy giảm giá có thể khách hàng chưa sở hữu hoặc đã sử dụng", 1, billDto), HttpStatus.BAD_REQUEST);
            }
            VoucherDetails voucherDetails = voucherDetailsOpt.get();
            Voucher voucher = voucherDetails.getVoucher();

            if (voucher.getQuantity() < 1) {
                return new ResponseEntity<>(new ResponseObject("error", "Số lượng giảm giá đã hết vui lòng chọn giảm giá khác", 1, billDto), HttpStatus.BAD_REQUEST);
            }
            // compareTo returns âm thì big 1 < big 2
            // compareTo returns bằng 0 thì big -1 = big 2
            // compareTo returns dương thì big -1 > big 2
            if ((totalMoney.compareTo(voucher.getOrderMinValue()) < 0)) {
                return new ResponseEntity<>(new ResponseObject("error", "Đơn hàng chưa đạt giá trị tối thiểu để dử dụng giảm giá này", 0, billRequest), HttpStatus.BAD_REQUEST);
            }
            if (DiscountType.GIAM_TRUC_TIEP.getLabel().equalsIgnoreCase(voucher.getDiscountType())) {
                // compareTo returns âm thì big 1 < big 2
                // compareTo returns bằng 0 thì big -1 = big 2
                // compareTo returns dương thì big -1 > big 2
                if (totalMoney.compareTo(new BigDecimal(Float.toString(voucher.getValue()))) < 0){
                    voucherMoney = totalMoney;
                }else {
                    voucherMoney = new BigDecimal(Float.toString(voucher.getValue()));
                }
            } else {
                voucherMoney = totalMoney.multiply(new BigDecimal(Float.toString(voucher.getValue()))).divide(new BigDecimal("100"));
            }
            // compareTo returns âm thì big 1 < big 2
            // compareTo returns bằng 0 thì big -1 = big 2
            // compareTo returns dương thì big -1 > big 2
            if ((voucherMoney.compareTo(voucher.getMaxDiscountValue()) > 0)) {
                voucherMoney = voucher.getMaxDiscountValue();
            }
            voucherDetails.setStatus(true);
            voucherDetails.setUsedDate(LocalDateTime.now());
            voucherDetails.setBill(bill);
            voucherDetailsRepo.save(voucherDetails);
            voucher.setQuantity(voucher.getQuantity() -1);
            voucherRepo.save(voucher);
        }


        BigDecimal intoMoney = totalMoney
                .subtract(voucherMoney)
                .add(billRequest.getShipMoney());

        Bill billOpt = billRepo.findById(bill.getId()).get();
        billOpt.setShipMoney(billRequest.getShipMoney());
        billOpt.setTotalMoney(totalMoney);
        billOpt.setVoucherMoney(voucherMoney);
        billOpt.setIntoMoney(intoMoney);
        if(intoMoney.compareTo(BigDecimal.ZERO) < 0){
            billOpt.setIntoMoney(BigDecimal.ZERO);
        }
        update(billOpt);
        historyBillService.addHistoryBill(billOpt, TrangThaiBill.TAO_DON_HANG.getLabel(), "");

        if (billOpt.getPaymentMethod().equalsIgnoreCase(PaymentMethod.CHUYEN_KHOAN.getLabel())) {
            historyBillService.addHistoryBill(billOpt, TrangThaiBill.CHO_THANH_TOAN.getLabel(), "");
            return vnpayService.createPayment(billOpt, billRequest.getReturnUrl());
        }
        historyBillService.addHistoryBill(billOpt, TrangThaiBill.CHO_XAC_NHAN.getLabel(), "");

        return new ResponseEntity<>(new ResponseObject("success", "Đặt Hàng Thành Công", 0, bill), HttpStatus.CREATED);

    }


    @Override
    public List<Bill> findBillByCustomerAndStatusAndStatusNot(String status, String statusNot) {
        Customer customer = authentication.getCustomer();
        return repository.findBillByCustomerAndStatusAndStatusNot(customer, status, statusNot);
    }

    @Override
    public List<Bill> findAllTypeAndStatus(String type, String status) {
        return repository.findAllTypeAndStatus(type, status);
    }

    @Override
    public ResponseEntity<ResponseObject> billCounterNew() {
        Bill entity = new Bill();
        entity.setBillType(BillType.OFFLINE.getLabel());
        entity.setStatus(TrangThaiBill.NEW.getLabel());
        entity.setCodeBill(invoiceGenerator.generateInvoiceNumber());
        entity.setId(null);
        entity.setDeleted(false);
        entity.setCreatedBy("Admin");
        entity.setCreatedDate(LocalDateTime.now());
        entity.setLastModifiedDate(LocalDateTime.now());
        entity.setLastModifiedBy("Admin");
        Integer count = billRepo.countBillsByTypeAndStatus(BillType.OFFLINE.getLabel(), TrangThaiBill.NEW.getLabel());
        // Kiểm tra nếu count là null hoặc lớn hơn hoặc bằng 5
        if (count != null && count >= 5) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Được Tạo Quá 5 Hóa Đơn ", 0, entity), HttpStatus.BAD_REQUEST);
        }
        System.out.println("Hóa Đơn Đã cớ+ " + count);
        Bill bill = billRepo.save(entity);
        historyBillService.addHistoryBill(bill, TrangThaiBill.TAO_DON_HANG.getLabel(), "");
        return new ResponseEntity<>(new ResponseObject("success", "Tạo Hóa Đơn Thành Công", 0, entity), HttpStatus.CREATED);

    }

    @Override
    public ResponseEntity<ResponseObject> countersAddProduct(List<BillDetailsRequest> lstBillDetailsDto, Long id) {
        Optional<Bill> billOpt = billRepo.findById(id);
        if (billOpt.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Id Hóa Đơn", 1, id), HttpStatus.BAD_REQUEST);
        }
        if (lstBillDetailsDto.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Chọn ít nhất 1 sản phẩm", 1, null), HttpStatus.BAD_REQUEST);
        }

        for (BillDetailsRequest billDetailsDto : lstBillDetailsDto) {
            Optional<ProductDetails> productDetailOpt = productDetailsRepo.findByProductDetailWhereDeletedAndStatus(billDetailsDto.getProductDetails(), TrangThai.HOAT_DONG.getLabel());
            if (productDetailOpt.isEmpty()) {
                return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Sản Phẩm", 1, null), HttpStatus.BAD_REQUEST);
            }

            Bill bill = billOpt.get();
            ProductDetails productDetails = productDetailOpt.get();

            Optional<BillDetails> billDetailsOpt = billDetailsRepo.findByBillAndProductDetails(bill, productDetails);

            if (productDetails.getQuantity() <= 0) {
                return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Sản Phẩm Là 0", 1, null), HttpStatus.BAD_REQUEST);
            }
            if (productDetails.getQuantity() < billDetailsDto.getQuantity()) {
                return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Sản Phẩm Không Đủ", 1, null), HttpStatus.BAD_REQUEST);
            }

            if (billDetailsOpt.isPresent()) {
                BillDetails billDetails = billDetailsOpt.get();
                billDetails.setQuantity(billDetails.getQuantity() + billDetailsDto.getQuantity());
                billDetailsService.update(billDetails);
                productDetails.setQuantity(productDetails.getQuantity() - billDetailsDto.getQuantity());
                productDetailsService.update(productDetails);
            } else {
                BillDetails billDetails = mapper.map(billDetailsDto, BillDetails.class);
                billDetails.setId(null);
                billDetails.setBill(bill);
                billDetails.setDiscount(BigDecimal.ZERO);
                billDetails.setOriginalPrice(BigDecimal.ZERO);
                billDetails.setUnitPrice(BigDecimal.ZERO);
                billDetails.setQuantity(billDetailsDto.getQuantity());
                billDetails.setProductDetails(productDetails);
                billDetails.setStatus(TrangThaiBill.DANG_BAN.getLabel());
                billDetailsService.createNew(billDetails);

                productDetails.setQuantity(productDetails.getQuantity() - billDetailsDto.getQuantity());
                productDetailsService.update(productDetails);
            }
        }

        return new ResponseEntity<>(new ResponseObject("success", "Thêm Sản Phẩm Thành Công", 0, lstBillDetailsDto), HttpStatus.CREATED);


    }

    @Override
    public ResponseEntity<ResponseObject> billUpdateCustomer(BillRequest billRequest, Long id) {
        Optional<Bill> otp = billRepo.findById(id);
        if (otp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Id Hóa Đơn", 0, id), HttpStatus.BAD_REQUEST);
        }
        Bill bill = otp.get();
        if (billRequest.getCustomer() == null) {
            bill.setCustomer(null);
        } else {
            Optional<Customer> customerOpt = customerRepo.findById(billRequest.getCustomer());
            if (customerOpt.isPresent()) {
                bill.setCustomer(customerOpt.get());
            } else {
                bill.setCustomer(null);
                return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Khách Hàng", 0, id), HttpStatus.BAD_REQUEST);
            }
        }
        update(bill);
        return new ResponseEntity<>(new ResponseObject("success", "Chọn Khách Hàng Thành Công", 0, billRequest), HttpStatus.CREATED);

    }

    @Override
    public ResponseEntity<ResponseObject> countersAddProductBarcode(Long id, String barcode) {
        Optional<Bill> otp = billRepo.findById(id);
        if (otp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Hóa Đơn", 0, id), HttpStatus.BAD_REQUEST);
        }
        Optional<ProductDetails> productDetailOtp = productDetailsRepo.findByBarcode(barcode);
        if (productDetailOtp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không tìm thấy sản phẩm có thể sản phẩm đã bị xóa hoặc ngừng hoạt động", 1, barcode), HttpStatus.BAD_REQUEST);
        }

        Bill bill = otp.get();
        ProductDetails productDetails = productDetailOtp.get();


        Optional<BillDetails> billDetailsOpt = billDetailsRepo.findByBillAndProductDetails(bill, productDetails);

        if (productDetails.getQuantity() <= 0) {
            return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Sản Phẩm Là 0", 1, null), HttpStatus.BAD_REQUEST);
        }

        if (billDetailsOpt.isPresent()) {
            BillDetails billDetails = billDetailsOpt.get();
            billDetails.setQuantity(billDetails.getQuantity() + 1);
            billDetailsService.update(billDetails);
            productDetails.setQuantity(productDetails.getQuantity() - 1);
            productDetailsService.update(productDetails);
        } else {
            BillDetails billDetails = new BillDetails();
            billDetails.setId(null);
            billDetails.setBill(bill);
            billDetails.setDiscount(BigDecimal.ZERO);
            billDetails.setOriginalPrice(BigDecimal.ZERO);
            billDetails.setUnitPrice(BigDecimal.ZERO);
            billDetails.setQuantity(1);
            billDetails.setProductDetails(productDetails);
            billDetails.setStatus(TrangThaiBill.DANG_BAN.getLabel());
            billDetailsService.createNew(billDetails);

            productDetails.setQuantity(productDetails.getQuantity() - 1);
            productDetailsService.update(productDetails);
        }

        return new ResponseEntity<>(new ResponseObject("success", "Thêm Sản Phẩm Thành Công", 0, barcode), HttpStatus.CREATED);

    }

    @Override
    public ResponseEntity<ResponseObject> chaneQuantityBillDetails(BillDetailsRequest billDto, Long idBillDetail) {
        Optional<BillDetails> opt = billDetailsRepo.findById(idBillDetail);
        if (opt.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy IdBilDetails Hóa Đơn", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }
        System.out.println("idBillDetail" + idBillDetail);
        Optional<ProductDetails> optProductDetails = productDetailsService.findById(opt.get().getProductDetails().getId());
        if (optProductDetails.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Sản Phẩm Trong Giỏ Hàng", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }

        if (billDto.getQuantity() == null) {
            return new ResponseEntity<>(new ResponseObject("error", "Vui lòng nhập số lượng", 1, null), HttpStatus.BAD_REQUEST);
        }
        if (billDto.getQuantity() < 1) {
            return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Phải Lớn Hơn 0", 1, null), HttpStatus.BAD_REQUEST);
        }

        ProductDetails productDetails = optProductDetails.get();

        productDetails.setQuantity(productDetails.getQuantity() + opt.get().getQuantity());

        if (productDetails.getQuantity() < billDto.getQuantity()) {
            return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Sản Phẩm Không Đủ", 1, null), HttpStatus.BAD_REQUEST);
        }


        productDetails.setQuantity(productDetails.getQuantity() - billDto.getQuantity());

        BillDetails billDetails = opt.get();
        billDetails.setQuantity(billDto.getQuantity());

        // Cần xác định giá của sản phẩm từ đâu
        if (productDetails.getPromotionDetailsActive() != null) {
            BigDecimal price = productDetails.getPrice().subtract(
                    productDetails.getPrice()
                            .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                    .divide(BigDecimal.valueOf(100))));
            billDetails.setUnitPrice(price);
            billDetails.setDiscount(productDetails.getPrice()
                    .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                            .divide(BigDecimal.valueOf(100))));
            billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
        } else {
            billDetails.setUnitPrice(productDetails.getPrice());
            billDetails.setDiscount(BigDecimal.ZERO);
            billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
        }

        billDetailsRepo.save(billDetails);
        productDetailsRepo.save(productDetails);
        return new ResponseEntity<>(new ResponseObject("success", "Cập Nhật Số Lượng Thành Công", 0, billDto), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ResponseObject> billCounterPay(BillRequest billDto, Long id) throws UnsupportedEncodingException {

        Optional<Bill> otp = findById(id);
        if (otp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Hóa Đơn", 1, billDto), HttpStatus.BAD_REQUEST);
        }
        Bill bill = otp.get();
        if (bill.getStatus().equalsIgnoreCase(TrangThaiBill.HOAN_THANH.getLabel())) {
            return new ResponseEntity<>(new ResponseObject("error", "Hóa Đơn Này Đã Thanh Toán", 1, billDto), HttpStatus.BAD_REQUEST);
        }

        List<BillDetails> lstBillDetail = billDetailsRepo.findAllByBill(bill);
        if (lstBillDetail.size() == 0) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Sản Phẩm Trong Giỏ Hàng", 0, id), HttpStatus.BAD_REQUEST);
        }

        for (BillDetails billDetails: lstBillDetail){
                ProductDetails productDetails = billDetails.getProductDetails();
                // Cần xác định giá của sản phẩm từ đâu
                if (productDetails.getPromotionDetailsActive() != null) {
                    BigDecimal price = productDetails.getPrice().subtract(
                            productDetails.getPrice()
                                    .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                            .divide(BigDecimal.valueOf(100))));
                    billDetails.setUnitPrice(price);
                    billDetails.setDiscount(productDetails.getPrice()
                            .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                    .divide(BigDecimal.valueOf(100))));
                    billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
                } else {
                    billDetails.setUnitPrice(productDetails.getPrice());
                    billDetails.setDiscount(BigDecimal.ZERO);
                    billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
                }
                billDetails.setOriginalPrice(productDetails.getPrice());
                billDetails.setQuantity(billDetails.getQuantity());
                billDetailsRepo.save(billDetails);
        }


        bill = mapper.map(billDto, Bill.class);
        BigDecimal totalMoney = lstBillDetail.stream()
                .map(billDetails -> billDetails.getUnitPrice().multiply(new BigDecimal(billDetails.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal voucherMoney = BigDecimal.ZERO;
        if (billDto.getVoucher() != null) {
            Optional<VoucherDetails> voucherDetailsOpt = voucherDetailsService.findVoucherDetailsByCustomerAndVoucher(otp.get().getCustomer().getId(), billDto.getVoucher());
            if (voucherDetailsOpt.isEmpty()) {
                return new ResponseEntity<>(new ResponseObject("error", "Không tìm thấy giảm giá có thể khách hàng chưa sở hữu hoặc đã sử dụng", 1, billDto), HttpStatus.BAD_REQUEST);
            }
            VoucherDetails voucherDetails = voucherDetailsOpt.get();
            Voucher voucher = voucherDetails.getVoucher();

            if (voucher.getQuantity() < 1) {
                return new ResponseEntity<>(new ResponseObject("error", "Số lượng giảm giá đã hết vui lòng chọn giảm giá khác", 1, billDto), HttpStatus.BAD_REQUEST);
            }
            // compareTo returns âm thì big 1 < big 2
            // compareTo returns bằng 0 thì big -1 = big 2
            // compareTo returns dương thì big -1 > big 2
            if ((totalMoney.compareTo(voucher.getOrderMinValue()) < 0)) {
                return new ResponseEntity<>(new ResponseObject("error", "Đơn hàng chưa đạt giá trị tối thiểu để dử dụng giảm giá này", 0, id), HttpStatus.BAD_REQUEST);
            }
            if (DiscountType.GIAM_TRUC_TIEP.getLabel().equalsIgnoreCase(voucher.getDiscountType())) {
                // compareTo returns âm thì big 1 < big 2
                // compareTo returns bằng 0 thì big -1 = big 2
                // compareTo returns dương thì big -1 > big 2
                if (totalMoney.compareTo(new BigDecimal(Float.toString(voucher.getValue()))) < 0){
                    voucherMoney = totalMoney;
                }else {
                    voucherMoney = new BigDecimal(Float.toString(voucher.getValue()));
                }
            } else {
                voucherMoney = totalMoney.multiply(new BigDecimal(Float.toString(voucher.getValue()))).divide(new BigDecimal("100"));
            }
            // compareTo returns âm thì big 1 < big 2
            // compareTo returns bằng 0 thì big -1 = big 2
            // compareTo returns dương thì big -1 > big 2
            if ((voucherMoney.compareTo(voucher.getMaxDiscountValue()) > 0)) {
                voucherMoney = voucher.getMaxDiscountValue();
            }


            voucherDetails.setBill(otp.get());
            voucher.setQuantity(voucher.getQuantity() -1);
            voucherDetails.setStatus(true);
            voucherDetails.setUsedDate(LocalDateTime.now());
            voucherDetailsRepo.save(voucherDetails);
            voucherRepo.save(voucher);
        }

        BigDecimal intoMoney = totalMoney
                .subtract(voucherMoney)
                .add(bill.getShipMoney());

        bill.setId(id);
        bill.setTotalMoney(totalMoney);
        bill.setIntoMoney(intoMoney);
        bill.setBookingDate(new Date());
        bill.setPaymentDate(new Date());
        bill.setBillType(otp.get().getBillType());
        bill.setBillFormat(otp.get().getBillType());
        bill.setCodeBill(otp.get().getCodeBill());
        bill.setCustomer(otp.get().getCustomer());
        bill.setUser(otp.get().getUser());
        bill.setVoucherMoney(voucherMoney);
        if(intoMoney.compareTo(BigDecimal.ZERO) < 0){
            bill.setIntoMoney(BigDecimal.ZERO);
        }

        if (billDto.getIsDelivery()) {
            bill.setBillFormat(BillType.DELIVERY.getLabel());
            bill.setStatus(TrangThaiBill.DA_XAC_NHAN.getLabel());
            historyBillService.addHistoryBill(bill, TrangThaiBill.DA_XAC_NHAN.getLabel(), "");
        }
        if (!billDto.getIsDelivery() && billDto.getPaymentMethod().equalsIgnoreCase(PaymentMethod.TIEN_MAT.getLabel())){
            historyBillService.addHistoryBill(bill, TrangThaiBill.HOAN_THANH.getLabel(), "");
            bill.setStatus(TrangThaiBill.HOAN_THANH.getLabel());
        }

        if (billDto.getPaymentMethod().equalsIgnoreCase(PaymentMethod.CHUYEN_KHOAN.getLabel()) && !billDto.getIsDelivery()) {
            bill.setStatus(otp.get().getStatus());
            update(bill);
            return vnpayService.createPayment(bill, billDto.getReturnUrl());
        }

        update(bill);

        if (!billDto.getIsDelivery()) {
            PaymentHistory paymentHistory = new PaymentHistory();
            paymentHistory.setBill(bill);
            paymentHistory.setDescription("");
            paymentHistory.setPaymentMethod(PaymentMethod.TIEN_MAT.getLabel());
            paymentHistory.setType(PaymentMethod.TIEN_MAT.getLabel());
            paymentHistory.setStatus("0"); // hoàn thành
            paymentHistory.setPaymentDate(LocalDateTime.now());
            paymentHistory.setPaymentAmount(bill.getIntoMoney());
            paymentHistory.setDeleted(false);
            paymentHistoryService.createNew(paymentHistory);
        }

        return new ResponseEntity<>(new ResponseObject("success", "Đặt Hàng Thành Công", 0, bill), HttpStatus.CREATED);

    }

    @Override
    public ResponseEntity<ResponseObject> billDeleteBillDetail(Long idBillDetail) {

        Optional<BillDetails> opt = billDetailsRepo.findById(idBillDetail);
        if (opt.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy BilDetails Hóa Đơn", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }
        BillDetails billDetails = opt.get();
        Optional<ProductDetails> productDetailsOtp = productDetailsRepo.findById(billDetails.getProductDetails().getId());

        if (productDetailsOtp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Sản Phẩm Trong Giỏ", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }

        ProductDetails productDetails = productDetailsOtp.get();
        productDetails.setQuantity(productDetails.getQuantity() + billDetails.getQuantity());
        productDetailsRepo.save(productDetails);

        billDetailsRepo.delete(billDetails);
//       updateChangeMoneyBill();

        return new ResponseEntity<>(new ResponseObject("success", "Xóa sản phẩm thành công", 0, idBillDetail), HttpStatus.CREATED);

    }

    @Override
    public ResponseEntity<ResponseObject> billDeleteAllBillDetail(Long idBill) {
        Optional<Bill> otp = findById(idBill);
        if (otp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Hóa Đơn", 1, idBill), HttpStatus.BAD_REQUEST);
        }

        List<BillDetails> lstBillDetails = billDetailsService.findAllByBill(otp.get());
        lstBillDetails.stream().map(billDetails -> {
            ProductDetails productDetails = billDetails.getProductDetails();
            productDetails.setQuantity(productDetails.getQuantity() + billDetails.getQuantity());
            productDetailsRepo.save(productDetails);
            return billDetails;
        }).collect(Collectors.toList());

        billDetailsRepo.deleteAll(lstBillDetails);
        return new ResponseEntity<>(new ResponseObject("success", "Xóa tất cả sản phẩm thành công", 0, idBill), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ResponseObject> deleteBillToBillDetailAll(Long idBill) {
        Optional<Bill> otp = findById(idBill);
        if (otp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Hóa Đơn", 1, idBill), HttpStatus.BAD_REQUEST);
        }
        List<BillDetails> lstBillDetails = billDetailsRepo.findAllByBill(otp.get());
        for (BillDetails billDetails : lstBillDetails) {
            billDeleteBillDetail(billDetails.getId());
        }

        //Xóa history dang mao ping
        List<HistoryBill> lstHistoryBill = historyBillRepo.findByIdBill(otp.get().getId());
        lstHistoryBill.stream().map(historyBill -> {
            historyBillRepo.delete(historyBill);
            return historyBill;
        }).collect(Collectors.toList());


        physicalDelete(idBill);
        return new ResponseEntity<>(new ResponseObject("success", "Đã Xóa Hóa Đơn Thành Công", 0, otp.get()), HttpStatus.OK);
    }

    @Override
    public Page<Bill> findAllDeletedFalseAndStatusAndStatusNot(Pageable page, Map<String, Object> keyWork, String statusNot) {
        return repository.findAllDeletedFalseAndStatusAndStatusNot(page, keyWork, statusNot);
    }

    @Override
    public Boolean updateChangeMoneyBill(Long idBill) {

        Optional<Bill> opt = findById(idBill);
        if (opt.isEmpty()) {
            return false;
        }
        Bill bill = opt.get();
        List<BillDetails> lstBillDetails = billDetailsService.findAllByBill(bill);
        if (lstBillDetails.size() == 0) {
            return false;
        }


        System.out.println(bill.getReceiverProvince() + " " + bill.getReceiverDistrict() + " " + bill.getReceiverCommune());

        RestTemplate restTemplate = new RestTemplate();
        String urlProvince = "https://online-gateway.ghn.vn/shiip/public-api/master-data/province";
        String urlDistrict = "https://online-gateway.ghn.vn/shiip/public-api/master-data/district";
        String urlWard = "https://online-gateway.ghn.vn/shiip/public-api/master-data/ward";
        String urlService = "https://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/available-services";
        String urlMoneyShip = "https://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/fee";
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", "dfe1e7cf-e582-11ee-b290-0e922fc774da");
        headers.set("shop_id", "4962936");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        CompletableFuture<String> provinceFuture = CompletableFuture.supplyAsync(() -> {
                    String responseBodyProvince = restTemplate.exchange(urlProvince, HttpMethod.GET, entity, String.class).getBody();
                    ObjectMapper objectMapper = new ObjectMapper();
                    try {
                        JsonNode jsonNode = objectMapper.readTree(responseBodyProvince);
                        JsonNode dataNodeProvince = jsonNode.get("data");
                        if (dataNodeProvince.isArray()) {
                            for (JsonNode provinceNode : dataNodeProvince) {
                                if (provinceNode.get("ProvinceName").asText().equalsIgnoreCase(bill.getReceiverProvince())) {
                                    System.out.println(provinceNode.get("ProvinceID").asText());
                                    System.out.println(provinceNode.get("ProvinceName").asText());
                                    return provinceNode.get("ProvinceID").asText();
                                }
                            }
                        }
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
        );


        CompletableFuture<String> districtFuture = provinceFuture.thenCompose(province -> {
            return CompletableFuture.supplyAsync(() -> {
                if (province == null) {
                    return null;
                }
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(urlDistrict)
                        .queryParam("province_id", province);
                String urlDistrictParam = builder.toUriString();
                System.out.println(urlDistrictParam);
                ResponseEntity<String> responseDistrict = restTemplate.exchange(urlDistrictParam, HttpMethod.GET, entity, String.class);
                String responseBodyDistrict = responseDistrict.getBody();
                // Phân tích cú pháp JSON
                ObjectMapper objectMapperDistrict = new ObjectMapper();
                try {
                    JsonNode jsonNode = objectMapperDistrict.readTree(responseBodyDistrict);
                    JsonNode dataNodeDistrict = jsonNode.get("data");
                    if (dataNodeDistrict.isArray()) {
                        for (JsonNode districtNode : dataNodeDistrict) {
                            if (districtNode.get("DistrictName").asText().equalsIgnoreCase(bill.getReceiverDistrict())) {
                                return districtNode.get("DistrictID").asText();
                            }
                        }
                    }
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }

                return null;
            });
        });

        CompletableFuture<String> wardFuture = districtFuture.thenCompose(district -> {
            return CompletableFuture.supplyAsync(() -> {
                if (district == null) {
                    return null;
                }
                //lấy wardId
                UriComponentsBuilder builderWard = UriComponentsBuilder.fromHttpUrl(urlWard)
                        .queryParam("district_id", district);
                String urlWardParam = builderWard.toUriString();
                System.out.println(urlWardParam);
                ResponseEntity<String> responseWard = restTemplate.exchange(urlWardParam, HttpMethod.GET, entity, String.class);
                String responseBodyWard = responseWard.getBody();
                // Phân tích cú pháp JSON
                ObjectMapper objectMapperWard = new ObjectMapper();
                try {
                    JsonNode jsonNode = objectMapperWard.readTree(responseBodyWard);
                    JsonNode dataNodeWard = jsonNode.get("data");
                    if (dataNodeWard.isArray()) {
                        for (JsonNode wardNode : dataNodeWard) {
                            if (wardNode.get("WardName").asText().equalsIgnoreCase(bill.getReceiverCommune())) {
                                return wardNode.get("WardCode").asText();
                            }
                        }
                    }
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }

                return null;
            });
        });


        String provinceId = provinceFuture.join();
        String districtId = districtFuture.join();
        String wardId = wardFuture.join();
        Integer insuranceValue = 10000;
        String serviceId = "53321";
        String fromDistrictId = "3440";
        Integer weightProduct = 1000;
        String coupon = null;
        Integer moneyShip = 0;

        if (provinceId == null && districtId == null) {
            return false;
        }

        insuranceValue = lstBillDetails.stream()
                .map(currentProduct -> currentProduct.getUnitPrice().multiply(BigDecimal.valueOf(currentProduct.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).intValue();

        weightProduct = lstBillDetails.stream()
                .mapToInt(currentProduct -> currentProduct.getProductDetails().getWeight() * currentProduct.getQuantity())
                .sum();

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(urlService)
                .queryParam("to_district", districtId).queryParam("shop_id", 4962936).queryParam("from_district", 3440);
        String urlServiceParam = builder.toUriString();
        System.out.println(urlServiceParam);
        ResponseEntity<String> responseService = restTemplate.exchange(urlServiceParam, HttpMethod.GET, entity, String.class);
        String responseBodyService = responseService.getBody();
        // Phân tích cú pháp JSON
        ObjectMapper objectMapperService = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapperService.readTree(responseBodyService);
            JsonNode dataNodeService = jsonNode.get("data");
            if (dataNodeService.isArray()) {
                System.out.println(dataNodeService);
                serviceId = dataNodeService.get(0).get("service_id").asText(); //lay serviceId
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }


        //Lấy Phí SHip
        UriComponentsBuilder builderMoneyShip = UriComponentsBuilder.fromHttpUrl(urlMoneyShip)
                .queryParam("service_id", serviceId).queryParam("insurance_value", insuranceValue).queryParam("coupon", coupon).queryParam("from_district_id", fromDistrictId)
                .queryParam("to_district_id", districtId).queryParam("to_ward_code", wardId).queryParam("weight", weightProduct);
        String urlMoneyParam = builderMoneyShip.toUriString();
        System.out.println(urlMoneyParam);
        ResponseEntity<String> responseMoneyShip = restTemplate.exchange(urlMoneyParam, HttpMethod.GET, entity, String.class);
        String responseBodyMoneyShip = responseMoneyShip.getBody();
        // Phân tích cú pháp JSON
        ObjectMapper objectMapperMoneyShip = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapperMoneyShip.readTree(responseBodyMoneyShip);
            JsonNode dataNodeMoneyShip = jsonNode.get("data");
            System.out.println(dataNodeMoneyShip.get("total").asText());
            moneyShip = Integer.parseInt(dataNodeMoneyShip.get("total").asText());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        BigDecimal voucherMoney = bill.getVoucherMoney();
        Optional<VoucherDetails> voucherDetailsOpt = voucherDetailsRepo.findByIdBill(bill.getId());
            if (voucherDetailsOpt.isPresent()) {
                VoucherDetails voucherDetails = voucherDetailsOpt.get();
                Voucher voucher = voucherDetails.getVoucher();
                if (DiscountType.GIAM_TRUC_TIEP.getLabel().equalsIgnoreCase(voucher.getDiscountType())) {
                    // compareTo returns âm thì big 1 < big 2
                    // compareTo returns bằng 0 thì big -1 = big 2
                    // compareTo returns dương thì big -1 > big 2
                    if (new BigDecimal(insuranceValue).compareTo(new BigDecimal(Float.toString(voucher.getValue()))) < 0){
                        voucherMoney = new BigDecimal(insuranceValue);
                    }else {
                        voucherMoney = new BigDecimal(Float.toString(voucher.getValue()));
                    }

                } else {
                    voucherMoney = BigDecimal.valueOf(insuranceValue).multiply(new BigDecimal(Float.toString(voucher.getValue()))).divide(new BigDecimal("100"));
                }
                // compareTo returns âm thì big 1 < big 2
                // compareTo returns bằng 0 thì big 1 = big 2
                // compareTo returns dương thì big 1 > big 2
                if (voucherMoney.compareTo(voucher.getMaxDiscountValue()) > 0) {
                    voucherMoney = voucher.getMaxDiscountValue();
                }
                if ((new BigDecimal(insuranceValue).compareTo(voucher.getOrderMinValue())<0)){
                    voucherMoney = BigDecimal.ZERO;
                }
            }

        bill.setShipMoney(BigDecimal.valueOf(moneyShip));
        bill.setVoucherMoney(voucherMoney);
        BigDecimal intoMoney = BigDecimal.valueOf(insuranceValue)
                .subtract(voucherMoney)
                .add(BigDecimal.valueOf(moneyShip));
        bill.setIntoMoney(intoMoney);
        bill.setTotalMoney(BigDecimal.valueOf(insuranceValue));
        // compareTo returns âm thì big 1 < big 2
        // compareTo returns bằng 0 thì big 1 = big 2
        // compareTo returns dương thì big 1 > big 2
        if(intoMoney.compareTo(BigDecimal.ZERO) < 0){
            bill.setIntoMoney(BigDecimal.ZERO);
        }

        update(bill);
        return true;
    }

    @Override
    public ResponseEntity<ResponseObject> billAddProductNew(List<BillDetailsRequest> lstBillDetailsDto, Long id) {
        Optional<Bill> billOpt = billRepo.findById(id);
        if (billOpt.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Id Hóa Đơn", 1, id), HttpStatus.BAD_REQUEST);
        }
        if (lstBillDetailsDto.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Chọn ít nhất 1 sản phẩm", 1, null), HttpStatus.BAD_REQUEST);
        }

        for (BillDetailsRequest billDetailsDto : lstBillDetailsDto) {
            Optional<ProductDetails> productDetailOpt = productDetailsRepo.findByProductDetailWhereDeletedAndStatus(billDetailsDto.getProductDetails(), TrangThai.HOAT_DONG.getLabel());
            if (productDetailOpt.isEmpty()) {
                return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Sản Phẩm", 1, null), HttpStatus.BAD_REQUEST);
            }

            Bill bill = billOpt.get();
            ProductDetails productDetails = productDetailOpt.get();

            Optional<BillDetails> billDetailsOpt = billDetailsRepo.findByBillAndProductDetails(bill, productDetails);

            if (productDetails.getQuantity() == 0) {
                return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Sản Phẩm Là 0", 1, null), HttpStatus.BAD_REQUEST);
            }
            if (productDetails.getQuantity() < billDetailsDto.getQuantity()) {
                return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Sản Phẩm Không Đủ", 1, null), HttpStatus.BAD_REQUEST);
            }

            if (billDetailsOpt.isPresent()) {
                BillDetails billDetails = billDetailsOpt.get();
                if (productDetails.getQuantity() < (billDetailsDto.getQuantity()+billDetails.getQuantity())) {
                    return new ResponseEntity<>(new ResponseObject("error", "Bạn đang có "+billDetails.getQuantity()+" sản phẩm. Lên vượt mức số lượng trong kho ", 1, null), HttpStatus.BAD_REQUEST);
                }

                // Cần xác định giá của sản phẩm từ đâu
                if (productDetails.getPromotionDetailsActive() != null) {
                    BigDecimal price = productDetails.getPrice().subtract(
                            productDetails.getPrice()
                                    .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                            .divide(BigDecimal.valueOf(100))));
                    billDetails.setUnitPrice(price);
                    billDetails.setDiscount(productDetails.getPrice()
                            .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                    .divide(BigDecimal.valueOf(100))));
                    billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
                } else {
                    billDetails.setUnitPrice(productDetails.getPrice());
                    billDetails.setDiscount(BigDecimal.ZERO);
                    billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
                }
                billDetails.setOriginalPrice(productDetails.getPrice());
                billDetails.setQuantity(billDetails.getQuantity() + billDetailsDto.getQuantity());

                billDetailsService.update(billDetails);
                if (!bill.getStatus().equalsIgnoreCase(TrangThaiBill.CHO_XAC_NHAN.getLabel())){
                    productDetails.setQuantity(productDetails.getQuantity() - billDetailsDto.getQuantity());
                    productDetailsRepo.save(productDetails);
                }
            } else {
                BillDetails billDetails = mapper.map(billDetailsDto, BillDetails.class);

                // Cần xác định giá của sản phẩm từ đâu
                if (productDetails.getPromotionDetailsActive() != null) {
                    BigDecimal price = productDetails.getPrice().subtract(
                            productDetails.getPrice()
                                    .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                            .divide(BigDecimal.valueOf(100))));
                    billDetails.setUnitPrice(price);

                    billDetails.setDiscount(productDetails.getPrice()
                            .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                    .divide(BigDecimal.valueOf(100))));
                    billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
                } else {
                    billDetails.setUnitPrice(productDetails.getPrice());
                    billDetails.setDiscount(BigDecimal.ZERO);

                    billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
                }

                billDetails.setId(null);
                billDetails.setBill(bill);
                billDetails.setOriginalPrice(productDetails.getPrice());
                billDetails.setProductDetails(productDetails);
                billDetails.setStatus(TrangThaiBill.DANG_BAN.getLabel());
                billDetailsService.createNew(billDetails);

                if (!bill.getStatus().equalsIgnoreCase(TrangThaiBill.CHO_XAC_NHAN.getLabel())){
                    productDetails.setQuantity(productDetails.getQuantity() - billDetailsDto.getQuantity());
                    productDetailsRepo.save(productDetails);
                }
            }
        }
        updateChangeMoneyBill(id);

        return new ResponseEntity<>(new ResponseObject("success", "Thêm Sản Phẩm Thành Công", 0, lstBillDetailsDto), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ResponseObject> chaneQuantityBillToBillDetails(BillDetailsRequest billDetailsRequest, Long idBill, Long idBillDetail) {
        Optional<BillDetails> opt = billDetailsRepo.findById(idBillDetail);
        if (opt.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy IdBilDetails Hóa Đơn", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }

        Optional<Bill> optBill = billRepo.findById(idBill);
        if (optBill.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Hóa Đơn", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }

        System.out.println("idBillDetail" + idBillDetail);
        Optional<ProductDetails> optProductDetails = productDetailsService.findByProductDetailWhereDeletedAndStatus(opt.get().getProductDetails().getId(),TrangThai.HOAT_DONG.getLabel());
        if (optProductDetails.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không tìm thấy mặt hàng này. Có thể đã bị xóa hoặc tạm ngưng bán", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }


        if (billDetailsRequest.getQuantity() == null) {
            return new ResponseEntity<>(new ResponseObject("error", "Vui lòng nhập số lượng", 1, null), HttpStatus.BAD_REQUEST);
        }
        if (billDetailsRequest.getQuantity() < 1) {
            return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Phải Lớn Hơn 0", 1, null), HttpStatus.BAD_REQUEST);
        }

        BillDetails billDetails = opt.get();
        ProductDetails productDetails = optProductDetails.get();

//        productDetails.setQuantity(productDetails.getQuantity() + opt.get().getQuantity());

        if ((productDetails.getQuantity() + opt.get().getQuantity()) < billDetailsRequest.getQuantity()) {
            return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Sản Phẩm Không Đủ", 1, null), HttpStatus.BAD_REQUEST);
        }


        billDetails.setQuantity(billDetailsRequest.getQuantity());

        // Cần xác định giá của sản phẩm từ đâu
        if (productDetails.getPromotionDetailsActive() != null) {
            BigDecimal price = productDetails.getPrice().subtract(
                    productDetails.getPrice()
                            .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                                    .divide(BigDecimal.valueOf(100))));
            billDetails.setUnitPrice(price);
            billDetails.setDiscount(productDetails.getPrice()
                    .multiply(BigDecimal.valueOf(productDetails.getPromotionDetailsActive().getPromotion().getValue())
                            .divide(BigDecimal.valueOf(100))));
            billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
        } else {
            billDetails.setUnitPrice(productDetails.getPrice());
            billDetails.setDiscount(BigDecimal.ZERO);
            billDetails.setPromotionDetailsActive(productDetails.getPromotionDetailsActive());
        }

//        productDetails.setQuantity(productDetails.getQuantity() - billDetailsRequest.getQuantity());
//        productDetailsRepo.save(productDetails);

        billDetailsRepo.save(billDetails);
        updateChangeMoneyBill(idBill);
        return new ResponseEntity<>(new ResponseObject("success", "Cập Nhật Số Lượng Thành Công", 0, billDetailsRequest), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ResponseObject> deleteBillToBillDetail(Long idBill, Long idBillDetail) {
        Optional<BillDetails> opt = billDetailsRepo.findById(idBillDetail);
        if (opt.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy BilDetails Hóa Đơn", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }
        Optional<Bill> optBill = billRepo.findById(idBill);
        if (optBill.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Hóa Đơn", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }
        BillDetails billDetails = opt.get();
        Optional<ProductDetails> productDetailsOtp = productDetailsRepo.findById(billDetails.getProductDetails().getId());

        if (productDetailsOtp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Sản Phẩm Trong Giỏ", 0, idBillDetail), HttpStatus.BAD_REQUEST);
        }

        ProductDetails productDetails = productDetailsOtp.get();
        if (!optBill.get().getStatus().equalsIgnoreCase(TrangThaiBill.CHO_XAC_NHAN.getLabel())){
            productDetails.setQuantity(productDetails.getQuantity() + billDetails.getQuantity());
            productDetailsRepo.save(productDetails);
        }

        billDetailsRepo.delete(billDetails);
        updateChangeMoneyBill(idBill);
        return new ResponseEntity<>(new ResponseObject("success", "Xóa sản phẩm thành công", 0, idBillDetail), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<ResponseObject> addHistorybill(HistoryBillRequest historyBillRequest, Long idBill) {
        Optional<Bill> optionalBill = billRepo.findById(idBill);
        if (optionalBill.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Hóa Đơn", 1, idBill), HttpStatus.BAD_REQUEST);
        }
        Bill bill = optionalBill.get();
        //phuong thuc thanh toan tin Tien mat
        if (bill.getStatus().equalsIgnoreCase(TrangThaiBill.CHO_XAC_NHAN.getLabel())
                && bill.getPaymentMethod().equalsIgnoreCase("0")
                && historyBillRequest.getType().equalsIgnoreCase(TrangThaiBill.DA_XAC_NHAN.getLabel())
        ){
            Set<BillDetails> lstBillDetails = bill.getLstBillDetails();
            for (BillDetails billDetails : lstBillDetails) {
                ProductDetails pd = billDetails.getProductDetails();
                if (pd.getQuantity() < billDetails.getQuantity()){
                    return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Sản Phẩm "+pd.getProduct().getName()+" Không Đủ", 1, billDetails), HttpStatus.BAD_REQUEST);
                }

                if (pd.getQuantity() <= 0){
                    return new ResponseEntity<>(new ResponseObject("error", "Số Lượng Sản Phẩm "+pd.getProduct().getName()+" Không Đang Nhỏ Hơn 0", 1, billDetails), HttpStatus.BAD_REQUEST);
                }
                pd.setQuantity(pd.getQuantity() - billDetails.getQuantity());
                productDetailsRepo.save(pd);
            }
        }


        if (bill.getStatus().equalsIgnoreCase(TrangThaiBill.DA_XAC_NHAN.getLabel())
                && bill.getPaymentMethod().equalsIgnoreCase("0")
                && historyBillRequest.getType().equalsIgnoreCase(TrangThaiBill.CHO_XAC_NHAN.getLabel())
        ){
            Set<BillDetails> lstBillDetails = bill.getLstBillDetails();
            for (BillDetails billDetails : lstBillDetails) {
                ProductDetails pd = billDetails.getProductDetails();
                pd.setQuantity(pd.getQuantity() + billDetails.getQuantity());
                productDetailsRepo.save(pd);
            }
        }

        HistoryBill historyBill = mapper.map(historyBillRequest, HistoryBill.class);
        historyBill.setBill(bill);
        for (TrangThaiBill trangThai : TrangThaiBill.values()) {
            if (trangThai.name().equals(historyBillRequest.getType()) || trangThai.getLabel().equals(historyBillRequest.getType())) {
                historyBill.setType(trangThai.getLabel());
                bill.setStatus(trangThai.getLabel());
                break;
            }
        }
        update(bill);
        historyBillService.createNew(historyBill);
        return new ResponseEntity<>(new ResponseObject("seccess", "Thay Đổi Trạng Thái Thành Công", 0, historyBillRequest), HttpStatus.OK);

    }

    @Override
    public ResponseEntity<ResponseObject> billPaymentHistory(PaymentHistoryRequest paymentHistoryRequest, Long idBill) {
        Optional<Bill> optionalBill = billRepo.findById(idBill);
        if (optionalBill.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Hóa Đơn", 1, idBill), HttpStatus.BAD_REQUEST);
        }

        PaymentHistory paymentHistory = mapper.map(paymentHistoryRequest, PaymentHistory.class);
        paymentHistory.setBill(optionalBill.get());
        paymentHistory.setPaymentDate(LocalDateTime.now());
        HistoryBill historyBill = new HistoryBill();
        historyBill.setBill(optionalBill.get());
        if (paymentHistory.getType().equalsIgnoreCase("1")){
            historyBill.setDescription("Đã Hoàn " + paymentHistory.getPaymentAmount()+ " VNĐ");
            historyBill.setType(TrangThaiBill.HOAN_TIEN.getLabel());
        }else {
            historyBill.setDescription("Đã Thanh Toán " + paymentHistory.getPaymentAmount()+ " VNĐ");
            historyBill.setType(TrangThaiBill.DA_THANH_TOAN.getLabel());
        }
        historyBillService.createNew(historyBill);
        paymentHistoryService.createNew(paymentHistory);
        return new ResponseEntity<>(new ResponseObject("seccess", "Thanh Toán Thành Công", 0, paymentHistoryRequest), HttpStatus.OK);

    }

    @Override
    public ResponseEntity<ResponseObject> cancellingBill(Long idBill,HistoryBillRequest historyBillRequest) {
        Optional<Bill> optionalBill = billRepo.findById(idBill);
        if (optionalBill.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy Hóa Đơn", 1, idBill), HttpStatus.BAD_REQUEST);
        }

        Bill bill = optionalBill.get();
        List<BillDetails> lstBillDetails = billDetailsService.findAllByBill(bill);

        lstBillDetails.stream().map(entity -> {
            ProductDetails productDetails = entity.getProductDetails();
            if (!bill.getStatus().equalsIgnoreCase(TrangThaiBill.CHO_XAC_NHAN.getLabel())){
                productDetails.setQuantity(productDetails.getQuantity() + entity.getQuantity());
                productDetailsRepo.save(productDetails);
            }
            return entity;
        }).collect(Collectors.toList());

        bill.setStatus(TrangThaiBill.HUY.getLabel());
        update(bill);
        historyBillService.addHistoryBill(bill, TrangThaiBill.HUY.getLabel(), historyBillRequest.getDescription());

        return new ResponseEntity<>(new ResponseObject("sucsess", "Hủy Hóa Đơn Thành Công", 0, idBill), HttpStatus.OK);

    }

    @Override
    public Optional<Bill> findBillByCode(String codeBill) {
        return billRepo.findBillByCode(codeBill.trim());
    }

    @Override
    public void autoUpdateBillChoThanhToanToHuy(LocalDateTime now, String choThanhToan, String daHuy) {
        List<Bill> lstBill = billRepo.findAllBillChoThanhToan(now, choThanhToan);
        for (Bill bill : lstBill) {
            bill.setStatus(TrangThaiBill.HUY.getLabel());
            List<BillDetails> lstBillDetail = billDetailsRepo.findAllByBill(bill);
            for (BillDetails billDetails : lstBillDetail) {
                ProductDetails productDetails = billDetails.getProductDetails();
                productDetails.setQuantity(productDetails.getQuantity() + billDetails.getQuantity());
                productDetailsRepo.save(productDetails);
            }
            update(bill);
            historyBillService.addHistoryBill(bill, TrangThaiBill.HUY.getLabel(), "Tự Động Hủy Bởi Hệ Thống");
        }

    }
}
