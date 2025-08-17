package com.yourname.streamci.streamci.model;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeadCommit {
    private String id;
    private Author author;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Author {
        private String name;
        private String email;
    }
}