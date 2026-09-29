package ru.korteng.finance_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.korteng.finance_manager.entity.Budget;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.category "
            + "WHERE b.userId = :userId AND b.category.id = :categoryId AND b.period = :period")
    Optional<Budget> findByUserAndCategoryAndPeriod(@Param("userId") Long userId,
                                                    @Param("categoryId") Long categoryId,
                                                    @Param("period") LocalDate period);

    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.category "
            + "WHERE b.userId = :userId AND b.period = :period")
    List<Budget> findAllByUserAndPeriod(@Param("userId") Long userId, @Param("period") LocalDate period);
}
