package com.soukly.user.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** @param email normalized email (trimmed, lower case) */
    Optional<UserAccount> findByEmail(String email);
}
