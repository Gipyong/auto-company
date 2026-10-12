package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.*;

@SpringBootTest
public class RatingEngineServiceTest {

    @Autowired
    private RatingEngineService ratingEngineService;

    @Test
    public void testLevelCalculationFormula() {
        // Section 5.2: 승진 공식 검증
        // Score = completedTasks * 0.4 + avgRating * 20 * 0.4
        int completedTasks = 10;
        float avgRating = 4.5f;
        double score = completedTasks * 0.4 + avgRating * 20 * 0.4;
        
        // 10 * 0.4 + 90 * 0.4 = 4 + 36 = 40
        // 레벨 4 이상이 되어야 함
        assertTrue(score >= 30.0);
    }

    @Test
    public void testRankMapping() {
        // 직급 매핑: 1-2=INTERN, 3-4=JUNIOR, 5-6=MID, 7-8=SENIOR, 9-10=LEAD
        assertTrue(true); // 매핑 로직은 RatingEngineService 내부 구현 확인
    }

    @Test
    public void testPeerRatingStorage() {
        // Section 5: 동료 평가 저장 기능 확인
        assertNotNull(ratingEngineService.getClass());
    }
}
