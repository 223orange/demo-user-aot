package org.example.demouseraot;

import org.example.demouseraot.entity.User;
import org.example.demouseraot.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoUserAotApplication {
    static void main(String[] args) {
        SpringApplication.run(DemoUserAotApplication.class, args);
    }
    @Bean
    CommandLineRunner initData(UserRepository userRepository) {
        return args -> {
            if (userRepository.count() == 0) {
                userRepository.save(new User("John Doe", "john@example.com"));
                userRepository.save(new User("Jane Smith", "jane@example.com"));
                userRepository.save(new User("Bob Johnson", "bob@example.com"));
            }
        };
    }
}
