package com.socialsea.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.socialsea.model.Post;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicReelDto extends PublicFeedDto {

    private PublicReelDto(PublicFeedDto base) {
        super(base);
    }

    public static PublicReelDto fromPost(Post post) {
        return new PublicReelDto(PublicFeedDto.fromPost(post));
    }
}
