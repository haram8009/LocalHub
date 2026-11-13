package com.enjoytrip;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
/** DAO adapter; every persistence operation is implemented by MyBatis XML. */
@Repository
@Transactional(readOnly=true)
public class MemberRepository {
 private final MemberMapper mapper;
 public MemberRepository(MemberMapper mapper) { this.mapper=mapper; }
 public Optional<Member> findById(String id) { return mapper.findById(id); }
 public Optional<Member> findLockedById(String id) { return mapper.findLockedById(id); }
 public Optional<Member> findByNameAndEmail(String name,String email) { return mapper.findByNameAndEmail(name,email); }
 public Optional<Member> findByIdAndEmail(String id,String email) { return mapper.findByIdAndEmail(id,email); }
 public boolean existsById(String id) { return mapper.existsById(id); }
 public boolean existsByEmail(String email) { return mapper.existsByEmail(email); }
 public long count() { return mapper.count(); }
 @Transactional public Member insert(Member member) { mapper.insert(member); return member; }
 @Transactional public Member saveAndFlush(Member member) {
   if(mapper.existsById(member.id)) mapper.update(member); else mapper.insert(member);
   return member;
 }
 @Transactional public void update(Member member) { mapper.update(member); }
 @Transactional public void delete(Member member) { mapper.delete(member.id); }
 @Transactional public void deleteAll() { mapper.deleteAll(); }
}
