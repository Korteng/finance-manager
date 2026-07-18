package ru.korteng.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.korteng.notification.entity.Notification;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByEventId(UUID eventId);
    Page<Notification> findByUserId(Long userId, Pageable pageable);
}