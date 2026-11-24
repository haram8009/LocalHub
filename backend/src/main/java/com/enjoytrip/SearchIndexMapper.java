package com.enjoytrip;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface SearchIndexMapper {
 List<PostSearchIndexRepository.Posting> findContaining(@Param("patterns") List<String> patterns);
 int add(PostSearchTerm term);
 int addTerm(PostSearchTerm term);
 int syncTerms();
 int remove(@Param("id") long id);
 int removeByAuthor(@Param("id") String id);
 int clear();
 int clearTerms();
 String version();
 int deleteState();
 int insertState(@Param("version") String version);
}
