package so.budget.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class BudgetApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BudgetApiApplication.class, args);
    }

}
