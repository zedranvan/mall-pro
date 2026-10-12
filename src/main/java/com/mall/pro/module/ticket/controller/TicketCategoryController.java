package com.mall.pro.module.ticket.controller;

import com.mall.pro.common.Result;
import com.mall.pro.module.pay.service.TicketRefundService;
import com.mall.pro.module.ticket.dto.RefundResultDTO;
import com.mall.pro.module.ticket.entity.TicketCategory;
import com.mall.pro.module.ticket.entity.TicketItem;
import com.mall.pro.module.ticket.mapper.TicketCategoryMapper;
import com.mall.pro.module.ticket.service.OfflineGateEngine;
import com.mall.pro.module.ticket.service.TicketCategoryService;
import com.mall.pro.module.ticket.service.TicketItemService;
import com.mall.pro.module.ticket.service.TicketTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketCategoryController {


    private final TicketCategoryService ticketCategoryService;
    private final TicketItemService ticketItemService;
    private final TicketRefundService ticketRefundService;
    private final TicketTransferService ticketTransferService;
    private final OfflineGateEngine offlineGateEngine;

    @GetMapping("/category/{id}")
    public Result<TicketCategory> getCategoryById(@PathVariable Long id) {
        TicketCategory category = ticketCategoryService.getById(id);
        return Result.success(category);
    }

    @PostMapping("/category/{id}/deduct")
    public Result<String> deductStock(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer count
    ) {
        boolean success = ticketCategoryService.deductStock(id, count);
        if (success) {
            return Result.success("扣减成功");
        } else {
            return Result.error(400, "库存不足或已售罄");
        }
    }
    // 2. 新增查询我的票夹接口：
    @GetMapping("/my-tickets")
    public Result<List<TicketItem>> getMyTickets(@RequestParam("userId") Long userId) {
        return Result.success(ticketItemService.getMyTickets(userId));
    }

    // 3. 新增现场闸机核销接口：
    @PostMapping("/verify")
    public Result<String> verifyTicket(
            @RequestParam("verifyCode") String verifyCode,
            @RequestParam(value = "gateNo", defaultValue = "GATE_A_01") String gateNo,
            @RequestParam(value = "idCardNo", required = false) String idCardNo) { // 👈 增加刷卡身份证
        return ticketItemService.verifyTicket(verifyCode, gateNo, idCardNo);
    }

    // 4. 门票逆向退票与阶梯退款接口
    @PostMapping("/refund")
    public Result<RefundResultDTO> refundTicket(
            @RequestParam("ticketItemId") Long ticketItemId,
            @RequestParam("userId") Long userId) {
        return Result.success(ticketRefundService.refundTicket(ticketItemId, userId));
    }

    // 5. 电子票好友转赠接口
    @PostMapping("/transfer")
    public Result<TicketItem> transferTicket(
            @RequestParam("ticketItemId") Long ticketItemId,
            @RequestParam("fromUserId") Long fromUserId,
            @RequestParam("toUserId") Long toUserId,
            @RequestParam("toRealName") String toRealName,
            @RequestParam("toIdCard") String toIdCard) {
        return Result.success(ticketTransferService.transferTicket(ticketItemId, fromUserId, toUserId, toRealName, toIdCard));
    }
    // 6. 用户获取离线防伪数字凭据
    @GetMapping("/offline-cert/{ticketItemId}")
    public Result<String> getOfflineCert(
            @PathVariable("ticketItemId") Long ticketItemId,
            @RequestParam("userId") Long userId) {
        return ticketItemService.getOfflineCert(ticketItemId, userId);
    }

    // 7. 现场闸机边缘公钥离线核验接口 (模拟完全断网环境)
    @PostMapping("/verify-offline")
    public Result<String> verifyOffline(
            @RequestParam("offlineCert") String offlineCert,
            @RequestParam(value = "gateNo", defaultValue = "OFFLINE_GATE_01") String gateNo,
            @RequestParam(value = "idCardNo", required = false) String idCardNo) {
        return offlineGateEngine.verifyOffline(offlineCert, gateNo, idCardNo);

    }
}
