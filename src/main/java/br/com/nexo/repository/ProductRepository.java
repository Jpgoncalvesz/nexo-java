package br.com.nexo.repository;

import br.com.nexo.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySku(String sku);
    @Query("select p from Product p where lower(p.name) like lower(concat('%',:q,'%')) or lower(p.sku) like lower(concat('%',:q,'%'))")
    Page<Product> search(@Param("q") String query, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findLocked(@Param("id") Long id);
}
