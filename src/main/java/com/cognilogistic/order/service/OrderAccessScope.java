package com.cognilogistic.order.service;

import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.order.model.Order;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.user.repository.OfficeRepository;
import com.cognilogistic.user.repository.UserOfficeAssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Centralises branch-office read/write scope for TP users. TP_ADMIN ({@link AuthPrincipal#isPrimary()})
 * may access any office in the tenancy; {@code TP_TRANSPORT_MANAGER} users are limited to
 * {@code user_office_assignments}.
 */
@Service
public class OrderAccessScope {

    private static final Pattern UUID_HEX = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final UserOfficeAssignmentRepository userOffices;
    private final OfficeRepository offices;

    public OrderAccessScope(UserOfficeAssignmentRepository userOffices, OfficeRepository offices) {
        this.userOffices = userOffices;
        this.offices = offices;
    }

    /**
     * @return {@code null} when the caller may see all offices in the TP; otherwise the
     *         non-empty list of assigned office ids (empty list means no access).
     */
    public List<String> assignedOfficeIdsOrNull(AuthPrincipal me) {
        if (me == null || me.isPrimary()) {
            return null;
        }
        return userOffices.findByUserId(me.userId()).stream()
                .map(a -> a.getOfficeId())
                .toList();
    }

    public void requireOfficeBelongsToTp(String officeId, String tpAccountId) {
        offices.findById(officeId)
                .filter(o -> o.getTpAccountId().equals(tpAccountId))
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN,
                        "Office does not belong to your TP account"));
    }

    /**
     * Validates an optional office filter for list/dashboard queries and returns the
     * effective single-office filter (may be {@code null}).
     */
    public String resolveOfficeFilter(AuthPrincipal me, String officeIdRaw, String tpAccountId) {
        String officeId = blankToNull(officeIdRaw);
        if (officeId != null) {
            if (!UUID_HEX.matcher(officeId).matches()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "officeId must be a valid UUID",
                        java.util.Map.of("fields", java.util.Map.of("officeId", "officeId must be a valid UUID")));
            }
            requireOfficeBelongsToTp(officeId, tpAccountId);
            requireOfficeInAssignments(me, officeId);
            return officeId;
        }
        List<String> assigned = assignedOfficeIdsOrNull(me);
        if (assigned != null && assigned.isEmpty()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "No branch offices assigned to your account");
        }
        return null;
    }

    /**
     * When non-primary, returns assigned office ids for {@code IN} clause; otherwise {@code null}.
     */
    public List<String> officeIdsForQuery(AuthPrincipal me, String resolvedOfficeId) {
        if (resolvedOfficeId != null) {
            return null;
        }
        return assignedOfficeIdsOrNull(me);
    }

    public void requireReadableOrder(AuthPrincipal me, Order order) {
        if (me == null || me.isPrimary()) {
            return;
        }
        String officeId = order.getAssignedOfficeId();
        if (officeId == null) {
            return;
        }
        requireOfficeInAssignments(me, officeId);
    }

    private void requireOfficeInAssignments(AuthPrincipal me, String officeId) {
        List<String> assigned = assignedOfficeIdsOrNull(me);
        if (assigned == null) {
            return;
        }
        if (!assigned.contains(officeId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "User not assigned to this office");
        }
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
