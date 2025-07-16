package ru.korteng.finance_manager.service;

import org.springframework.stereotype.Service;
import ru.korteng.finance_manager.dto.TransactionRequest;

@Service
public class TransactionService {

    public Object createTransaction(TransactionRequest request) {

        return "Transaction created (stub)";
    }

    public Object getTransactionById(Long id) {

        return "Transaction " + id + " (stub)";
    }
}
