package com.cognilogistic.order.repository;

import com.cognilogistic.order.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Customer} entities.
 *
 * <p>WhatsApp phone is the unique customer identifier when set; {@code NULL} is allowed for
 * shadow rows created before a phone is known (MySQL UNIQUE permits multiple NULLs).
 */
public interface CustomerRepository extends JpaRepository<Customer, String> {

    /**
     * Looks up a customer by their WhatsApp phone number.
     * Used for login lookups (portal auth) and BR-04 shadow-customer detection on order creation.
     *
     * @param whatsappPhone the customer's WhatsApp phone number
     * @return the matching customer, or empty if no account exists for that phone
     */
    Optional<Customer> findByWhatsappPhone(String whatsappPhone);
}
