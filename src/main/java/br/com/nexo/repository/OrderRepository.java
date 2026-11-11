package br.com.nexo.repository;

import br.com.nexo.domain.OrderStatus;
import br.com.nexo.domain.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @Query("select o from PurchaseOrder o where (:status is null or o.status = :status) and lower(o.customer) like lower(concat('%',:q,'%'))")
    Page<PurchaseOrder> search(@Param("status") OrderStatus status, @Param("q") String query, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findLocked(@Param("id") Long id);
    long countByStatus(OrderStatus status);
    @Query("select sum(i.unitPrice * i.quantity) from OrderItem i where i.order.status = br.com.nexo.domain.OrderStatus.CONFIRMADO")
    BigDecimal confirmedRevenue();
}
