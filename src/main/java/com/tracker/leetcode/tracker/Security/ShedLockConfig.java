package com.tracker.leetcode.tracker.Security;

import com.mongodb.client.MongoClient;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// defaultLockAtMostFor is a failsafe. If a server crashes while holding the lock,
// the lock will automatically release after 10 minutes so it isn't stuck forever.
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(MongoClient mongoClient) {
        return new MongoLockProvider(mongoClient.getDatabase("mongodb://localhost:27017/LeetcodeTracker"));
        // NOTE: Replace "leetcode_tracker" with your actual MongoDB database name!
    }
}