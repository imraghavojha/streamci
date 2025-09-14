package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.User;
import com.yourname.streamci.streamci.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.time.LocalDateTime;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String ALGORITHM = "AES";

    private final UserRepository userRepository;
    private final String encryptionKey;

    public UserService(UserRepository userRepository,
                       @Value("${app.encryption.key:default-key-change-me-in-prod}") String encryptionKey) {
        this.userRepository = userRepository;
        this.encryptionKey = encryptionKey;
    }

    public User createOrUpdateUser(String clerkUserId) {
        Optional<User> existing = userRepository.findByClerkUserId(clerkUserId);

        if (existing.isPresent()) {
            User user = existing.get();
            user.setLastActiveAt(LocalDateTime.now());
            return userRepository.save(user);
        }

        User newUser = User.builder()
                .clerkUserId(clerkUserId)
                .tokenValidated(false)
                .build();

        logger.info("creating new user for clerk id: {}", clerkUserId);
        return userRepository.save(newUser);
    }

    public Optional<User> findByClerkUserId(String clerkUserId) {
        return userRepository.findByClerkUserId(clerkUserId);
    }

    public User saveGithubToken(String clerkUserId, String token) throws Exception {
        User user = findByClerkUserId(clerkUserId)
                .orElseThrow(() -> new RuntimeException("user not found"));

        String encryptedToken = encryptToken(token);
        user.setGithubTokenEncrypted(encryptedToken);
        user.setTokenValidated(false); // will validate separately

        return userRepository.save(user);
    }

    public String decryptGithubToken(User user) throws Exception {
        if (user.getGithubTokenEncrypted() == null) {
            throw new RuntimeException("no github token found");
        }
        return decryptToken(user.getGithubTokenEncrypted());
    }

    private String encryptToken(String token) throws Exception {
        SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(token.getBytes()));
    }

    private String decryptToken(String encryptedToken) throws Exception {
        SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedValue = Base64.getDecoder().decode(encryptedToken);
        return new String(cipher.doFinal(decodedValue));
    }
}