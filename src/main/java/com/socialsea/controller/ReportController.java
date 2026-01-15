package com.socialsea.controller;

import com.socialsea.model.Report;
import com.socialsea.model.User;
import com.socialsea.repository.ReportRepository;
import com.socialsea.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report")
@CrossOrigin("https://socialsea.netlify.app")
public class ReportController {

    private final ReportRepository reportRepo;
    private final UserRepository userRepo;

    public ReportController(ReportRepository reportRepo, UserRepository userRepo) {
        this.reportRepo = reportRepo;
        this.userRepo = userRepo;
    }

    @PostMapping
    public void report(
        @RequestParam Long postId,
        @RequestParam String reason,
        @RequestParam String type,
        Authentication auth
    ) {
        User u = userRepo.findByUsername(auth.getName()).orElseThrow();

        Report r = new Report();
        r.setPostId(postId);
        r.setType(type);
        r.setReason(reason);
        r.setReporter(u);

        reportRepo.save(r);
    }
}
