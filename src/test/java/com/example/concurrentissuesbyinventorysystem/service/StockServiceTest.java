package com.example.concurrentissuesbyinventorysystem.service;

import com.example.concurrentissuesbyinventorysystem.IntegrationTestSupport;
import com.example.concurrentissuesbyinventorysystem.domain.Stock;
import com.example.concurrentissuesbyinventorysystem.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class StockServiceTest extends IntegrationTestSupport {

    @Autowired
    private PessimisticLockStockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @AfterEach
    public void tearDown() {
        stockRepository.deleteAllInBatch();
    }

    @DisplayName("상품의 재고가 1 감소한다.")
    @Test
    public void decrease_test() {
        //given
        Stock stock = new Stock(1L, 100L);
        Stock savedStock = stockRepository.save(stock);

        //when
        stockService.decrease(savedStock.getId(), 1L);

        //then
        Stock findStock = stockRepository.findById(savedStock.getId()).orElseThrow();
        assertThat(findStock.getQuantity()).isEqualTo(99L);
    }

    @DisplayName("상품을 동시에 100명이 주문하면, 비관락에 의해 동시성 문제가 발생하지 않고 재고가 0이 된다.")
    @Test
    public void sameTimeOrderTest() throws InterruptedException {
        //given
        Stock stock = new Stock(1L, 100L);
        Stock savedStock = stockRepository.save(stock);

        //when
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decrease(savedStock.getId(), 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        //then
        Stock findStock = stockRepository.findById(savedStock.getId()).orElseThrow();
        assertThat(findStock.getQuantity()).isEqualTo(0L);
    }
}