package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.Exception.LeetCodeApiException;
import com.tracker.leetcode.tracker.Models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component // Tells Spring to manage this class as a reusable tool
public class LeetCodeApiClient {

    private final String LEETCODE_API_URL = "https://leetcode.com/graphql";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpHeaders headers;

    public LeetCodeApiClient() {
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
    }


    private JsonNode executeGraphQLQuery(String query, String username) {
        HttpEntity<String> request = new HttpEntity<>(query, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(LEETCODE_API_URL, request, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Network or JSON parsing error for user {}: {}", username, e.getMessage());
            throw new LeetCodeApiException("Failed to communicate with LeetCode servers for user: " + username);
        }
    }

    // 1. Fetch Calendar Heatmap Data
    public List<DailyProgress> fetchCalendarData(String username) {
        String query = """
                {"query":"query userProfileCalendar($username: String!) { matchedUser(username: $username) { userCalendar { submissionCalendar } } }","variables":{"username":"%s"}}
                """.formatted(username);

        JsonNode root = executeGraphQLQuery(query, username);
        JsonNode calendarNode = root.path("data").path("matchedUser").path("userCalendar").path("submissionCalendar");

        if (calendarNode.isMissingNode() || calendarNode.isNull()) {
            throw new LeetCodeApiException("LeetCode returned no calendar data for user: " + username);
        }

        try {
            Map<String, Integer> submissionMap = objectMapper.readValue(calendarNode.asString(), new TypeReference<>() {});
            List<DailyProgress> progressList = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : submissionMap.entrySet()) {
                LocalDate date = Instant.ofEpochSecond(Long.parseLong(entry.getKey())).atZone(ZoneId.systemDefault()).toLocalDate();
                progressList.add(new DailyProgress(date, entry.getValue()));
            }
            return progressList;
        } catch (Exception e) {
            log.error("Failed to map Calendar data for {}: {}", username, e.getMessage());
            throw new LeetCodeApiException("Error parsing LeetCode calendar data.");
        }
    }

    // 2. Fetch Problem Stats Data
    public List<ProblemStats> fetchProblemStats(String username) {
        String query = """
                {"query":"query userProblemsSolved($username: String!) { allQuestionsCount { difficulty count } matchedUser(username: $username) { problemsSolvedBeatsStats { difficulty percentage } submitStatsGlobal { acSubmissionNum { difficulty count } } } }","variables":{"username":"%s"}}
                """.formatted(username);

        JsonNode root = executeGraphQLQuery(query, username);
        JsonNode matchedUser = root.path("data").path("matchedUser");

        if (matchedUser.isMissingNode() || matchedUser.isNull()) {
            throw new LeetCodeApiException("LeetCode returned no stats data for user: " + username);
        }

        JsonNode submissionStats = matchedUser.path("submitStatsGlobal").path("acSubmissionNum");
        JsonNode beatsStats = matchedUser.path("problemsSolvedBeatsStats");
        List<ProblemStats> statsList = new ArrayList<>();

        if (submissionStats.isArray()) {
            for (JsonNode statNode : submissionStats) {
                String difficulty = statNode.path("difficulty").asString();
                int count = statNode.path("count").asInt();
                double percentage = 0.0;

                if (beatsStats.isArray()) {
                    for (JsonNode beatNode : beatsStats) {
                        if (beatNode.path("difficulty").asString().equals(difficulty) && !beatNode.path("percentage").isNull()) {
                            percentage = beatNode.path("percentage").asDouble();
                            break;
                        }
                    }
                }
                statsList.add(new ProblemStats(difficulty, count, percentage));
            }
        }
        return statsList;
    }

    // 3. Fetch Recent Submissions Data
    public List<RecentSubmission> fetchRecentSubmissions(String username, int limit) {
        String query = """
                {"query":"query recentAcSubmissions($username: String!, $limit: Int!) { recentAcSubmissionList(username: $username, limit: $limit) { id title titleSlug timestamp } }","variables":{"username":"%s","limit":%d}}
                """.formatted(username, limit);

        JsonNode root = executeGraphQLQuery(query, username);
        JsonNode submissionList = root.path("data").path("recentAcSubmissionList");

        if (submissionList.isMissingNode() || submissionList.isNull()) {
            throw new LeetCodeApiException("LeetCode returned no recent submissions for user: " + username);
        }

        List<RecentSubmission> recentList = new ArrayList<>();
        if (submissionList.isArray()) {
            for (JsonNode node : submissionList) {
                String title = node.path("title").asString();
                String titleSlug = node.path("titleSlug").asString();
                long timestamp = Long.parseLong(node.path("timestamp").asString());
                recentList.add(new RecentSubmission(title, titleSlug, timestamp));
            }
        }
        return recentList;
    }

