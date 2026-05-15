package com.cognilogistic.order.repository;

import com.cognilogistic.order.model.DocumentType;
import com.cognilogistic.order.model.OrderDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link OrderDocument}.
 *
 * <p>Used by the order detail page (lists every document attached to an order) and by
 * GR / LR / invoice generation flows (look up an existing doc before generating a fresh one).
 */
public interface OrderDocumentRepository extends JpaRepository<OrderDocument, String> {

    /**
     * Returns every document attached to an order, newest first.
     *
     * @param orderId the order's UUID
     * @return list of documents, sorted by upload time descending
     */
    List<OrderDocument> findByOrderIdOrderByUploadedAtDesc(String orderId);

    /**
     * Returns the most recent document of a specific type attached to this order. Used
     * for "do we already have a GR for this order?" lookups before re-rendering the PDF.
     *
     * @param orderId the order's UUID
     * @param docType the document type (GR / LR / INVOICE / CUSTOM)
     * @return the most recent matching document, if any
     */
    Optional<OrderDocument> findFirstByOrderIdAndDocTypeOrderByUploadedAtDesc(
            String orderId, DocumentType docType);
}
