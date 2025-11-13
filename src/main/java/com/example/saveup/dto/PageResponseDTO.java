package com.example.saveup.dto;

import lombok.Data;
import java.util.List;

@Data
public class PageResponseDTO<T> {
    private List<T> content;
    private int currentPage;
    private long totalItems;
    private int totalPages;
}