package com.tracker.leetcode.tracker.Service;

import com.tracker.leetcode.tracker.Exception.LeetCodeApiException;
import com.tracker.leetcode.tracker.Models.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LeetCodeApiClient {

    private static final String LEETCODE_API_URL = "https://leetcode.com/graphql";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpHeaders headers;
    private final RedisTemplate<String, Object> redisTemplate;

    public LeetCodeApiClient(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode executeGraphQLQuery(String query, String username) {
        try {
            HttpEntity<String> request = new HttpEntity<>(query, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(LEETCODE_API_URL, request, String.class);

            if (response.getBody() == null) {
                throw new LeetCodeApiException("Empty response from LeetCode API for user: " + username);
            }

            return objectMapper.readTree(response.getBody());
        } catch (LeetCodeApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Network or JSON parsing error for user {}: {}", username, e.getMessage());
            throw new LeetCodeApiException("Failed to communicate with LeetCode servers for user: " + username);
        }
    }

    /**
     * Save data to Redis cache for fallback use when circuit breaker opens
     * TTL: 7 days to preserve stale data
     */
    private void cacheDataForFallback(String key, Object data) {
        try {
            redisTemplate.opsForValue().set(key, data, 7, TimeUnit.DAYS);
            log.debug("Data cached in Redis for fallback: {}", key);
        } catch (Exception e) {
            log.warn("Failed to cache data for fallback: {}", e.getMessage());
        }
    }

    /**
     * Retrieve fallback data from Redis cache
     * Safely casts the object using the provided Class type to avoid unchecked warnings
     */
    private <T> T getFallbackDataFromCache(String key, Class<T> type) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (type.isInstance(cached)) {
                log.info("Using fallback data from Redis cache: {}", key);
                return type.cast(cached);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve fallback data from cache: {}", e.getMessage());
        }
        return null;
    }

    // 1. THE MASTER SYNC METHOD (Replaces 5 separate calls!)
    @CircuitBreaker(name = "leetcodeApi", fallbackMethod = "fetchCompleteProfileFallback")
    @Retry(name = "leetcodeApi")
    @RateLimiter(name = "leetcodeApi")
    public Student fetchCompleteProfile(String username) {
        String query = """
                {"query":"query fullProfileSync($username: String!, $limit: Int!) { allQuestionsCount { difficulty count } matchedUser(username: $username) { githubUrl twitterUrl linkedinUrl profile { ranking aboutMe userAvatar } badges { name icon creationDate } userCalendar { submissionCalendar } problemsSolvedBeatsStats { difficulty percentage } submitStatsGlobal { acSubmissionNum { difficulty count } } tagProblemCounts { advanced { tagName problemsSolved } intermediate { tagName problemsSolved } fundamental { tagName problemsSolved } } } userContestRanking(username: $username) { rating } userContestRankingHistory(username: $username) { attended rating ranking problemsSolved totalProblems contest { title startTime } } recentAcSubmissionList(username: $username, limit: $limit) { id title titleSlug timestamp } }","variables":{"username":"%s","limit":20}}
                """.formatted(username);

        JsonNode root = executeGraphQLQuery(query, username);
        JsonNode data = root.path("data");
        JsonNode matchedUser = data.path("matchedUser");

        if (matchedUser.isMissingNode() || matchedUser.isNull()) {
            throw new LeetCodeApiException("LeetCode returned no data for user: " + username);
        }

        Student student = new Student();
        JsonNode profile = matchedUser.path("profile");

        // --- 1. Basic Profile & Socials ---
        student.setAbout(profile.path("aboutMe").asString(null));
        student.setRank(profile.path("ranking").asString("Unranked"));
        student.setAvatarUrl(profile.path("userAvatar").asString(null));
        student.setSocialMedia(new SocialMedia(
                matchedUser.path("githubUrl").asString(null),
                matchedUser.path("linkedinUrl").asString(null),
                matchedUser.path("twitterUrl").asString(null)
        ));

        JsonNode rankingNode = data.path("userContestRanking");
        if (!rankingNode.isNull() && !rankingNode.isMissingNode()) {
            student.setCurrentContestRating(rankingNode.path("rating").asDouble(0.0));
        }

        // --- 2. Calendar / Heatmap ---
        try {
            JsonNode calendarNode = matchedUser.path("userCalendar").path("submissionCalendar");
            if (!calendarNode.isMissingNode() && !calendarNode.isNull()) {
                Map<String, Integer> submissionMap = objectMapper.readValue(calendarNode.asString(), new TypeReference<>() {});
                List<DailyProgress> progressList = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : submissionMap.entrySet()) {
                    LocalDate date = Instant.ofEpochSecond(Long.parseLong(entry.getKey())).atZone(ZoneId.systemDefault()).toLocalDate();
                    progressList.add(new DailyProgress(date, entry.getValue()));
                }
                student.setProgressHistory(progressList);
            }
        } catch (Exception e) { log.warn("Failed to parse calendar for {}", username); }

        // --- 3. Problem Stats ---
        JsonNode submissionStats = matchedUser.path("submitStatsGlobal").path("acSubmissionNum");
        JsonNode beatsStats = matchedUser.path("problemsSolvedBeatsStats");
        List<ProblemStats> statsList = new ArrayList<>();
        if (submissionStats.isArray()) {
            for (JsonNode statNode : submissionStats) {
                String diff = statNode.path("difficulty").asString();
                int count = statNode.path("count").asInt();
                double pct = 0.0;
                if (beatsStats.isArray()) {
                    for (JsonNode beatNode : beatsStats) {
                        if (beatNode.path("difficulty").asString().equals(diff) && !beatNode.path("percentage").isNull()) {
                            pct = beatNode.path("percentage").asDouble();
                            break;
                        }
                    }
                }
                statsList.add(new ProblemStats(diff, count, pct));
            }
        }
        student.setProblemStats(statsList);

        // --- 4. Recent Submissions ---
        JsonNode subList = data.path("recentAcSubmissionList");
        List<RecentSubmission> recentList = new ArrayList<>();
        if (subList.isArray()) {
            for (JsonNode node : subList) {
                try {
                    recentList.add(new RecentSubmission(
                            node.path("title").asString(),
                            node.path("titleSlug").asString(),
                            Long.parseLong(node.path("timestamp").asString())
                    ));
                } catch (Exception ignored) {}
            }
        }
        student.setRecentSubmissions(recentList);

        // --- 5. Skills (Topics) ---
        List<SkillStat> skills = new ArrayList<>();
        JsonNode tagCounts = matchedUser.path("tagProblemCounts");
        if (!tagCounts.isMissingNode() && !tagCounts.isNull()) {
            for (String level : new String[]{"fundamental", "intermediate", "advanced"}) {
                JsonNode levelNode = tagCounts.path(level);
                if (levelNode.isArray()) {
                    for (JsonNode tag : levelNode) {
                        skills.add(new SkillStat(tag.path("tagName").asString(), tag.path("problemsSolved").asInt()));
                    }
                }
            }
        }
        student.setSkills(skills);

        // --- 6. Badges & Contests ---
        List<Badge> badgeList = new ArrayList<>();
        JsonNode badgesNode = matchedUser.path("badges");
        if (badgesNode.isArray()) {
            for (JsonNode b : badgesNode) {
                badgeList.add(new Badge(b.path("name").asString(), b.path("icon").asString(), b.path("creationDate").asString()));
            }
        }
        student.setBadges(badgeList);

        List<ContestHistory> historyList = new ArrayList<>();
        JsonNode historyNode = data.path("userContestRankingHistory");
        if (historyNode.isArray()) {
            for (JsonNode c : historyNode) {
                if (c.path("attended").asBoolean()) {
                    JsonNode meta = c.path("contest");
                    historyList.add(new ContestHistory(meta.path("title").asString(), meta.path("startTime").asLong(), c.path("rating").asDouble(), c.path("ranking").asInt(), c.path("problemsSolved").asInt(), c.path("totalProblems").asInt()));
                }
            }
        }
        student.setContestHistory(historyList);

        cacheDataForFallback("LeetCode:master:" + username, student);
        return student;
    }

    @SuppressWarnings("unused") // Called via AOP by Resilience4j
    public Student fetchCompleteProfileFallback(String username, Exception ex) {
        log.warn("Circuit breaker OPEN for {}. Serving stale master data. Error: {}", username, ex.getMessage());
        Student cached = getFallbackDataFromCache("LeetCode:master:" + username, Student.class);
        if (cached != null) return cached;
        throw new LeetCodeApiException("API unavailable and no cached data for: " + username);
    }

    // 2. Verify Manual Submission URL (Bypassing Privacy Block)
    @CircuitBreaker(name = "leetcodeApi", fallbackMethod = "verifySubmissionFallback")
    @Retry(name = "leetcodeApi")
    @RateLimiter(name = "leetcodeApi")
    public boolean verifySubmission(String submissionId, String expectedUsername, String expectedTitleSlug) {

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
            for (JsonNode node : submissionList) {
                String actualId = node.path("id").asString();
                String actualSlug = node.path("titleSlug").asString();

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

    @SuppressWarnings("unused") // Called via AOP by Resilience4j
    public boolean verifySubmissionFallback(String submissionId, String expectedUsername, String expectedTitleSlug, Exception ex) {
        log.warn("Circuit breaker OPEN for submission verification of {}. Denying verification during outage. Error: {}",
                expectedUsername, ex.getMessage());
        return false;
    }
}