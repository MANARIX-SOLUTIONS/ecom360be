package com.ecom360.admin.application.dto;

import java.util.List;

public record AdminStatsResponse(
        long businessesCount,
        long usersCount,
        long storesCount,
        long monthlyRevenue,
        List<PlanCount> planDistribution,
        List<TopBusiness> topBusinesses
) {
    public record PlanCount(String plan, int count, int pct) {}
    public record TopBusiness(String name, String owner, long revenue, int storesCount, String plan) {}
}
