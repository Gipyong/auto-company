"""
Cost-Performance Model Router Engine

Implements the two-step selection system:
1. Hard filtering: exclude models below quality threshold for the task
2. Cost-efficiency optimization: select lowest-cost model that meets requirements
Plus escalation strategy when tasks fail.
"""
from .config import CONFIG
from .models import Task, TaskClassification, ModelEvaluation
from dataclasses import dataclass
from typing import Optional, Dict, Any, List
from datetime import datetime


@dataclass
class ExperimentRecord:
    """Record of a model experiment for history-based learning"""
    timestamp: str
    model_name: str
    task_type: str
    success: bool
    cost: float
    verification_passed: bool


class CostPerformanceRouter:
    """
    Model router using cost-performance optimization.
    
    Router's goal: deliver required quality at MINIMUM total cost,
    not just select the best model.
    """
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or CONFIG
        self.model_registry = self.config["models"]
        self.task_classifications = self.config["task_types"]
        self.experiment_history: List[ExperimentRecord] = []
        
    # ------------------------------------------------------------------
    # STEP 1: Task Classification
    # ------------------------------------------------------------------
    def classify_task(self, task: Task) -> TaskClassification:
        """Classify task based on type and requirements"""
        task_type = self.task_classifications.get(task.task_type,
                                                  self.task_classifications["feature"])
        
        complexity_map = {
            "simple_fix": 0.5,
            "feature": 0.8,
            "architecture": 0.9,
            "doc": 0.3
        }
        
        return TaskClassification(
            task_type=task.task_type,
            complexity_score=complexity_map.get(task.task_type, 0.7),
            quality_requirement=task_type["required_quality"],
            cost_budget=task_type["max_cost"],
            deterministic_possible=task_type["deterministic"],
            required_tools=[]
        )
    
    # ------------------------------------------------------------------
    # STEP 2: Quality Threshold Hard-Filter
    # ------------------------------------------------------------------
    def apply_quality_filter(self, classification: TaskClassification) -> List[str]:
        """
        Exclude models that cannot meet the task's quality requirement.
        
        Critical exception from design doc: even if a cheap model scores
        high on other factors, if it can't meet quality threshold, exclude it.
        """
        quality_threshold = classification.quality_requirement
        eligible_models = []
        
        for model_name, model_info in self.model_registry.items():
            if model_info["quality_score"] >= quality_threshold:
                eligible_models.append(model_name)
        
        return eligible_models
    
    # ------------------------------------------------------------------
    # STEP 3: Cost-Efficiency Scoring
    # ------------------------------------------------------------------
    def calculate_model_score(
        self, model: Dict[str, Any], 
        classification: TaskClassification
    ) -> float:
        """Calculate cost-performance score for a model"""
        # Quality factor (weight 0.4)
        quality_factor = model["quality_score"] * 0.4
        
        # Experience history factor (based on past success) (weight 0.2)
        history = self._get_model_history(model["name"], classification.task_type)
        experience_factor = history["success_rate"] * 0.2
        
        # Verification pass rate factor (weight 0.1)
        verification_factor = history["verification_rate"] * 0.1
        
        # Cost factor (negatively weighted) (weight 0.3)
        # Total cost includes tokens, retries, and verification calls
        cost_factor = min(model["cost_per_token"] * 10000, 1.0) * 0.3
        
        # Latency factor (weight 0.0 - minimized)
        latency_factor = 0.0
        
        score = (quality_factor + experience_factor + verification_factor 
                 - cost_factor - latency_factor)
        
        return max(0.0, min(score, 10.0))
    
    def select_best_model(self, classification: TaskClassification) -> Optional[str]:
        """
        Two-step selection:
        1. Filter out models that don't meet quality threshold
        2. Select cheapest model among remaining candidates
        """
        # Step 1: Quality filtering
        eligible_models = self.apply_quality_filter(classification)
        
        if not eligible_models:
            return None  # No model can satisfy the quality requirement
        
        # Step 2: Cost optimization - pick cheapest viable model
        best_score = -1
        best_model = None
        
        for model_name in eligible_models:
            score = self.calculate_model_score(self.model_registry[model_name],
                                               classification)
            if score > best_score:
                best_score = score
                best_model = model_name
        
        return best_model
    
    # ------------------------------------------------------------------
    # History & Learning (Experience-Based Weight Update)
    # ------------------------------------------------------------------
    def _get_model_history(self, model_name: str, task_type: str) -> Dict[str, Any]:
        """Get historical performance for model on similar tasks"""
        past = [e for e in self.experiment_history 
                if e.model_name == model_name and e.task_type == task_type]
        
        if not past:
            return {
                "success_rate": 0.5,
                "verification_rate": 0.5,
                "avg_cost": 0.0,
                "total_experiments": 0
            }
        
        success_count = sum(1 for e in past if e.success)
        verification_count = sum(1 for e in past if e.verification_passed)
        
        return {
            "success_rate": success_count / len(past),
            "verification_rate": verification_count / len(past),
            "avg_cost": sum(e.cost for e in past) / len(past),
            "total_experiments": len(past)
        }
    
    def log_experiment(self, model_name: str, task_type: str, success: bool,
                       cost: float, verification_passed: bool = False):
        """Log experiment results for future model selection"""
        record = ExperimentRecord(
            timestamp=datetime.now().isoformat(),
            model_name=model_name,
            task_type=task_type,
            success=success,
            cost=cost,
            verification_passed=verification_passed
        )
        self.experiment_history.append(record)
        
        # Keep only last 1000 experiments for performance
        if len(self.experiment_history) > 1000:
            self.experiment_history = self.experiment_history[-1000:]
    
    # ------------------------------------------------------------------
    # Escalation Strategy (Step-By-Step Model Promotion)
    # ------------------------------------------------------------------
    def get_escalation_model(self, current_model: str, task_type: str) -> Optional[str]:
        """
        Find a better (more expensive) model for task re-execution
        when the current model fails.
        """
        models = list(self.model_registry.keys())
        current_index = models.index(current_model) if current_model in models else -1
        
        if current_index < 0:
            return None
        
        # Find next better model
        next_index = current_index + 1
        if next_index < len(models):
            next_model = models[next_index]
            test_classification = self.classify_task(Task(
                id="escalation", project_id="test", assignee_id="test",
                task_type=task_type, title="Escalation",
                prompt="test", status="pending"
            ))
            
            # Only escalate if the next model can meet quality requirements
            if self.calculate_model_score(
                self.model_registry[next_model], test_classification) > 0:
                return next_model
        
        return None