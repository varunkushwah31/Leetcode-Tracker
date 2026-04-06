package com.tracker.leetcode.tracker.Repository;

import com.tracker.leetcode.tracker.Models.Classroom;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClassroomRepository extends MongoRepository<Classroom,String > {
    List<Classroom> findByMentorId(String mentorId);
    List<Classroom> findByStudentIdsContaining(String studentId);
}
