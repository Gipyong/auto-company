package com.autocompany.service;

import com.autocompany.entity.User;
import com.autocompany.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 로컬 단일 사용자(User) 확보.
 * 이 서비스는 로컬 단일 사용자 배포를 전제로 하므로 User는 항상 1명만 존재한다.
 * 이미 사용자가 있으면 그 사용자를 재사용하고, 없으면 요청된 id로 1명 생성한다.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreateLocalUser(UUID requestedId) {
        UUID id = (requestedId != null) ? requestedId : UUID.randomUUID();
        return userRepository.findById(id)
                .orElseGet(() -> userRepository.save(User.builder()
                        .id(id)
                        .email(id + "@autocompany.local")
                        .name("Local CEO")
                        .build()));
    }
}
