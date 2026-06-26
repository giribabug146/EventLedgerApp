package com.example.gateway.repository;

import com.example.gateway.domain.LedgerEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEventRepository extends JpaRepository<LedgerEvent, String> {
    List<LedgerEvent> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
