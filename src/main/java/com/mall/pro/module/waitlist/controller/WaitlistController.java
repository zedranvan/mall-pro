package com.mall.pro.module.waitlist.controller;

import com.mall.pro.common.Result;
import com.mall.pro.module.waitlist.dto.WaitlistResponseDTO;
import com.mall.pro.module.waitlist.dto.WaitlistSubmitDTO;
import com.mall.pro.module.waitlist.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping("/submit")
    public Result<WaitlistResponseDTO> submit(@RequestBody WaitlistSubmitDTO dto) {
        return Result.success(waitlistService.submitWaitlist(dto));
    }

    @GetMapping("/my")
    public Result<List<WaitlistResponseDTO>> getMyWaitlist() {
        return Result.success(waitlistService.getMyWaitlist());
    }

    @PostMapping("/cancel/{id}")
    public Result<String> cancel(@PathVariable("id") Long waitlistId) {
        waitlistService.cancelWaitlist(waitlistId);
        return Result.success("取消候补成功");
    }
}
