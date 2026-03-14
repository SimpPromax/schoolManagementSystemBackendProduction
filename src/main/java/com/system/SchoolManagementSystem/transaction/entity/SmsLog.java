package com.system.SchoolManagementSystem.transaction.entity;

import com.system.SchoolManagementSystem.student.entity.Student;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sms_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private PaymentTransaction paymentTransaction;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SmsStatus status = SmsStatus.PENDING;

    @Column(name = "gateway_response", length = 1000)
    private String gatewayResponse;

    @Column(name = "gateway_message_id")
    private String gatewayMessageId;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "delivery_status")
    private String deliveryStatus;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    public enum SmsStatus {
        PENDING, SENT, DELIVERED, FAILED, QUEUED
    }
}