    // 4. Fetch Extended Profile (Badges, Socials, Contests, Rank, About, AVATAR)
    public Student fetchExtendedProfileDetails(String username) {
        // ADDED 'userAvatar' TO THE GRAPHQL QUERY
        String query = """
                {"query":"query fullProfile($username: String!) { matchedUser(username: $username) { githubUrl twitterUrl linkedinUrl profile { ranking aboutMe userAvatar } badges { name icon creationDate } } userContestRanking(username: $username) { rating } userContestRankingHistory(username: $username) { attended rating ranking problemsSolved totalProblems contest { title startTime } } }","variables":{"username":"%s"}}
                """.formatted(username);

        JsonNode root = executeGraphQLQuery(query, username);
        JsonNode data = root.path("data");

        if (data.path("matchedUser").isNull() || data.path("matchedUser").isMissingNode()) {
            throw new LeetCodeApiException("LeetCode returned no extended profile data for user: " + username);
        }

        Student extendedData = new Student();
        JsonNode matchedUser = data.path("matchedUser");
        JsonNode profile = matchedUser.path("profile");

        // 1. Parse Central Fields, Avatar, & Social Media
        extendedData.setAbout(profile.path("aboutMe").asString(null));
        extendedData.setRank(profile.path("ranking").asString("Unranked"));
        extendedData.setAvatarUrl(profile.path("userAvatar").asString(null)); // <-- CAPTURE THE AVATAR!

        extendedData.setSocialMedia(new SocialMedia(
                matchedUser.path("githubUrl").asString(null),
                matchedUser.path("linkedinUrl").asString(null),
                matchedUser.path("twitterUrl").asString(null)
        ));

        // Parse Current Contest Rating
        JsonNode rankingNode = data.path("userContestRanking");
        if (!rankingNode.isNull() && !rankingNode.isMissingNode()) {
            extendedData.setCurrentContestRating(rankingNode.path("rating").asDouble(0.0));
        }

        // 2. Parse Badges
        List<Badge> badgeList = new ArrayList<>();
        JsonNode badgesNode = matchedUser.path("badges");
        if (badgesNode.isArray()) {
            for (JsonNode b : badgesNode) {
                badgeList.add(new Badge(
                        b.path("name").asString(),
                        b.path("icon").asString(),
                        b.path("creationDate").asString()
                ));
            }
        }
        extendedData.setBadges(badgeList);

        // 3. Parse Contest History
        List<ContestHistory> historyList = new ArrayList<>();
        JsonNode historyNode = data.path("userContestRankingHistory");
        if (historyNode.isArray()) {
            for (JsonNode c : historyNode) {
                if (c.path("attended").asBoolean()) {
                    JsonNode contestMeta = c.path("contest");
                    historyList.add(new ContestHistory(
                            contestMeta.path("title").asString(),
                            contestMeta.path("startTime").asLong(),
                            c.path("rating").asDouble(),
                            c.path("ranking").asInt(),
                            c.path("problemsSolved").asInt(),
                            c.path("totalProblems").asInt()
                    ));
                }
            }
        }
        extendedData.setContestHistory(historyList);

        return extendedData;
    }

    // 5. Verify Manual Submission URL (Bypassing Privacy Block)

    public boolean verifySubmission(String submissionId, String expectedUsername, String expectedTitleSlug) {

        // Instead of querying the protected submissionDetails, we query the public recentAcSubmissionList
        // We fetch their last 20 accepted submissions to see if the ID is in there.
        String query = """
                {"query":"query recentAcSubmissions($username: String!, $limit: Int!) { recentAcSubmissionList(username: $username, limit: $limit) { id titleSlug } }","variables":{"username":"%s","limit":20}}
                """.formatted(expectedUsername);

        JsonNode root = executeGraphQLQuery(query, expectedUsername);
        JsonNode submissionList = root.path("data").path("recentAcSubmissionList");

        if (submissionList.isMissingNode() || submissionList.isNull() || !submissionList.isArray()) {
            log.warn("Could not fetch recent submissions or list is empty for user: {}", expectedUsername);
            return false;
        }

        try {
            // Loop through their recent accepted submissions looking for a match
            for (JsonNode node : submissionList) {
                String actualId = node.path("id").asString();
                String actualSlug = node.path("titleSlug").asString();

                // If the ID matches AND the question matches, it's 100% valid!
                if (submissionId.equals(actualId) && expectedTitleSlug.equalsIgnoreCase(actualSlug)) {
                    log.info("Validation Successful -> Found ID: {} for Slug: {}", actualId, actualSlug);
                    return true;
                }
            }

            log.warn("Submission ID {} not found in the recent 20 Accepted submissions for {}.", submissionId, expectedUsername);
            return false;

        } catch (Exception e) {
            log.error("Failed to parse recent submissions array for validation: {}", e.getMessage());
            return false;
        }
    }

    // 6. Fetch Skill Stats (Topic Tags)
    public List<SkillStat> fetchSkillStats(String username) {
        String query = """
                {"query":"query skillStats($username: String!) { matchedUser(username: $username) { tagProblemCounts { advanced { tagName problemsSolved } intermediate { tagName problemsSolved } fundamental { tagName problemsSolved } } } }","variables":{"username":"%s"}}
                """.formatted(username);

        JsonNode root = executeGraphQLQuery(query, username);
        JsonNode tagCounts = root.path("data").path("matchedUser").path("tagProblemCounts");

        List<SkillStat> skills = new ArrayList<>();
        if (tagCounts.isMissingNode() || tagCounts.isNull()) {
            return skills;
        }

        // Helper to parse the 3 difficulty arrays
        String[] levels = {"fundamental", "intermediate", "advanced"};
        for (String level : levels) {
            JsonNode levelNode = tagCounts.path(level);
            if (levelNode.isArray()) {
                for (JsonNode tag : levelNode) {
                    skills.add(new SkillStat(
                            tag.path("tagName").asString(),
                            tag.path("problemsSolved").asInt()
                    ));
                }
            }
        }
        return skills;
    }
}