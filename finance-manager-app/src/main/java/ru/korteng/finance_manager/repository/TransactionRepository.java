package ru.korteng.finance_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.korteng.finance_manager.entity.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.category.id = :categoryId "
            + "AND t.createdAt >= :from AND t.createdAt < :to")
    BigDecimal sumAmountForCategoryInPeriod(@Param("userId") Long userId,
                                            @Param("categoryId") Long categoryId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category "
            + "WHERE t.userId = :userId "
            + "AND (cast(:categoryId as long) IS NULL OR t.category.id = :categoryId) "
            + "AND (cast(:from as timestamp) IS NULL OR t.createdAt >= :from) "
            + "AND (cast(:to as timestamp) IS NULL OR t.createdAt < :to) "
            + "ORDER BY t.createdAt DESC")
    List<Transaction> findAllForUser(@Param("userId") Long userId,
                                     @Param("categoryId") Long categoryId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);
}
