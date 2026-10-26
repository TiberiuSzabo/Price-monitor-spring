package com.persPrjUBB.priceMonitor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricePointRepository extends JpaRepository<PricePoint, Long> {
    List<PricePoint> findByProductIdOrderByCheckedAtAsc(Long productId);

    void deleteByProductId(Long productId);
}
