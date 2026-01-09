package com.socialsea.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;

    private boolean read = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    private User user; // receiver
}
