"""
Configuration for AutoCompany Local Runner
"""

import os

CONFIG = {
    "ollama_base_url": "http://localhost:11434",
    "openai_api_key": os.getenv("OPENAI_API_KEY"),
    "anthropic_api_key": os.getenv("ANTHROPIC_API_KEY"),
    "models": {
        "llama3_local": {
            "name": "llama3:8b",
            "type": "local",
            "cost_per_token": 0.0,
            "base_model": "ollama",
            "quality_score": 0.72,
            "max_retries": 2
        },
        "gpt4o_mini": {
            "name": "gpt-4o-mini",
            "type": "api",
            "cost_per_token": 0.00015,
            "base_model": "openai",
            "quality_score": 0.88,
            "max_retries": 1
        },
        "gpt4o": {
            "name": "gpt-4o",
            "type": "api",
            "cost_per_token": 0.005,
            "base_model": "openai",
            "quality_score": 0.95,
            "max_retries": 1
        },
        "claude3_5": {
            "name": "claude-3-5-sonnet-20241022",
            "type": "api",
            "cost_per_token": 0.008,
            "base_model": "anthropic",
            "quality_score": 0.97,
            "max_retries": 1
        }
    },
    "worker_docker_images": {
        "developer": "autocompany:developer-dev",
        "planner": "autocompany:planner-dev",
        "designer": "autocompany:designer-dev",
        "technical_writer": "autocompany:writer-dev"
    },
    "task_types": {
        "simple_fix": {"required_quality": 0.85, "max_cost": 0.01, "deterministic": True},
        "feature": {"required_quality": 0.9, "max_cost": 0.05, "deterministic": False},
        "architecture": {"required_quality": 0.95, "max_cost": 0.1, "deterministic": False},
        "doc": {"required_quality": 0.8, "max_cost": 0.005, "deterministic": True}
    }
}