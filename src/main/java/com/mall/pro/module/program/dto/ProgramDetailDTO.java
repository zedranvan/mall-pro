package com.mall.pro.module.program.dto;

import com.mall.pro.module.ticket.entity.TicketCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class ProgramDetailDTO {

private Long id;
    private String title;
    private String actor;
    private String place;
    private LocalDateTime showTime;
    private List<TicketCategory> ticketCategories;
}
