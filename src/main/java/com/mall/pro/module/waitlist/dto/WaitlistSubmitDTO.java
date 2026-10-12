package com.mall.pro.module.waitlist.dto;

import lombok.Data;

@Data
public class WaitlistSubmitDTO {
    private Long programId;
    private Long ticketCategoryId;
    private Long attendeeId;

}
