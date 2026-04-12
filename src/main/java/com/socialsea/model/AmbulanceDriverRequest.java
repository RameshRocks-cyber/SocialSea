package com.socialsea.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ambulance_driver_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AmbulanceDriverRequest {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 120)
    private String driverName;

    @Column(length = 40)
    private String phone;

    @Column(length = 80)
    private String vehicleNumber;

    @Column(length = 140)
    private String serviceName;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    @Column(length = 255)
    private String reviewedBy;

    @Column(length = 500)
    private String rejectReason;
}
