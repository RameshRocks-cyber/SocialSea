package com.socialsea.model;

import jakarta.persistence.*;

@Entity
@Table(name = "follows",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"follower_id", "following_id"}
    )
)
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "follower_id")
    private User follower;

    @ManyToOne
    @JoinColumn(name = "following_id")
    private User following;

    public Follow() {}

    public Follow(Long id, User follower, User following) {
        this.id = id;
        this.follower = follower;
        this.following = following;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getFollower() { return follower; }
    public void setFollower(User follower) { this.follower = follower; }

    public User getFollowing() { return following; }
    public void setFollowing(User following) { this.following = following; }
}
