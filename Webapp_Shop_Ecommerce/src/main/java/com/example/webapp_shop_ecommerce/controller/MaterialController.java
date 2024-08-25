package com.example.webapp_shop_ecommerce.controller;

import com.example.webapp_shop_ecommerce.dto.request.material.MaterialRequest;
import com.example.webapp_shop_ecommerce.dto.response.ResponseObject;
import com.example.webapp_shop_ecommerce.dto.response.material.MaterialResponse;
import com.example.webapp_shop_ecommerce.entity.Brand;
import com.example.webapp_shop_ecommerce.entity.Material;
import com.example.webapp_shop_ecommerce.service.Impl.MaterialServiceImpl;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/material")
public class MaterialController {
    @Autowired
    private ModelMapper mapper;
    @Autowired
    private MaterialServiceImpl materialService;



    @GetMapping
    public ResponseEntity<?> findAll(
            @RequestParam(value = "page", defaultValue = "-1") Integer page,
            @RequestParam(value = "size", defaultValue = "-1") Integer size) {
        Pageable pageable = Pageable.unpaged();
        if (size < 0) {
            size = 5;
        }
        if (page >= 0) {
            pageable = PageRequest.of(page, size);
        }
        List<Material> brand = materialService.findAllDeletedFalse(pageable).getContent();
        List<MaterialResponse> result = brand.stream().map(attr -> mapper.map(attr, MaterialResponse.class)).collect(Collectors.toList());
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> findObjById(@PathVariable("id") Long id) {
        Optional<Material> otp = materialService.findById(id);

        if (otp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("Fail", "Không tìm thấy id " + id, 1, null), HttpStatus.BAD_REQUEST);
        }
        MaterialResponse brand = otp.map(pro -> mapper.map(pro, MaterialResponse.class)).orElseThrow(IllegalArgumentException::new);
        return new ResponseEntity<>(brand, HttpStatus.OK);
    }
    @PostMapping()
    public ResponseEntity<ResponseObject> add(@Valid @RequestBody MaterialRequest MaterialRequest, BindingResult result){
        if (result.hasErrors()) {
            // Xử lý lỗi validate ở đây
            StringBuilder errors = new StringBuilder();
            for (FieldError error : result.getFieldErrors()) {
                errors.append(error.getDefaultMessage()).append("\n");
            }
            // Xử lý lỗi validate ở đây, ví dụ: trả về ResponseEntity.badRequest()
            return new ResponseEntity<>(new ResponseObject("error", errors.toString(), 1, MaterialRequest), HttpStatus.BAD_REQUEST);
        }
        Optional<Material> opt = materialService.findByName(MaterialRequest.getName().trim());
        if (opt.isPresent()){
            return new ResponseEntity<>(new ResponseObject("Fail", "Tên thuộc tính đã tồn tại", 1, MaterialRequest), HttpStatus.BAD_REQUEST);
        }
        Material material = mapper.map(MaterialRequest, Material.class);
        return materialService.createNew(material);

    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseObject> update(@Valid @RequestBody MaterialRequest MaterialRequest, BindingResult result, @PathVariable Long id){
        if (result.hasErrors()) {
            // Xử lý lỗi validate ở đây
            StringBuilder errors = new StringBuilder();
            for (FieldError error : result.getFieldErrors()) {
                errors.append(error.getDefaultMessage()).append("\n");
            }
            // Xử lý lỗi validate ở đây, ví dụ: trả về ResponseEntity.badRequest()
            return new ResponseEntity<>(new ResponseObject("error", errors.toString(), 1, MaterialRequest), HttpStatus.BAD_REQUEST);
        }

        Optional<Material> opt = materialService.findById(id);
        if (opt.isEmpty()){
            return new ResponseEntity<>(new ResponseObject("Fail", "Không Tìm Thấy ID", 1, MaterialRequest), HttpStatus.BAD_REQUEST);
        }

        if (materialService.findByName(MaterialRequest.getName().trim()).isPresent()){
            return new ResponseEntity<>(new ResponseObject("Fail", "Tên thuộc tính đã tồn tại", 1, MaterialRequest), HttpStatus.BAD_REQUEST);
        }

        Material material = opt.get();
        material.setName(MaterialRequest.getName());
        return materialService.update(material);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseObject> delete(@PathVariable Long id){
        return materialService.delete(id);
    }


}
