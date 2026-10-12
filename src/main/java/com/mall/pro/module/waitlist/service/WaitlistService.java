package com.mall.pro.module.waitlist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.pro.common.BusinessException;
import com.mall.pro.common.util.UserContext;
import com.mall.pro.module.order.entity.TicketOrder;
import com.mall.pro.module.order.enums.OrderStatus;
import com.mall.pro.module.order.mapper.TicketOrderMapper;
import com.mall.pro.module.pay.event.OrderPaidEvent;
import com.mall.pro.module.ticket.entity.TicketCategory;
import com.mall.pro.module.ticket.service.TicketCategoryService;
import com.mall.pro.module.user.entity.UserAttendee;
import com.mall.pro.module.user.mapper.UserAttendeeMapper;
import com.mall.pro.module.waitlist.dto.WaitlistResponseDTO;
import com.mall.pro.module.waitlist.dto.WaitlistSubmitDTO;
import com.mall.pro.module.waitlist.entity.WaitlistOrder;
import com.mall.pro.module.waitlist.mapper.WaitlistOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor

public class WaitlistService {
    private final WaitlistOrderMapper waitlistOrderMapper;
    private final UserAttendeeMapper userAttendeeMapper;
    private final TicketCategoryService ticketCategoryService;
    private final TicketOrderMapper ticketOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 1. 提交候补排队申请
     */
    @Transactional(rollbackFor = Exception.class)
    public WaitlistResponseDTO  submitWaitlist(WaitlistSubmitDTO dto){
        Long currentUserId = UserContext.getUserId();
        if(currentUserId == null){
            throw new BusinessException(401,"请先登录！");
        }

        // 防线 1: 校验常用观演人归属 (防越权 IDOR)
        UserAttendee attendee = userAttendeeMapper.selectById(dto.
                getAttendeeId());
        if (attendee == null || !attendee.getUserId().
                equals(currentUserId)) {
            throw new BusinessException(403,
                    "观演人不存在或无权使用该观演人！");
        }

        //防线 2：校验库存（必须已售罄才允许后部，未售罄引导秒杀）
        String stockStr = stringRedisTemplate.opsForValue().get("ticket:stock:"+dto.getTicketCategoryId());
        if(stockStr != null && Integer.parseInt(stockStr) > 0){
            throw new BusinessException(400, "当前票档仍有余票，请直接秒杀购买，无需候补！");
        }

        //防线 3: 防重复排队
        Long existingCount = waitlistOrderMapper.selectCount(new LambdaQueryWrapper<WaitlistOrder>()
                .eq(WaitlistOrder::getProgramId,dto.getProgramId())
                .eq(WaitlistOrder::getTicketCategoryId,dto.getTicketCategoryId())
                .eq(WaitlistOrder::getIdCardHash,attendee.getIdCardHash())
                .eq(WaitlistOrder::getStatus,0));
        if(existingCount > 0){
            throw new BusinessException(400, "该观演人已在候补排队中，请勿重复提交！");
        }
        // 插入候补订单
        WaitlistOrder waitlistOrder = WaitlistOrder.builder()
                .userId(currentUserId)
                .programId(dto.getProgramId())
                .ticketCategoryId(dto.getTicketCategoryId())
                .attendeeId(attendee.getId())
                .realName(attendee.getRealName())
                .idCardMasked(attendee.getIdCardMasked())
                .idCardHash(attendee.getIdCardHash())
                .status(0)
                .createTime(LocalDateTime.now())
                .build();

        waitlistOrderMapper.insert(waitlistOrder);

        //计算当前顺位号（同场次同票档排在自己前面的数量 + 1）
        Long position = waitlistOrderMapper.selectCount(new LambdaQueryWrapper<WaitlistOrder>()
                .eq(WaitlistOrder::getProgramId, dto.getProgramId())
                .eq(WaitlistOrder::getTicketCategoryId, dto.getTicketCategoryId())
                .eq(WaitlistOrder::getStatus, 0)
                .le(WaitlistOrder::getCreateTime, waitlistOrder.getCreateTime()));
        log.info("[候补排队成功] userId={}, attendee={}, 当前顺位号={}", currentUserId, attendee.getRealName(), position);
        return  WaitlistResponseDTO.builder()
                .waitlistId(waitlistOrder.getId())
                .programId(waitlistOrder.getProgramId())
                .ticketCategoryId(waitlistOrder.getTicketCategoryId())
                .realName(waitlistOrder.getRealName())
                .idCardMasked(waitlistOrder.getIdCardMasked())
                .status(0)
                .queuePosition(position)
                .createTime(waitlistOrder.getCreateTime())
                .build();

    }
    /**
     * 2. 核心撮合引擎：当有退票或关单释放库存时被调用
     * @param ticketCategoryId 票档 ID
     * @return true: 候补撮合成功并截胡出票（禁止回补 Redis 公海）；false: 无人候补（可回补公海）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean triggerMatching(Long ticketCategoryId) {
        // 1. 查询排在最前面的 1 条 WAITING 候补单 (FIFO 顺序)
        WaitlistOrder candidate = waitlistOrderMapper.selectOne(new LambdaQueryWrapper<WaitlistOrder>()
                .eq(WaitlistOrder::getTicketCategoryId, ticketCategoryId)
                .eq(WaitlistOrder::getStatus, 0)
                .orderByAsc(WaitlistOrder::getCreateTime)
                .last("LIMIT 1"));

        if (candidate == null) {
            log.info("[候补撮合引擎] 票档 categoryId={} 无等待中的候补单，允许库存回流公海", ticketCategoryId);
            return false;
        }

        // 2. 推进候补单状态 -> 1 (撮合成功已出票)
        candidate.setStatus(1);
        candidate.setMatchTime(LocalDateTime.now());
        waitlistOrderMapper.updateById(candidate);

        // 3. 自动生成正式已支付订单并出票 (自动履约)
        TicketCategory category = ticketCategoryService.getById(ticketCategoryId);
        TicketOrder formalOrder = TicketOrder.builder()
                .userId(candidate.getUserId())
                .programId(candidate.getProgramId())
                .ticketCategoryId(ticketCategoryId)
                .ticketName(category.getName())
                .orderPrice(category.getPrice())
                .status(OrderStatus.PAID.getCode()) // 候补撮合成功直接标记为已支付
                .version(1)
                .createTime(LocalDateTime.now())
                .attendeeId(candidate.getAttendeeId())
                .realName(candidate.getRealName())
                .idCardMasked(candidate.getIdCardMasked())
                .idCardHash(candidate.getIdCardHash())
                .build();
        ticketOrderMapper.insert(formalOrder);

        // 4. 发送异步出票消息给 Kafka 消费者 (TicketIssueConsumer) 履约出票
        try {
            OrderPaidEvent event = OrderPaidEvent.builder()
                    .orderId(formalOrder.getId())
                    .paySn("WAITLIST_" + formalOrder.getId())
                    .tradeNo("WL_TRADE_" + System.currentTimeMillis())
                    .userId(formalOrder.getUserId())
                    .amount(formalOrder.getOrderPrice())
                    .payTime(LocalDateTime.now())
                    .build();
            kafkaTemplate.send("order-paid-topic", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("[候补履约发消息异常] orderId={}", formalOrder.getId(), e);
        }

        log.info("[候补撮合截胡成功] waitlistId={}, 转化为正式订单 orderId={}, 成功阻断公海回补",
                candidate.getId(), formalOrder.getId());
        return true;
    }

    /**
     * 3. 查询当前登录用户的所有候补单（动态计算排位顺位）
     */
    public List<WaitlistResponseDTO> getMyWaitlist() {
        Long currentUserId = UserContext.getUserId();
        List<WaitlistOrder> list = waitlistOrderMapper.selectList(new LambdaQueryWrapper<WaitlistOrder>()
                .eq(WaitlistOrder::getUserId, currentUserId)
                .orderByDesc(WaitlistOrder::getCreateTime));

        return list.stream().map(order -> {
            Long position = null;
            if (order.getStatus() == 0) {
                position = waitlistOrderMapper.selectCount(new LambdaQueryWrapper<WaitlistOrder>()
                        .eq(WaitlistOrder::getProgramId, order.getProgramId())
                        .eq(WaitlistOrder::getTicketCategoryId, order.getTicketCategoryId())
                        .eq(WaitlistOrder::getStatus, 0)
                        .le(WaitlistOrder::getCreateTime, order.getCreateTime()));
            }
            return WaitlistResponseDTO.builder()
                    .waitlistId(order.getId())
                    .programId(order.getProgramId())
                    .ticketCategoryId(order.getTicketCategoryId())
                    .realName(order.getRealName())
                    .idCardMasked(order.getIdCardMasked())
                    .status(order.getStatus())
                    .queuePosition(position)
                    .createTime(order.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 4. 用户主动取消候补
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelWaitlist(Long waitlistId) {
        Long currentUserId = UserContext.getUserId();
        WaitlistOrder order = waitlistOrderMapper.selectById(waitlistId);
        if (order == null || !order.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "候补单不存在或无权操作！");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(400, "仅排队中的候补单支持取消！");
        }
        order.setStatus(2); // 2: 已取消
        waitlistOrderMapper.updateById(order);
        log.info("[取消候补成功] waitlistId={}, userId={}", waitlistId, currentUserId);
    }

}


