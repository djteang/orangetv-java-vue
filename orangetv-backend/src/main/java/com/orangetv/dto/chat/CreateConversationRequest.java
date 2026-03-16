package com.orangetv.dto.chat;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateConversationRequest {

    @NotEmpty(message = "participants不能为空")
    private List<String> participants;

    private String type = "private";

    private String name;
}
