#!/bin/bash

docker-compose up -d
source /home/petar/Fakultet/Semester\ 7/Mining\ massive\ datasets/Homeworks/homework_venv/bin/activate
python3 ./scripts/producer.py
