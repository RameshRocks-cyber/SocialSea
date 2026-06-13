package com.socialsea.service;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AdminUserDeletionService {
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public DeletedUserSummary deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        String normalizedEmail = normalizeEmail(user.getEmail());

        deleteByUserId("delete from LoginSession s where s.userId = :userId", userId);
        deleteByUserId("delete from FollowRequest fr where fr.sender.id = :userId or fr.receiver.id = :userId", userId);
        deleteByUserId("delete from Follow f where f.follower.id = :userId or f.following.id = :userId", userId);
        deleteByUserId("delete from ChatMessage m where m.sender.id = :userId or m.receiver.id = :userId", userId);
        deleteByUserId("delete from ChatGroupMember m where m.user.id = :userId", userId);
        deleteByUserId("delete from SavedPost sp where sp.user.id = :userId", userId);
        deleteByUserId("delete from PostLike l where l.user.id = :userId", userId);
        deleteByUserId("delete from Comment c where c.user.id = :userId", userId);
        deleteByUserId("delete from StoryLike sl where sl.user.id = :userId", userId);
        deleteByUserId("delete from StoryComment sc where sc.user.id = :userId", userId);
        deleteByUserId("delete from StoryView sv where sv.user.id = :userId", userId);
        deleteByUserId("delete from AmbulanceDriverRequest r where r.user.id = :userId", userId);
        deleteByUserId("delete from JobOpening j where j.owner.id = :userId", userId);
        deleteByUserId("delete from Report r where r.reporter.id = :userId", userId);

        deleteByEmail("delete from Notification n where lower(n.recipient) = lower(:email)", normalizedEmail);
        deleteByEmail("delete from WebPushSubscription s where lower(s.recipient) = lower(:email)", normalizedEmail);

        List<Long> postIds = findIds("select p.id from Post p where p.user.id = :userId", userId);
        if (!postIds.isEmpty()) {
            deleteByIds("delete from SavedPost sp where sp.post.id in :ids", postIds);
            deleteByIds("delete from PostLike l where l.post.id in :ids", postIds);
            deleteByIds("delete from Comment c where c.post.id in :ids", postIds);
            deleteByIds("delete from Report r where r.postId in :ids", postIds);
            deleteByIds("delete from Post p where p.id in :ids", postIds);
        }

        List<Long> storyIds = findIds("select s.id from Story s where s.user.id = :userId", userId);
        if (!storyIds.isEmpty()) {
            deleteByIds("delete from StoryLike sl where sl.story.id in :ids", storyIds);
            deleteByIds("delete from StoryComment sc where sc.story.id in :ids", storyIds);
            deleteByIds("delete from StoryView sv where sv.story.id in :ids", storyIds);
            deleteByIds("delete from Story s where s.id in :ids", storyIds);
        }

        List<Long> groupIds = findIds("select g.id from ChatGroup g where g.owner.id = :userId", userId);
        if (!groupIds.isEmpty()) {
            deleteByIds("delete from ChatMessage m where m.group.id in :ids", groupIds);
            deleteByIds("delete from ChatGroupMember m where m.group.id in :ids", groupIds);
            deleteByIds("delete from ChatGroup g where g.id in :ids", groupIds);
        }

        userRepository.delete(user);
        return new DeletedUserSummary(userId, normalizedEmail);
    }

    private List<Long> findIds(String jpql, Long userId) {
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    private void deleteByUserId(String jpql, Long userId) {
        entityManager.createQuery(jpql)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private void deleteByEmail(String jpql, String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        entityManager.createQuery(jpql)
                .setParameter("email", email)
                .executeUpdate();
    }

    private void deleteByIds(String jpql, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        entityManager.createQuery(jpql)
                .setParameter("ids", ids)
                .executeUpdate();
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record DeletedUserSummary(Long userId, String email) {}
}
