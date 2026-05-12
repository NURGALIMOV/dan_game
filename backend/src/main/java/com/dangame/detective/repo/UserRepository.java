package com.dangame.detective.repo;

import com.dangame.detective.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByToken(String token);

    Optional<UserEntity> findByVerificationToken(String verificationToken);

    List<UserEntity> findTop10ByOrderByCasesSolvedDesc();
}
