/* 文件作用：看板响应结构，金额保持BigDecimal，空比例返回null而非虚构百分比。 */
package com.bishe.crm.module.analytics.vo;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class DashboardViews {
    private DashboardViews() {}
    public record Owner(Long id,String name,String teamName) {}
    public record Metrics(long customerCount,long openOpportunities,BigDecimal signedAmount,BigDecimal receivedAmount,BigDecimal outstandingAmount,BigDecimal overdueAmount,BigDecimal currentContractAmount,BigDecimal currentReceivedAmount,BigDecimal currentCollectionRate) {}
    public record Stage(String code,String name,long count,BigDecimal amount) {}
    public record Trend(String month,BigDecimal signedAmount,BigDecimal receivedAmount) {}
    public record Rank(Long ownerId,String ownerName,String teamName,long signedCount,BigDecimal signedAmount,BigDecimal receivedAmount,long wonCount) {}
    public record Conversion(long cohortCount,long dealCount,long wonCount,BigDecimal dealToWonRate,BigDecimal averageSigningDays) {}
    public record Action(String type,String title,long count,String target) {}
    public record ProcessQuality(List<StageDuration> stageDurations,long rollbackCount) {}
    public record StageDuration(String code,String name,long opportunityCount,BigDecimal averageDays) {}
    public record Result(LocalDate from,LocalDate to,String scope,LocalDateTime generatedAt,String source,Metrics metrics,List<Stage> stages,List<Trend> trends,List<Rank> ranking,Conversion conversion,List<Action> actions,long legacyContracts,long legacyReceipts,ProcessQuality processQuality) {}
}
