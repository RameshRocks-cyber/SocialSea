package com.socialsea.model;

import jakarta.persistence.*;

@Entity
@Table(name = "story_likes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "story_id"})
})
public class StoryLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    public StoryLike() {}

    public StoryLike(Long id, User user, Story story) {
        this.id = id;
        this.user = user;
        this.story = story;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Story getStory() {
        return story;
    }

    public void setStory(Story story) {
        this.story = story;
    }
}
