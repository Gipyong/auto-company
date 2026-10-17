package com.autocompany.model;

import java.util.List;

/**
 * 모든 provider가 구현하는 공통 어댑터 인터페이스 (설계 §9.1)
 *
 * 도메인 코드가 특정 벤더 SDK를 직접 호출하지 않도록 한다.
 * 구현체는 connect/read/overall timeout과 제한된 재시도 정책을 가져야 한다.
 */
public interface ModelAdapter {

    /** provider가 제공하는 모델 목록 */
    List<ModelDescriptor> listModels();

    /** provider 상태 확인 (health check) */
    ProviderHealth healthCheck();

    /** 모델 호출 */
    ModelResponse generate(ModelRequest request);

    /** 호출 전 예상 비용 추정 (추정값과 실제값을 구분, §16) */
    CostEstimate estimateCost(ModelRequest request);
}
