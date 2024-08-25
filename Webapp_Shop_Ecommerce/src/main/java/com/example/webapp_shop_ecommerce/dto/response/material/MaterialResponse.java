package com.example.webapp_shop_ecommerce.dto.response.material;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter

public class MaterialResponse {
    private Long id;

    private String name;
    private LocalDateTime createdDate;
}
