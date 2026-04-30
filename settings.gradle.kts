rootProject.name = "ai-job-hunter"

include(":core", ":app")

project(":core").projectDir = file("backend/core")
project(":app").projectDir = file("backend/app")
