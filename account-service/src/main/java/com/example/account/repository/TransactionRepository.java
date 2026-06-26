package com.example.account.repository;

import com.example.account.domain.Transaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
