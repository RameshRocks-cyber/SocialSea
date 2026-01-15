package com.socialsea.model;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "PostLike") // "Like" is a reserved JPQL keyword, so we rename the entity model
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "likes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "post_id"})
})
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "post_id")
    private Post post;
}
