package com.autocompany.repository;

import com.autocompany.entity.PeerRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PeerRatingRepository extends JpaRepository<PeerRating, UUID> {
    List<PeerRating> findByRatedEmployeeId(UUID ratedEmployeeId);
    List<PeerRating> findByRatedEmployeeIdOrderByCreatedAtDesc(UUID ratedEmployeeId);
    List<PeerRating> findByTaskId(UUID taskId);
    List<PeerRating> findByRaterEmployeeId(UUID raterEmployeeId);
}
