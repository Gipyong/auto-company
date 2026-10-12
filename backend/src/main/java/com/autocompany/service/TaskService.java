package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final PeerRatingRepository ratingRepository;
    private final DiscussionMessageRepository messageRepository;
    private final CostPerformanceRouterService routerService;
    private final DockerManagerService dockerManagerService;
    private final RatingEngineService ratingEngineService;
}
