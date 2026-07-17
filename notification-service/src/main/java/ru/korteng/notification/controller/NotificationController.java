package ru.korteng.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import ru.korteng.notification.entity.Notification;
import ru.korteng.notification.repository.NotificationRepository;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repository;

    @GetMapping
    public Page<Notification> getByUser(@RequestParam Long userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable);
    }
}