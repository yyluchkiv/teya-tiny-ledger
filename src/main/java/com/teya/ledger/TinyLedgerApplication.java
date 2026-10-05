package com.teya.ledger;

import com.teya.ledger.domain.Ledger;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TinyLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TinyLedgerApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Ledger ledger(Clock clock) {
        return new Ledger(clock);
    }
}
