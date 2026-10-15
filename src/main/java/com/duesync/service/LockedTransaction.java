package com.duesync.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class LockedTransaction {

    private final Object lock = new Object();
    private final TransactionTemplate transaction;

    public LockedTransaction(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public <T> T run(Supplier<T> work) {
        synchronized (lock) {
            return transaction.execute(status -> work.get());
        }
    }
}
