package com.mall.pro.dto;

public class InventoryCheckResult {

    /** 初始总配额度 */
    private int initialStock;
    /** postgresql的数据剩余库存 */
    private int dbStock;
    /** redis的缓存的剩余库存 */
    private int redisStock;
    /** 实际售出有效订单数量 */
    private long orderCount;
    /** 守恒差额：初始总配额-（剩余库存+售出订单） */
    private int diff;
    /** 是否存在超卖问题，如果剩余库存和redis剩余库存都大于0 */
    private boolean nonNegative;
    /** 本维度是否完全通过 */
    private boolean passed;
    /** 诊断结果描述 */
    private String message;

}
