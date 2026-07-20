package br.ufes.soe.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class NbaDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(NbaDashboardApplication.class, args);
    }
}
