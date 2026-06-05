package com.stock.stockSimulator.repository;

import com.stock.stockSimulator.domain.MemberStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberStockRepository extends JpaRepository<MemberStock, Long> {

    Optional<MemberStock> findByMemberIdAndStockCode(Long memberId, String stockCode);
    List<MemberStock> findAllByMemberId(Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    INSERT INTO member_stock (member_id, stock_code, quantity, average_price)
                    VALUES (:memberId, :stockCode, :quantity, :price)
                    ON DUPLICATE KEY UPDATE
                        average_price = CASE
                            WHEN quantity + VALUES(quantity) = 0 THEN 0
                            ELSE FLOOR(((COALESCE(average_price, 0) * quantity) + (:price * :quantity)) / (quantity + VALUES(quantity)))
                        END,
                        quantity = quantity + VALUES(quantity)
                    """,
            nativeQuery = true
    )
    void addBuyPosition(@Param("memberId") Long memberId,
                        @Param("stockCode") String stockCode,
                        @Param("quantity") int quantity,
                        @Param("price") long price);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MemberStock ms SET ms.quantity = ms.quantity - :quantity WHERE ms.memberId = :memberId AND ms.stockCode = :stockCode")
    int subtractQuantity(@Param("memberId") Long memberId,
                         @Param("stockCode") String stockCode,
                         @Param("quantity") int quantity);
}
