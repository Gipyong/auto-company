package com.autocompany.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DockerManagerService {
    public static class ExecutionResult {
        public String output;
        public String logs;
        public String verification;
        public float estimatedCost;
        public boolean success;
        public boolean verificationPassed;
    }
}
