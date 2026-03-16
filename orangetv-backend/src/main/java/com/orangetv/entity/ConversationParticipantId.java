package com.orangetv.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipantId implements Serializable {
    private String conversation;
    private Long user;
}
