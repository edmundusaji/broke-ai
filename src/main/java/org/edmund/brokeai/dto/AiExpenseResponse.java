package org.edmund.brokeai.dto;

import lombok.Data;

@Data
public class AiExpenseResponse {
    private String tanggal; // date
    private Double total;
    private String kategori;
    private String merchant;
    private String waktu; // time
}