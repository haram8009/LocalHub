package com.enjoytrip;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.Optional;
@Mapper
public interface TokenMapper {
 int insertRefresh(@Param("hash") String hash,@Param("memberId") String memberId,@Param("expiresAt") Instant expiresAt);
 Optional<RefreshRow> findRefreshForUpdate(@Param("hash") String hash);
 int deleteRefresh(@Param("hash") String hash);
 int deleteRefreshByMember(@Param("memberId") String memberId);
 int deleteExpiredRefresh(@Param("now") Instant now);
 int deleteAllRefresh();
 int upsertReset(@Param("memberId") String memberId,@Param("code") String code,@Param("issuedAt") Instant issuedAt);
 Optional<PasswordReset> findResetForUpdate(@Param("memberId") String memberId);
 Optional<PasswordReset> findReset(@Param("memberId") String memberId);
 int increaseResetAttempts(@Param("memberId") String memberId);
 int deleteReset(@Param("memberId") String memberId);

 class RefreshRow { String memberId; Instant expiresAt; public RefreshRow() {} }
 class PasswordReset { String memberId; String code; Instant issuedAt; int attempts; public PasswordReset() {} }
}
