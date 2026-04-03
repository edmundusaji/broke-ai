package org.edmund.brokeai.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
public class Transaction {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime tanggal;

    private Double jumlah;

    private String kategori;

    private String merchant;

    @Column(name = "tipe_input")
    private String tipeInput; // RECEIPT, NOTIFICATION

    @Column(name = "status_validasi")
    private String statusValidasi; // PENDING, CONFIRMED
}
