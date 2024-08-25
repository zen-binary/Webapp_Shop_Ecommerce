package com.example.webapp_shop_ecommerce.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "HistoryBill")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class HistoryBill extends BaseEntity{
    @ManyToOne()
    @JoinColumn(name = "bill_id")
    private Bill bill;
    @Column(name = "type")
    private String type;
    @Column(name = "description")
    private String description;
}
