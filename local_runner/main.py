"""
AutoCompany Local Runner - Main Entry Point

Demonstrates the full workflow:
1. Setup AI workers
2. Create tasks
3. Execute tasks with Cost-Performance Router
4. Show results with escalation strategy
"""
import sys
import uuid
from datetime import datetime

from .config import CONFIG
from .models import Task
from .runner import LocalRunner


def main():
    print("=" * 60)
    print("     🏢 AUTO COMPANY LOCAL RUNNER v1.0")
    print("=" * 60)
    
    # Initialize runner
    runner = LocalRunner(CONFIG)
    
    # Step 1: Setup AI workers
    print("\n[1] 👥 Setting up AI Workers...")
    workers = runner.setup_workers()
    
    # Step 2: Create tasks
    print("\n[2] 📝 Creating Tasks...")
    dev_id = workers[0].id  # 김개발
    writer_id = workers[2].id  # 박문서
    
    # Simple fix task (should use cheap model)
    task1 = runner.create_task(
        project_id="proj-001",
        assignee_id=dev_id,
        task_type="simple_fix",
        title="Fix null pointer in login handler",
        prompt="There is a null pointer exception when user_id is empty",
        priority=2
    )
    
    # Complex architecture task (may require escalation)
    task2 = runner.create_task(
        project_id="proj-001",
        assignee_id=dev_id,
        task_type="architecture",
        title="Design microservices architecture for payment system",
        prompt="Design a distributed payment processing system with retry, idempotency, and fallback mechanisms",
        priority=1
    )
    
    # Documentation task
    task3 = runner.create_task(
        project_id="proj-001",
        assignee_id=writer_id,
        task_type="doc",
        title="Write API documentation for authentication endpoints",
        prompt="Document all auth endpoints with request/response examples",
        priority=3
    )
    
    # Step 3: Execute tasks with Cost-Performance Router
    print("\n[3] ⚙️ Executing Tasks with Cost-Performance Router...")
    print("\n" + "-" * 50)
    print("Task 1: Simple Fix (should use cheapest model)")
    print("-" * 50)
    result1 = runner.execute_task(task1)
    print(f"   Status: {result1.status} | Cost: ${result1.total_cost:.6f} | Escalations: {result1.escalation_count}")
    
    print("\n" + "-" * 50)
    print("Task 2: Architecture (may require escalation)")
    print("-" * 50)
    result2 = runner.execute_task(task2)
    print(f"   Status: {result2.status} | Cost: ${result2.total_cost:.6f} | Escalations: {result2.escalation_count}")
    
    print("\n" + "-" * 50)
    print("Task 3: Documentation")
    print("-" * 50)
    result3 = runner.execute_task(task3)
    print(f"   Status: {result3.status} | Cost: ${result3.total_cost:.6f} | Escalations: {result3.escalation_count}")
    
    # Step 4: Show final dashboard
    print("\n[4] 📊 Final Dashboard")
    runner.print_dashboard()
    
    # Step 5: Show router history
    print("\n[5] 📈 Router Experiment History")
    print("-" * 60)
    for i, exp in enumerate(runner.router.experiment_history):
        status_icon = "✅" if exp.success else "❌"
        print(f"  {status_icon} [{exp.model_name}] {exp.task_type} | Cost: ${exp.cost:.6f} | Verif: {exp.verification_passed}")
    print("-" * 60)
    
    # Step 6: Summary
    total_cost = sum(t.total_cost for t in runner.tasks.values())
    completed = sum(1 for t in runner.tasks.values() if t.status == "completed")
    failed = sum(1 for t in runner.tasks.values() if t.status == "failed")
    escalated = sum(1 for t in runner.tasks.values() if t.status == "escalated")
    
    print("\n" + "=" * 60)
    print("                     SUMMARY")
    print("=" * 60)
    print(f"  Total Tasks:     {len(runner.tasks)}")
    print(f"  Completed:       {completed}")
    print(f"  Failed:          {failed}")
    print(f"  Escalated:       {escalated}")
    print(f"  Total Cost:      ${total_cost:.6f}")
    print("=" * 60)
    
    return 0


if __name__ == "__main__":
    sys.exit(main())