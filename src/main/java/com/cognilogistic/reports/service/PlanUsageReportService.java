package com.cognilogistic.reports.service;

import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.reports.dto.PlanUsageReportDto;
import com.cognilogistic.user.model.Plan;
import com.cognilogistic.user.model.TpAccount;
import com.cognilogistic.user.repository.TpAccountJpa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Plan-tier usage report — current month tender count vs the plan cap.
 *
 * <p>Cap rules per BR-PLN-03:
 * <ul>
 *   <li>{@link Plan#BASIC} — 5 tenders/month.</li>
 *   <li>{@link Plan#PREMIUM}, {@link Plan#ENTERPRISE} — unlimited.</li>
 * </ul>
 *
 * <p>The {@code plan_usage} table is the post-UAT canonical counter; until that's
 * populated by the tender-publish flow, this service reads tender counts directly
 * out of {@code tenders} table for the current calendar month. The result matches
 * what {@code plan_usage.tender_count} will eventually hold.
 */
@Service
public class PlanUsageReportService {

    /** BR-PLN-03 cap for BASIC tier. PREMIUM / ENTERPRISE are uncapped (null). */
    private static final int BASIC_MONTHLY_TENDER_CAP = 5;

    @PersistenceContext
    private EntityManager em;

    private final TpAccountJpa tpAccountJpa;

    public PlanUsageReportService(TpAccountJpa tpAccountJpa) {
        this.tpAccountJpa = tpAccountJpa;
    }

    /**
     * Builds the plan-usage snapshot for the given TP for the current calendar month
     * (UTC).
     *
     * @param tpAccountId the TP whose usage to report
     * @return the snapshot DTO
     * @throws ApiException with {@link ErrorCode#OFFICE_NOT_FOUND} if the TP doesn't exist —
     *                      no dedicated TP-not-found code today, so we reuse this one.
     *                      The follow-up "TP_NOT_FOUND error code" is tracked in user.md.
     */
    @Transactional(readOnly = true)
    public PlanUsageReportDto currentMonth(String tpAccountId) {
        TpAccount tp = tpAccountJpa.findById(tpAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.OFFICE_NOT_FOUND, "TP account not found: " + tpAccountId));

        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC);

        TypedQuery<Long> q = em.createQuery("""
                SELECT COUNT(t.id)
                  FROM Tender t
                 WHERE t.tpAccountId = :tp
                   AND t.createdAt >= :start
                """, Long.class);
        q.setParameter("tp", tpAccountId);
        q.setParameter("start", monthStart);
        long tendersThisMonth = q.getSingleResult();

        Integer cap = capFor(tp.getPlan());

        return new PlanUsageReportDto(tpAccountId, tp.getPlan(), cap, tendersThisMonth);
    }

    /** Maps a plan tier to its monthly tender cap; null means unlimited. */
    private static Integer capFor(Plan plan) {
        if (plan == null) return BASIC_MONTHLY_TENDER_CAP;
        return switch (plan) {
            case BASIC -> BASIC_MONTHLY_TENDER_CAP;
            case PREMIUM, ENTERPRISE -> null;
        };
    }
}
