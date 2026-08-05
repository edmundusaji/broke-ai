package org.edmund.brokeai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "receipt")
@Data
public class Transaction {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_date")
    private LocalDateTime date;

    private Double amount;

    private String category;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "description")
    private String description;

    @Column(name = "input_type")
    private String inputType; // RECEIPT, NOTIFICATION

    @Column(name = "validation_status")
    private String validationStatus; // PENDING, CONFIRMED

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;
}
