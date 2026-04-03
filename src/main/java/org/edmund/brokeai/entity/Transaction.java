package org.edmund.brokeai.entity;

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

    private LocalDateTime tanggal;

    private Double jumlah;

    private String kategori;

    private String merchant;

    private String tipeInput; // RECEIPT, NOTIFICATION

    private String statusValidasi; // PENDING, CONFIRMED
}
