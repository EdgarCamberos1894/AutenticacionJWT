package com.example.demo.repository;


import com.example.demo.model.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Query("SELECT up FROM UserProfile up JOIN up.user u WHERE u.email = :email")
    Optional<UserProfile> findByUserEmail(String email);
}
