rootProject.name = "ai-job-hunter"

include("backend:core", "backend:app")

project(":backend:core").projectDir = file("backend/core")
project(":backend:app").projectDir = file("backend/app")
