package com.socialsea.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reason;

    private Long postId;

    private String type; // POST | ANONYMOUS | REEL

    @ManyToOne
    @JoinColumn(name = "reporter_id")
    private User reporter;
}
