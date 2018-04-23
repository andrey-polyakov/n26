package com.n26;

import com.n26.restful.api.dto.StatisticsDto;
import com.n26.restful.api.dto.TransactionDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * This is a basic integration test to save you some headache.
 */
@RunWith(SpringRunner.class)
@TestConfiguration("ProjectConfiguration")
@TestPropertySource("classpath:config.properties")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class IntegrationTest {
    public static final String TRANSACTIONS = "/transactions";
    public static final String STATISTICS = "/statistics";

    private ExecutorService e = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void basicRun() throws InterruptedException {
        long count = 5l;
        for (int ii = 0; ii < count; ii ++) {
            e.submit(() -> {
                restTemplate.postForEntity(TRANSACTIONS, new TransactionDto(0.3, new Date().getTime()), null);
                restTemplate.postForEntity(TRANSACTIONS, new TransactionDto(0.2, new Date().getTime()), null);

            });
        }
        Thread.yield();
        ResponseEntity<StatisticsDto> entity = restTemplate.getForEntity(STATISTICS, StatisticsDto.class);
        while(entity.getBody().getCount() < 10) {
            entity = restTemplate.getForEntity(STATISTICS, StatisticsDto.class);
        }
        System.out.println(entity);
        assertTrue(entity.getStatusCode().is2xxSuccessful());
        assertEquals(0.2, entity.getBody().getMin().doubleValue(), 0.00001);
        assertEquals(0.3, entity.getBody().getMax().doubleValue(), 0.00001);
        assertEquals(0.25, entity.getBody().getAvg().doubleValue(), 0.00001);
        assertEquals(2.5, entity.getBody().getSum().doubleValue(), 0.00001);
        assertEquals(10, entity.getBody().getCount().longValue());

    }
}
