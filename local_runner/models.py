"""
Data Models for AutoCompany Local Runner
"""
from dataclasses import dataclass, field
from typing import Optional, Dict, List, Any
from datetime import datetime
from enum import Enum

class TaskType(str, Enum):
    SIMPLE_FIX = "simple_fix"
    FEATURE = "feature"
    ARCHITECTURE = "architecture"
    DOCUMENTATION = "doc"

class TaskStatus(str, Enum):
    PENDING = "pending"
    ASSIGNED = "assigned"
    RUNNING = "running"
    VERIFICATION = "verification"
    COMPLETED = "completed"
    FAILED = "failed"
    ESCALATED = "escalated"

@dataclass
class Employee:
    """AI Worker (Employee) in the company"""
    id: str
    name: str
    role: str
    ai_model: str
    docker_container_id: Optional[str] = None
    status: str = "idle"
    tasks_completed: int = 0
    avg_rating: float = 0.0
    level: int = 1
    system_prompt: str = ""
    repo_path: Optional[str] = None
    branch_name: Optional[str] = None
    
    def to_dict(self):
        return {
            "id": self.id,
            "name": self.name,
            "role": self.role,
            "ai_model": self.ai_model,
            "docker_container_id": self.docker_container_id,
            "status": self.status,
            "tasks_completed": self.tasks_completed,
            "avg_rating": self.avg_rating,
            "level": self.level,
            "system_prompt": self.system_prompt,
            "repo_path": self.repo_path,
            "branch_name": self.branch_name
        }

@dataclass
class Task:
    """Work unit assigned to an employee"""
    id: str
    project_id: str
    assignee_id: str
    task_type: str
    title: str
    prompt: str
    status: str
    created_at: datetime = field(default_factory=datetime.now)
    deadline: Optional[datetime] = None
    priority: int = 1
    verification_results: Optional[Dict[str, Any]] = None
    output: Optional[str] = None
    docker_logs: Optional[str] = None
    estimated_cost: float = 0.0
    total_cost: float = 0.0
    escalation_count: int = 0
    success: bool = False
    
    def to_dict(self):
        return {
            "id": self.id,
            "project_id": self.project_id,
            "assignee_id": self.assignee_id,
            "task_type": self.task_type,
            "title": self.title,
            "prompt": self.prompt,
            "status": self.status,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "deadline": self.deadline.isoformat() if self.deadline else None,
            "priority": self.priority,
            "verification_results": self.verification_results,
            "output": self.output,
            "docker_logs": self.docker_logs,
            "estimated_cost": self.estimated_cost,
            "total_cost": self.total_cost,
            "escalation_count": self.escalation_count,
            "success": self.success
        }

@dataclass
class TaskClassification:
    """Task classification for model selection"""
    task_type: str
    complexity_score: float
    quality_requirement: float
    cost_budget: float
    deterministic_possible: bool
    required_tools: List[str] = field(default_factory=list)

@dataclass
class ModelEvaluation:
    """Model evaluation score for task"""
    model_name: str
    quality_score: float
    estimated_cost: float
    latency: float
    success_rate: float
    verification_pass_rate: float