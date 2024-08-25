package com.example.webapp_shop_ecommerce.controller;

import com.example.webapp_shop_ecommerce.dto.request.categories.CategoryRequest;
import com.example.webapp_shop_ecommerce.dto.response.ResponseObject;
import com.example.webapp_shop_ecommerce.dto.response.categories.CategoryResponse;
import com.example.webapp_shop_ecommerce.dto.response.customer.CustomerResponse;
import com.example.webapp_shop_ecommerce.entity.Category;
import com.example.webapp_shop_ecommerce.entity.Customer;
import com.example.webapp_shop_ecommerce.service.ICategoryService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/category")
public class CategoryController {

    @Autowired
    private ModelMapper mapper;

    @Autowired
    private ICategoryService categoryService;

    @GetMapping
    public ResponseEntity<?> findCategoryAll(
            @RequestParam(value = "page", defaultValue = "-1") Integer page,
            @RequestParam(value = "size", defaultValue = "-1") Integer size) {
        Pageable pageable = Pageable.unpaged();
        if (size < 0) {
            size = 5;
        }
        if (page >= 0) {
            pageable = PageRequest.of(page, size);
        }
        System.out.println("page=" + page + " size=" + size);
        List<Category> lstPro = categoryService.findAllDeletedFalse(pageable).getContent();
        List<CategoryResponse> resultDto = lstPro.stream().map(pro -> mapper.map(pro, CategoryResponse.class)).collect(Collectors.toList());
        return new ResponseEntity<>(resultDto, HttpStatus.OK);
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> findObjById(@PathVariable("id") Long id) {
        Optional<Category> otp = categoryService.findById(id);
        if (otp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không tìm thấy id " + id, 1, null), HttpStatus.BAD_REQUEST);
        }
        CategoryResponse category = otp.map(pro -> mapper.map(pro, CategoryResponse.class)).orElseThrow(IllegalArgumentException::new);
        return new ResponseEntity<>(category, HttpStatus.OK);
    }
    @PostMapping()
    public ResponseEntity<ResponseObject> saveCategory(@Valid @RequestBody CategoryRequest categoryRequest, BindingResult result) {
        if (result.hasErrors()) {
            // Xử lý lỗi validate ở đây
            StringBuilder errors = new StringBuilder();
            for (FieldError error : result.getFieldErrors()) {
                errors.append(error.getDefaultMessage()).append("\n");
            }
            // Xử lý lỗi validate ở đây, ví dụ: trả về ResponseEntity.badRequest()
            return new ResponseEntity<>(new ResponseObject("error", errors.toString(), 1, categoryRequest), HttpStatus.BAD_REQUEST);
        }

        Optional<Category> otp = categoryService.findByName(categoryRequest.getName().trim());
        if (otp.isPresent()) {
            return new ResponseEntity<>(new ResponseObject("error", "Tên sản phẩm đã tồn tại", 1, categoryRequest), HttpStatus.BAD_REQUEST);
        }
        Category category =mapper.map(categoryRequest, Category.class);
        return categoryService.createNew(category);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseObject> deleteCategory(@PathVariable("id") Long id) {
        System.out.println("Delete ID: " + id);
        return categoryService.delete(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseObject> updateCategory(@Valid @RequestBody CategoryRequest categoryRequest, BindingResult result, @PathVariable("id") Long id ) {

        if (result.hasErrors()) {
            // Xử lý lỗi validate ở đây
            StringBuilder errors = new StringBuilder();
            for (FieldError error : result.getFieldErrors()) {
                errors.append(error.getDefaultMessage()).append("\n");
            }
            // Xử lý lỗi validate ở đây, ví dụ: trả về ResponseEntity.badRequest()
            return new ResponseEntity<>(new ResponseObject("error", errors.toString(), 1, categoryRequest), HttpStatus.BAD_REQUEST);
        }
        System.out.println("Update ID: " + id);

        Optional<Category> otp = categoryService.findById(id);
        if (otp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Thấy ID", 1, categoryRequest), HttpStatus.BAD_REQUEST);
        }

        if (categoryService.findByName(categoryRequest.getName().trim()).isPresent()) {
            return new ResponseEntity<>(new ResponseObject("error", "Tên loại đã tồn tại", 1, categoryRequest), HttpStatus.BAD_REQUEST);
        }
        Category category = otp.get();
        category.setName(categoryRequest.getName());
        return categoryService.update(category);


    }
}
