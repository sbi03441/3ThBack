package com.github.gather.controller;

import com.github.gather.dto.MessageDto;
import com.github.gather.entity.User;
import com.github.gather.service.AuthService;
import com.github.gather.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;

@Tag(name = "ChatRoom",description = "채팅방 API")
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;

    public ChatController(ChatService chatService, AuthService authService) {
        this.chatService = chatService;
        this.authService = authService;
    }

    @Operation(summary = "채팅방 입장" , description = "채팅방 입장을 진행합니다.")
    @PostMapping("/enter/{roomId}")
    public ResponseEntity<Void> enterChatRoom(@PathVariable Long roomId, HttpServletRequest request) {
        User user = authService.checkToken(request);
        chatService.enterChatRoom(user.getUserId(), roomId);
        log.info("User entered: {}", user);  // 로그 추가
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "채팅방 퇴장" , description = "채팅방 퇴장을 진행합니다.")
    @PostMapping("/leave/{roomId}")
    public ResponseEntity<Void> leaveChatRoom(@PathVariable Long roomId, HttpServletRequest request) {
        User user = authService.checkToken(request);
        chatService.leaveChatRoom(user.getUserId(), roomId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "메세지 전달" , description = "메세지 전달을 진행합니다.")
    @MessageMapping("/chat/{roomId}")
    @SendTo("/topic/{roomId}")
    public MessageDto send(MessageDto messageDto, @AuthenticationPrincipal Principal principal) {

        User user = authService.getUser(principal);
        messageDto.setUserId(user.getUserId());
        chatService.saveMessage(messageDto);
        return messageDto;
    }
}
