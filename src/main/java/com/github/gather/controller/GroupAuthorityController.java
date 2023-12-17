package com.github.gather.controller;

import com.github.gather.entity.User;
import com.github.gather.service.AuthService;
import com.github.gather.service.group.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/group")
public class GroupAuthorityController {

    private final AuthService authService;
    private final GroupService groupService;

    public GroupAuthorityController(AuthService authService, GroupService groupService) {
        this.authService = authService;
        this.groupService = groupService;
    }

    // 방장 권한 이전
    @PostMapping("/{groupId}/transferLeader/{newLeaderId}")
    public ResponseEntity<?> transferLeader(@PathVariable Long groupId, @PathVariable Long newLeaderId, HttpServletRequest request) {
        User currentUser = authService.checkToken(request);
        groupService.transferLeader(groupId, newLeaderId, currentUser);

        return ResponseEntity.ok().build();
    }

    // 멤버 추방
    @PostMapping("/{groupId}/kick/{userId}")
    public ResponseEntity<?> kickMember(@PathVariable Long groupId, @PathVariable Long userId, HttpServletRequest request) {
        User currentUser = authService.checkToken(request);
        groupService.kickMember(groupId, userId, currentUser);

        return ResponseEntity.ok().build();
    }

}
