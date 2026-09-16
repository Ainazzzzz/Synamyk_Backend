package synamyk.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProfileResponse {
    private Long id;
    private String phone;
    private String firstName;
    private String lastName;
    private String bio;
    private String avatarUrl;
    private String language;
    private Long regionId;
    private String regionName;
    private Long districtId;
    private String districtName;
    private Long schoolId;
    private String schoolName;
    /** Personal invite code («Пригласи друга»), null until generated via GET /api/referrals/me. */
    private String referralCode;
    /** Game rating (Elo), 1000 for new players. */
    private Integer gameRating;

    /** Number of completed test sessions. */
    private long completedTests;
    /** Sum of correctAnswers across all completed sessions. */
    private long totalScore;
}