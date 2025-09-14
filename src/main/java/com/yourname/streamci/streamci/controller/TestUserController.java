package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.User;
import com.yourname.streamci.streamci.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestUserController {

    private final UserService userService;

    public TestUserController(UserService userService) {
        this.userService = userService;
    }

    // test endpoint to create a user
    @PostMapping("/create-user")
    public ResponseEntity<User> createUser(@RequestBody Map<String, String> body) {
        String clerkUserId = body.get("clerkUserId");
        User user = userService.createOrUpdateUser(clerkUserId);
        return ResponseEntity.ok(user);
    }

    // test endpoint to get user
    @GetMapping("/user/{clerkUserId}")
    public ResponseEntity<User> getUser(@PathVariable String clerkUserId) {
        return userService.findByClerkUserId(clerkUserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // test endpoint to save token
    @PostMapping("/save-token")
    public ResponseEntity<String> saveToken(@RequestBody Map<String, String> body) {
        try {
            String clerkUserId = body.get("clerkUserId");
            String token = body.get("token");

            userService.saveGithubToken(clerkUserId, token);
            return ResponseEntity.ok("token saved successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("error: " + e.getMessage());
        }
    }
}