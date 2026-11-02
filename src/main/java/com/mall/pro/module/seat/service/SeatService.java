package com.mall.pro.module.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.common.BusinessException;
import com.mall.pro.module.seat.dto.SeatDTO;
import com.mall.pro.module.seat.dto.SeatLockResponseDTO;
import com.mall.pro.module.seat.entity.Seat;
import com.mall.pro.module.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatMapper seatMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String BITMAP_KEY_PREFIX = "venue:bitmap:";
    private static final String LOCK_KEY_PREFIX = "venue:lock:";
    private static final long LOCK_TTL_SECONDS = 300; // 5分钟选座租约有效期

    /**
     * 1. 获取选座大厅全景（带实时锁态与 Redis Bitmap 缓存核验）
     */
    public List<SeatDTO> getSeatMap(Long programId, Long categoryId, Long currentUserId) {
        LambdaQueryWrapper<Seat> query = new LambdaQueryWrapper<Seat>()
                .eq(Seat::getProgramId, programId);
        if (categoryId != null) {
            query.eq(Seat::getCategoryId, categoryId);
        }
        query.orderByAsc(Seat::getRowNum, Seat::getColNum);

        List<Seat> seats = seatMapper.selectList(query);
        String bitmapKey = BITMAP_KEY_PREFIX + programId;

        List<SeatDTO> dtoList = new ArrayList<>();
        for (Seat seat : seats) {
            String lockKey = LOCK_KEY_PREFIX + programId + ":" + seat.getSeatIndex();
            Long expireSeconds = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);

            // 自愈检测：若数据库是锁定态(1)，但 Redis 租约已过期，执行自动解冻回滚
            if (seat.getStatus() == 1 && (expireSeconds == null || expireSeconds <= 0)) {
                log.info("发现过期未付选座，触发自愈释放: seatId={}, seatIndex={}", seat.getId(), seat.getSeatIndex());
                stringRedisTemplate.opsForValue().setBit(bitmapKey, seat.getSeatIndex(), false);
                seatMapper.resetTimeoutSeat(seat.getId());
                seat.setStatus(0);
                seat.setLockUserId(null);
                expireSeconds = 0L;
            }

            Boolean isMyLocked = (currentUserId != null && currentUserId.equals(seat.getLockUserId()));

            dtoList.add(SeatDTO.builder()
                    .id(seat.getId())
                    .programId(seat.getProgramId())
                    .categoryId(seat.getCategoryId())
                    .area(seat.getArea())
                    .rowNum(seat.getRowNum())
                    .colNum(seat.getColNum())
                    .seatIndex(seat.getSeatIndex())
                    .status(seat.getStatus())
                    .remainingLockSeconds(expireSeconds != null && expireSeconds > 0 ? expireSeconds : 0L)
                    .isMyLocked(isMyLocked)
                    .build());
        }
        return dtoList;
    }

    /**
     * 2. 原子锁定物理座位（Redis Bitmap 预占 + 分布式租约 + DB 乐观锁状态跃迁）
     */
    @Transactional(rollbackFor = Exception.class)
    public SeatLockResponseDTO lockSeats(Long userId, Long programId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new BusinessException(400, "请至少选择一个座位！");
        }
        if (seatIds.size() > 6) {
            throw new BusinessException(400, "单次选座最多不可超过 6 个座位！");
        }

        List<Seat> seats = seatMapper.selectBatchIds(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new BusinessException(404, "选座列表中包含无效座位！");
        }

        String bitmapKey = BITMAP_KEY_PREFIX + programId;
        List<Integer> successfullyLockedIndices = new ArrayList<>();

        try {
            // 阶段 1：Redis 内存层原子位图与租约排他锁定
            for (Seat seat : seats) {
                if (!seat.getProgramId().equals(programId)) {
                    throw new BusinessException(400, "跨场次选座非法！");
                }
                int seatIndex = seat.getSeatIndex();
                String lockKey = LOCK_KEY_PREFIX + programId + ":" + seatIndex;

                // 原子占用：SETNX 锁定 300 秒
                Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                        lockKey, String.valueOf(userId), Duration.ofSeconds(LOCK_TTL_SECONDS)
                );

                if (!Boolean.TRUE.equals(acquired)) {
                    String currentHolder = stringRedisTemplate.opsForValue().get(lockKey);
                    if (!String.valueOf(userId).equals(currentHolder)) {
                        throw new BusinessException(409, String.format("座位 [%s排%s号] 已被其他用户锁定！", seat.getRowNum(), seat.getColNum()));
                    }
                }

                // 同步设置位图 bit = 1 (已占用)
                stringRedisTemplate.opsForValue().setBit(bitmapKey, seatIndex, true);
                successfullyLockedIndices.add(seatIndex);
            }

            // 阶段 2：DB 持久层 CAS 状态机跃迁 (status 0 -> 1)
            for (Seat seat : seats) {
                int updated = seatMapper.lockSeat(seat.getId(), userId);
                if (updated == 0) {
                    throw new BusinessException(409, String.format("座位 [%s排%s号] 状态已发生变更，未能成功锁定！", seat.getRowNum(), seat.getColNum()));
                }
                seat.setStatus(1);
                seat.setLockUserId(userId);
            }

        } catch (Exception e) {
            // 发生任何冲突，严格回滚已占用的 Redis 锁和 Bitmap
            log.warn("选座锁定发生冲突或异常，回滚 Redis 占位: userId={}, indices={}", userId, successfullyLockedIndices);
            for (Integer idx : successfullyLockedIndices) {
                String lockKey = LOCK_KEY_PREFIX + programId + ":" + idx;
                String holder = stringRedisTemplate.opsForValue().get(lockKey);
                if (String.valueOf(userId).equals(holder)) {
                    stringRedisTemplate.delete(lockKey);
                    stringRedisTemplate.opsForValue().setBit(bitmapKey, idx, false);
                }
            }
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(500, "选座锁定失败: " + e.getMessage());
        }

        List<SeatDTO> lockedDTOs = seats.stream().map(s -> SeatDTO.builder()
                .id(s.getId())
                .programId(s.getProgramId())
                .categoryId(s.getCategoryId())
                .area(s.getArea())
                .rowNum(s.getRowNum())
                .colNum(s.getColNum())
                .seatIndex(s.getSeatIndex())
                .status(1)
                .remainingLockSeconds(LOCK_TTL_SECONDS)
                .isMyLocked(true)
                .build()
        ).collect(Collectors.toList());

        return SeatLockResponseDTO.builder()
                .lockedSeats(lockedDTOs)
                .lockExpireSeconds(LOCK_TTL_SECONDS)
                .message("座位锁定成功！已为您保留 5 分钟，请尽快完成支付")
                .build();
    }

    /**
     * 3. 主动释放/取消已锁定的座位
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlockSeats(Long userId, Long programId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        List<Seat> seats = seatMapper.selectBatchIds(seatIds);
        String bitmapKey = BITMAP_KEY_PREFIX + programId;

        for (Seat seat : seats) {
            int seatIndex = seat.getSeatIndex();
            String lockKey = LOCK_KEY_PREFIX + programId + ":" + seatIndex;
            String holder = stringRedisTemplate.opsForValue().get(lockKey);

            if (holder != null && holder.equals(String.valueOf(userId))) {
                stringRedisTemplate.delete(lockKey);
                stringRedisTemplate.opsForValue().setBit(bitmapKey, seatIndex, false);
                seatMapper.unlockSeat(seat.getId(), userId);
            }
        }
    }

    /**
     * 4. 滑动窗口贪心连续座位智能推荐算法 (Consecutive Seat Recommendation)
     */
    public List<SeatDTO> recommendAdjacentSeats(Long programId, Long categoryId, Integer count) {
        if (count == null || count <= 0 || count > 6) {
            throw new BusinessException(400, "连座推荐数量必须在 1~6 之间！");
        }

        // 获取该票档下所有座位按 行排号 升序排列
        List<Seat> allSeats = seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getProgramId, programId)
                .eq(Seat::getCategoryId, categoryId)
                .orderByAsc(Seat::getRowNum, Seat::getColNum));

        if (allSeats.isEmpty()) {
            throw new BusinessException(404, "该场次指定票档暂无座位数据！");
        }

        String bitmapKey = BITMAP_KEY_PREFIX + programId;
        Map<Integer, List<Seat>> rowMap = allSeats.stream().collect(
                Collectors.groupingBy(Seat::getRowNum, LinkedHashMap::new, Collectors.toList())
        );

        List<Seat> bestCandidate = null;
        double bestScore = Double.MAX_VALUE; // 越小越优

        for (Map.Entry<Integer, List<Seat>> entry : rowMap.entrySet()) {
            Integer rowNum = entry.getKey();
            List<Seat> rowSeats = entry.getValue();

            int totalColsInRow = rowSeats.size();
            double centerCol = (totalColsInRow + 1) / 2.0;

            // 过滤该排可用的座位（DB 可选 且 Redis 位图为 0 且 无活跃锁定）
            List<Seat> availableSeats = new ArrayList<>();
            for (Seat s : rowSeats) {
                if (s.getStatus() == 0) {
                    Boolean bit = stringRedisTemplate.opsForValue().getBit(bitmapKey, s.getSeatIndex());
                    if (!Boolean.TRUE.equals(bit)) {
                        availableSeats.add(s);
                    }
                }
            }

            // 在该排可用座位中寻找连续 count 个座位 (colNum 连续)
            for (int i = 0; i <= availableSeats.size() - count; i++) {
                boolean isContiguous = true;
                List<Seat> candidateWindow = new ArrayList<>();
                candidateWindow.add(availableSeats.get(i));

                for (int k = 1; k < count; k++) {
                    Seat prev = availableSeats.get(i + k - 1);
                    Seat curr = availableSeats.get(i + k);
                    if (curr.getColNum() != prev.getColNum() + 1) {
                        isContiguous = false;
                        break;
                    }
                    candidateWindow.add(curr);
                }

                if (isContiguous) {
                    // 评分函数：
                    // 1. 前排优先权重: rowNum * 2.0
                    // 2. 居中视线优先权重: |avgCol - centerCol| * 1.5
                    // 3. 避免单座孤岛: 如果选择后在两端留下了单独 1 个空位，增加孤岛惩罚
                    double avgCol = candidateWindow.stream().mapToInt(Seat::getColNum).average().orElse(0);
                    double centerDist = Math.abs(avgCol - centerCol);
                    double score = (rowNum * 2.0) + (centerDist * 1.5);

                    if (score < bestScore) {
                        bestScore = score;
                        bestCandidate = candidateWindow;
                    }
                }
            }
        }

        if (bestCandidate == null) {
            throw new BusinessException(400, "抱歉，当前票档暂无连续 " + count + " 个相邻空闲座位，请尝试减少人数或自选！");
        }

        return bestCandidate.stream().map(s -> SeatDTO.builder()
                .id(s.getId())
                .programId(s.getProgramId())
                .categoryId(s.getCategoryId())
                .area(s.getArea())
                .rowNum(s.getRowNum())
                .colNum(s.getColNum())
                .seatIndex(s.getSeatIndex())
                .status(0)
                .remainingLockSeconds(0L)
                .isMyLocked(false)
                .build()
        ).collect(Collectors.toList());
    }

    /**
     * 5. 场馆位图与状态全量预热 / 重置同步
     */
    public void syncVenueBitmap(Long programId) {
        List<Seat> seats = seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getProgramId, programId));
        String bitmapKey = BITMAP_KEY_PREFIX + programId;

        for (Seat seat : seats) {
            boolean occupied = (seat.getStatus() != 0);
            stringRedisTemplate.opsForValue().setBit(bitmapKey, seat.getSeatIndex(), occupied);
        }
        log.info("场馆位图预热同步完成: programId={}, 总座位数={}", programId, seats.size());
    }
}
