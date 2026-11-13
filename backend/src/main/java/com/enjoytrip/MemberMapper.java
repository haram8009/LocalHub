package com.enjoytrip;
import org.apache.ibatis.annotations.*;
import java.util.Optional;
@Mapper
public interface MemberMapper {
 Optional<Member> findById(@Param("id") String id);
 Optional<Member> findLockedById(@Param("id") String id);
 Optional<Member> findByNameAndEmail(@Param("name") String name,@Param("email") String email);
 Optional<Member> findByIdAndEmail(@Param("id") String id,@Param("email") String email);
 boolean existsById(@Param("id") String id);
 boolean existsByEmail(@Param("email") String email);
 int insert(Member member);
 int update(Member member);
 int delete(@Param("id") String id);
 int deleteAll();
 long count();
}
