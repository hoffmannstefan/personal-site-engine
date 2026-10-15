package com.homepage.comment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostSlugOrderByCreatedAtAscIdAsc(String postSlug);

    void deleteByParentId(Long parentId);

    @Query("select c.postSlug as postSlug, count(c) as total from Comment c group by c.postSlug")
    List<CommentCount> countByPost();

    interface CommentCount {
        String getPostSlug();

        long getTotal();
    }
}
