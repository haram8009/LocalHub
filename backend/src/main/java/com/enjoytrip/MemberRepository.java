package com.enjoytrip;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findLockedById(@Param("id") String id);
    boolean existsByEmail(String email);
    Optional<Member> findByNameAndEmail(String name, String email);
    Optional<Member> findByIdAndEmail(String id, String email);
}
