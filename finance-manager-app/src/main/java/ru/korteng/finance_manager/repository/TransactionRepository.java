package ru.korteng.finance_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.korteng.finance_manager.dto.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
