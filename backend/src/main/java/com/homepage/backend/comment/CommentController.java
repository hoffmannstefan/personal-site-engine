package com.homepage.backend.comment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class CommentController {

    private final CommentService service;

    CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping("/posts/{slug}/comments")
    List<CommentDto.View> list(@PathVariable String slug) {
        return service.list(slug);
    }

    @PostMapping(value = "/posts/{slug}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CommentDto.View create(@PathVariable String slug, @Valid @RequestBody CommentDto.Create request,
                           HttpServletRequest http) {
        return service.create(slug, request, http.getRemoteAddr());
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        service.delete(id, token);
    }
}
