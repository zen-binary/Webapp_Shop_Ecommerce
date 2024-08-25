package com.example.webapp_shop_ecommerce.service.Impl;


import com.example.webapp_shop_ecommerce.entity.BaseEntity;
import com.example.webapp_shop_ecommerce.infrastructure.security.Authentication;
import com.example.webapp_shop_ecommerce.repositories.IBaseReporitory;
import com.example.webapp_shop_ecommerce.dto.response.ResponseObject;
import com.example.webapp_shop_ecommerce.service.IBaseService;
import com.example.webapp_shop_ecommerce.ultiltes.EntitySpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Optional;
public class BaseServiceImpl<E extends BaseEntity, ID extends Serializable, R extends IBaseReporitory<E ,ID>>
        implements IBaseService<E, ID> {
    protected R repository;
    @Autowired
    private Authentication authentication;
    @Autowired
    public void setRepository(R repository) {
        this.repository = repository;
    }
    @Override
    public ResponseEntity<ResponseObject> createNew(E entity) {

        entity.setId(null);
        entity.setDeleted(false);
        entity.setCreatedBy(authentication.getUsers().getFullName());
        entity.setCreatedDate(LocalDateTime.now());
        entity.setLastModifiedDate(LocalDateTime.now());
        entity.setLastModifiedBy(authentication.getUsers().getFullName());
        repository.save(entity);
        return new ResponseEntity<>(new ResponseObject("success", "Thêm Mới Thành Công", 0, entity), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ResponseObject> update(E entity) {
        if (entity != null) {
            entity.setLastModifiedDate(LocalDateTime.now());
            entity.setLastModifiedBy(authentication.getUsers().getFullName());
            entity.setDeleted(false);
            repository.save(entity);
            return new ResponseEntity<>(new ResponseObject("success", "Cập Nhật Thành Công", 0, entity), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(new ResponseObject("error", "Đối tượng không hợp lệ", 1, null), HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ResponseEntity<ResponseObject> physicalDelete(ID id) {
        Optional<E> otp = findById(id);
        if (otp.isEmpty()) {
            return new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy ID",1,null), HttpStatus.BAD_REQUEST);
        }

        repository.deleteById(id);
        return new ResponseEntity<>(new ResponseObject("success", "Đã Xóa Vĩnh Viên Thành Công", 0, otp.get()), HttpStatus.OK);

    }

    @Override
    public ResponseEntity<ResponseObject> delete(ID id) {
        return findById(id)
                .map(entity -> {
                    entity.setLastModifiedDate(LocalDateTime.now());
                    entity.setDeleted(true);
                    repository.save(entity);
                    return new ResponseEntity<>(new ResponseObject("success", "Đã Xóa Thành Công", 0, entity), HttpStatus.OK);
                })
                .orElseGet(() -> new ResponseEntity<>(new ResponseObject("error", "Không Tìm Thấy ID", 1, null), HttpStatus.BAD_REQUEST));
    }

    @Override
    public ResponseEntity<ResponseObject> delete(E entity) {
        if (entity != null) {
            entity.setDeleted(true);
            repository.save(entity);
            return new ResponseEntity<>(new ResponseObject("success", "Đã Xóa Thành Công", 0, entity), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(new ResponseObject("error", "Đối tượng không hợp lệ", 1, null), HttpStatus.BAD_REQUEST);
        }

    }

    @Override
    public Optional<E> findById(ID id) {
        return repository.findByIdAndDeletedFalses(id);
    }

    @Override
    public Boolean existsById(ID id) {
        return existsById(id);
    }


    @Override
    public Page<E> findAll(Specification<E> spec, Pageable page) {
        return repository.findAll(spec, page);
    }

    @Override
    public Page<E> findAllDeletedFalse(Pageable page) {
        Specification<E> spec = EntitySpecifications.isNotDeletedAndSortByCreatedDate();

        return repository.findAll(spec, page);
    }
}
