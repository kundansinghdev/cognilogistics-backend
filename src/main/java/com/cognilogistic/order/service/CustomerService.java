package com.cognilogistic.order.service;

import com.cognilogistic.order.model.Customer;
import com.cognilogistic.order.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Service for customer data access and shadow-customer creation (BR-04).
 *
 * <p>Customers are uniquely identified by WhatsApp phone <em>when one is set</em>; phone-less
 * shadow rows are supported for orders created before a number is captured.
 */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /**
     * Looks up a customer by WhatsApp phone number without creating one.
     * Returns empty if no customer record exists for that phone.
     *
     * @param whatsappPhone the phone number to search for
     * @return the customer if found, or empty
     */
    @Transactional(readOnly = true)
    public Optional<Customer> lookup(String whatsappPhone) {
        if (!StringUtils.hasText(whatsappPhone)) {
            return Optional.empty();
        }
        return customers.findByWhatsappPhone(whatsappPhone);
    }

    /**
     * TP-scoped customer lookup for order pre-fill: returns a row only when
     * {@code created_by_tp_id} matches the caller's TP (prevents cross-tenant phone probing).
     */
    @Transactional(readOnly = true)
    public Optional<Customer> lookupForTp(String whatsappPhone, String tpAccountId) {
        if (!StringUtils.hasText(whatsappPhone) || !StringUtils.hasText(tpAccountId)) {
            return Optional.empty();
        }
        return customers.findByWhatsappPhone(whatsappPhone)
                .filter(c -> tpAccountId.equals(c.getCreatedByTp()));
    }

    /**
     * Retrieves a customer by database ID.
     *
     * @param id the customer's primary key
     * @return the customer if found, or empty
     */
    @Transactional(readOnly = true)
    public Optional<Customer> findById(String id) {
        return customers.findById(id);
    }

    /**
     * BR-04: finds an existing customer by phone number, or auto-creates a shadow customer if none exists.
     * Order creation must never fail because a customer is unregistered — a shadow placeholder is
     * created instead so the TP can still manage the order.
     *
     * @param whatsappPhone the customer's WhatsApp phone number
     * @param createdByTp   the TP account ID performing the order creation (recorded on the shadow row)
     * @return the existing or newly created customer
     */
    @Transactional
    public Customer findOrCreateShadow(String whatsappPhone, String createdByTp) {
        return findOrCreateShadow(whatsappPhone, createdByTp, null);
    }

    /**
     * Same as {@link #findOrCreateShadow(String, String)}, but when a new shadow row is created
     * (or an existing shadow has no {@code legal_name} yet), stamps {@code displayName} onto the customer.
     *
     * <p>When {@code whatsappPhone} is {@code null} or blank, no lookup is possible —
     * a brand-new shadow customer is created with {@code phone = NULL}. Multiple phone-less
     * shadows can coexist (UNIQUE on phone treats each NULL as distinct in MySQL), so the
     * caller will get a fresh row per order until the customer is edited and a real phone
     * is captured.
     */
    @Transactional
    public Customer findOrCreateShadow(String whatsappPhone, String createdByTp, String displayName) {
        String normalised = (whatsappPhone == null || whatsappPhone.isBlank()) ? null : whatsappPhone;
        if (normalised == null) {
            Customer c = Customer.newShadow(null, createdByTp);
            if (displayName != null && !displayName.isBlank()) {
                c.setName(displayName.trim());
            }
            return customers.save(c);
        }
        return customers.findByWhatsappPhone(normalised)
                .map(existing -> maybeFillName(existing, displayName))
                .orElseGet(() -> {
                    Customer c = Customer.newShadow(normalised, createdByTp);
                    if (displayName != null && !displayName.isBlank()) {
                        c.setName(displayName.trim());
                    }
                    return customers.save(c);
                });
    }

    private Customer maybeFillName(Customer existing, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return existing;
        }
        if (existing.getName() == null || existing.getName().isBlank()) {
            existing.setName(displayName.trim());
            return customers.save(existing);
        }
        return existing;
    }
}
