package com.yourname.streamci.streamci.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitHubRepo {
    private String name;

    @JsonProperty("owner")
    private Owner owner;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("created_at")
    private String createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Owner {
        private String login;
    }
}