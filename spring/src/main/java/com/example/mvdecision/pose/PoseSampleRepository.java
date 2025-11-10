package com.example.mvdecision.pose;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PoseSampleRepository extends JpaRepository<PoseSample, Long> {
    // 検索API実装時に、ここにメソッドを足してもOK
}
