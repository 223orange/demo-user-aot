package org.example.demouseraot.controller;

import org.example.demouseraot.entity.User;
import org.example.demouseraot.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
class UserController {
    private UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String home() {
        return "Spring Boot AOT Demo with Java 25 - Application is running!";
    }

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/users/{id}")
    public Optional<User> getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/users/search/{name}")
    public List<User> searchUsers(@PathVariable String name) {
        return userService.searchUsersByName(name);
    }

    @GetMapping("/health")
    public String health() {
        return "AOT Application is healthy - " + LocalDateTime.now();
    }

    @GetMapping("/memory")
    public String memoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        return String.format("Memory Info: Max=%dMB, Total=%dMB, Used=%dMB, Free=%dMB",
                maxMemory / 1024 / 1024,
                totalMemory / 1024 / 1024,
                usedMemory / 1024 / 1024,
                freeMemory / 1024 / 1024);
    }
}
