#!/usr/bin/env python3
"""
Demo script for AutoCompany Local Runner
Run: python demo.py
"""
import sys
import os

# Add local_runner to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from local_runner.main import main

if __name__ == "__main__":
    main()