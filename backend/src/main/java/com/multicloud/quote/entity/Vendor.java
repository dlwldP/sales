package com.multicloud.quote.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vendor", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorType name;

    public Vendor(VendorType name) {
        this.name = name;
    }
}
