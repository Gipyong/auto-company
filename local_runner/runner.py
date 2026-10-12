"""
Local Runner Engine for AI Workers in Docker Containers

Manages AI employee lifecycle, task assignment, and Docker-based
execution with the Cost-Performance Router for model selection.
"""
import subprocess
import time
import uuid
from typing import Dict, Optional, Any
from datetime import datetime

from .config import CONFIG
from .models import Employee, Task, TaskClassification
from .router import CostPerformanceRouter


class LocalRunner:
    """Local runner for AI workers in Docker containers"""
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or CONFIG
        self.router = CostPerformanceRouter(self.config)
        self.employees: Dict[str, Employee] = {}
        self.tasks: Dict[str, Task] = {}
        self.running_containers: Dict[str, subprocess.Popen] = {}
        
    # ------------------------------------------------------------------
    # Employee (AI Worker) Management
    # ------------------------------------------------------------------
    def setup_workers(self):
        """Setup initial AI workers with appropriate default models"""
        worker_configs = [
            {"name": "김개발", "role": "developer", "level": 3, "default_model": "llama3_local"},
            {"name": "이기획", "role": "planner", "level": 2, "default_model": "llama3_local"},
            {"name": "박문서", "role": "technical_writer", "level": 1, "default_model": "llama3_local"},
            {"name": "최디자이너", "role": "designer", "level": 2, "default_model": "gpt4o_mini"}
        ]
        
        for wc in worker_configs:
            employee = Employee(
                id=str(uuid.uuid4()),
                name=wc["name"],
                role=wc["role"],
                ai_model=wc["default_model"],
                status="idle",
                level=wc["level"]
            )
            self.employees[employee.id] = employee
            print(f"👥 Worker created: {employee.name} ({employee.role}) - Model: {employee.ai_model}")
        
        return list(self.employees.values())
    
    def get_worker(self, employee_id: str) -> Optional[Employee]:
        """Get an employee by ID"""
        return self.employees.get(employee_id)
    
    def list_workers(self) -> list:
        """List all workers and status"""
        return [emp.to_dict() for emp in self.employees.values()]
    
    # ------------------------------------------------------------------
    # Task Execution Engine
    # ------------------------------------------------------------------
    def create_task(self, project_id: str, assignee_id: str, task_type: str,
                    title: str, prompt: str, priority: int = 1) -> Task:
        """Create a new task and add to queue"""
        task = Task(
            id=str(uuid.uuid4()),
            project_id=project_id,
            assignee_id=assignee_id,
            task_type=task_type,
            title=title,
            prompt=prompt,
            status="pending",
            priority=priority
        )
        self.tasks[task.id] = task
        print(f"📝 Task created: '{title}' Type: {task_type}")
        return task
    
    def execute_task(self, task: Task) -> Task:
        """Execute a task using the cost-performance router"""
        print(f"\n🚀 Starting Task: {task.title}")
        print(f"   Type: {task.task_type}, Priority: {task.priority}")
        
        # Get employee and classify task
        employee = self.get_worker(task.assignee_id)
        if not employee:
            task.status = "failed"
            task.output = "Assigned employee not found"
            return task
        
        classification = self.router.classify_task(task)
        
        # Select best model using the cost-performance router
        selected_model = self.router.select_best_model(classification)
        
        if not selected_model:
            task.status = "failed"
            task.output = f"No model can satisfy quality requirement ({classification.quality_requirement})"
            print(f"   ❌ No eligible model found")
            return task
        
        model_info = self.config["models"][selected_model]
        print(f"   🔍 Selected Model: {selected_model}")
        print(f"   Quality: {model_info['quality_score']} | Cost: ${model_info['cost_per_token']}/token | Type: {model_info['type']}")
        
        # Update employee state
        employee.ai_model = selected_model
        employee.status = "working"
        
        # Execute in Docker container
        try:
            result = self._execute_in_docker(employee, task, classification, model_info)
            
            task.output = result["output"]
            task.docker_logs = result["logs"]
            task.verification_results = result["verification"]
            task.estimated_cost = result["estimated_cost"]
            task.total_cost = result["estimated_cost"]
            task.success = result["success"]
            
            # Log to history for future model selection
            self.router.log_experiment(
                model_name=model_info["name"],
                task_type=task.task_type,
                success=task.success,
                cost=task.total_cost,
                verification_passed=bool(result["verification"].get("passed", False))
            )
            
            if task.success:
                task.status = "completed"
                employee.tasks_completed += 1
                print(f"   ✅ Task completed successfully!")
            else:
                task.status = "failed"
                print(f"   ❌ Task failed - initiating escalation")
                # Attempt escalation
                task = self._handle_escalation(employee, task, classification)
                    
        except Exception as e:
            print(f"   ⚠️ Task execution error: {str(e)}")
            task.status = "failed"
            task.output = f"Execution error: {str(e)}"
        
        employee.status = "idle"
        return task
    
    def _execute_in_docker(self, employee: Employee, task: Task,
                           classification: TaskClassification,
                           model_info: Dict[str, Any]) -> Dict[str, Any]:
        """Execute task inside Docker container with selected AI model"""
        container_name = f"autocompany-worker-{employee.id[:8]}"
        employee.docker_container_id = container_name
        
        print(f"   🐳 Docker container: {container_name}")
        
        # Simulate processing (actual implementation would use Docker SDK)
        processing_time = 0.3 + (classification.complexity_score * 1.5)
        if model_info["type"] == "local":
            processing_time *= 0.7  # Local model is faster
        else:
            processing_time *= 0.9
        
        time.sleep(processing_time)
        
        # Generate output based on model and task
        output = self._generate_output(task, model_info)
        
        # Deterministic verification simulation
        verification_passed = self._simulate_verification(
            task, output, classification
        )
        
        # Cost calculation (simulated)
        estimated_cost = self._calculate_cost(task, model_info)
        
        return {
            "output": output,
            "logs": (f"Processing time: {processing_time:.2f}s | Model: {model_info['name']} "
                     f"| Docker: {container_name}"),
            "verification": {
                "passed": verification_passed,
                "deterministic": classification.deterministic_possible,
                "method": "unit_test" if classification.deterministic_possible else "peer_review"
            },
            "estimated_cost": estimated_cost,
            "success": verification_passed
        }
    
    def _handle_escalation(self, employee: Employee, task: Task,
                           classification: TaskClassification) -> Task:
        """
        Step 4: Escalation - when low-cost model fails, retry with higher-tier model
        """
        current_model_key = self._get_model_key_by_name(employee.ai_model)
        
        next_model_key = self.router.get_escalation_model(current_model_key, task.task_type)
        
        if next_model_key and next_model_key in self.config["models"]:
            next_model = self.config["models"][next_model_key]
            task.escalation_count += 1
            task.status = "escalated"
            
            print(f"   🆙 Escalating to higher-tier model: {next_model_key}")
            print(f"   New Quality: {next_model['quality_score']} | Cost: ${next_model['cost_per_token']}/token")
            
            employee.ai_model = next_model["name"]
            employee.status = "working"
            
            try:
                result = self._execute_in_docker(employee, task, classification, next_model)
                task.output = result["output"]
                task.docker_logs = result.get("logs", "")
                task.verification_results = result["verification"]
                task.total_cost += result["estimated_cost"]
                task.estimated_cost = result["estimated_cost"]
                task.success = result["success"]
                
                if task.success:
                    task.status = "completed"
                    employee.tasks_completed += 1
                    print(f"   ✅ Task completed successfully after escalation!")
                else:
                    task.status = "failed"
                    print(f"   ❌ Task failed even after escalation")
                
            except KeyError:
                task.status = "failed"
                task.output = "Escalation model not found"
        else:
            task.status = "failed"
            if not task.output:
                task.output = "Task failed and no higher-tier model available for escalation"
        
        employee.status = "idle"
        return task
    
    # ------------------------------------------------------------------
    # Utility Methods
    # ------------------------------------------------------------------
    def _generate_output(self, task: Task, model_info: Dict[str, Any]) -> str:
        """Generate simulated output based on model and task"""
        header = f"// Generated by {model_info['name']} ({model_info['type']})\n"
        header += f"// Task ID: {task.id}\n"
        header += f"// Assigned to: {self.get_worker(task.assignee_id).name if self.get_worker(task.assignee_id) else 'Unknown'}\n\n"
        
        if task.task_type == "simple_fix":
            return header + self._generate_fix_output(task)
        elif task.task_type == "feature":
            return header + self._generate_feature_output(task)
        elif task.task_type == "architecture":
            return header + self._generate_architecture_output(task)
        elif task.task_type == "doc":
            return header + self._generate_doc_output(task)
        else:
            return header + f"// Output for: {task.title}\n{model_info['name']} says: {task.prompt}"
    
    def _generate_fix_output(self, task: Task) -> str:
        return f"# Bug Fix: {task.title}\n" + \
               "def fixed_function():\n    # Fixed implementation\n    return True\n"
    
    def _generate_feature_output(self, task: Task) -> str:
        return f"// Feature: {task.title}\n" + \
               "// TODO: Implement feature logic\n" + \
               "pass\n"
    
    def _generate_architecture_output(self, task: Task) -> str:
        return f"// Architecture Design: {task.title}\n" + \
               "// System components and interfaces\n" + \
               "class SystemArchitecture:\n    pass\n"
    
    def _generate_doc_output(self, task: Task) -> str:
        return f"# Documentation: {task.title}\n\n" + \
               f"**Description**: {task.prompt}\n\n" + \
               "## Summary\nDocumentation generated successfully.\n"
    
    def _simulate_verification(self, task: Task, output: str,
                               classification: TaskClassification) -> bool:
        """
        Simulate deterministic verification or peer review.
        In Phase 2, this will run actual unit tests in Docker containers.
        """
        # Simulate success rate based on model quality and task complexity
        # Higher quality model = more likely to pass
        quality_factor = 0.5  # Base chance
        
        # Get model quality from config
        model_key = self._get_model_key_by_name(self.get_worker(task.assignee_id).ai_model)
        if model_key and model_key in self.config["models"]:
            quality_factor += self.config["models"][model_key]["quality_score"] * 0.5
        
        # Lower quality models struggle with complex tasks
        complexity_penalty = classification.complexity_score * 0.3
        
        # Deterministic verification passes more reliably
        if classification.deterministic_possible:
            quality_factor += 0.3
        
        success_chance = max(0.1, quality_factor - complexity_penalty)
        
        # Simulated verification - in real system this would run actual tests
        import random
        random.seed(hash(task.id) % 2**32)
        return random.random() < success_chance
    
    def _calculate_cost(self, task: Task, model_info: Dict[str, Any]) -> float:
        """Calculate estimated cost for task execution"""
        model_key = self._get_model_key_by_name(model_info["name"])
        cost_per_token = self.config["models"][model_key]["cost_per_token"]
        
        # Simulate token usage
        prompt_tokens = len(task.prompt.split()) * 1.3
        output_tokens = 500 + (task.priority * 200)
        total_tokens = prompt_tokens + output_tokens
        
        base_cost = total_tokens * cost_per_token
        
        # Add retry cost simulation (local models retry more)
        retry_multiplier = model_info["max_retries"] * 0.1
        
        return round(base_cost * (1 + retry_multiplier), 4)
    
    def _get_model_key_by_name(self, model_name: str) -> Optional[str]:
        """Find the config key for a model given its display name"""
        for key, info in self.config["models"].items():
            if info["name"] == model_name:
                return key
        return None
    
    # ------------------------------------------------------------------
    # Status Reporting
    # ------------------------------------------------------------------
    def get_worker_status(self) -> Dict[str, Any]:
        """Get summary status of all workers"""
        return {
            "total_workers": len(self.employees),
            "idle": sum(1 for e in self.employees.values() if e.status == "idle"),
            "working": sum(1 for e in self.employees.values() if e.status == "working"),
            "completed_tasks": sum(e.tasks_completed for e in self.employees.values()),
            "avg_rating": round(
                sum(e.avg_rating for e in self.employees.values()) / max(len(self.employees), 1), 2
            ) if self.employees else 0
        }
    
    def print_dashboard(self):
        """Print a simple text dashboard of worker status"""
        print("\n" + "=" * 60)
        print("          🏢 AUTO COMPANY - LIVE DASHBOARD            ")
        print("=" * 60)
        print(f"{'이름':<8} | {'역할':<18} | {'레벨':<4} | {'모델':<16} | {'상태':<8} | 완료")
        print("-" * 60)
        for emp in self.employees.values():
            print(f"{emp.name:<8} | {emp.role:<18} | {emp.level:<4} | "
                  f"{emp.ai_model:<16} | {emp.status:<8} | {emp.tasks_completed}")
        print("=" * 60)