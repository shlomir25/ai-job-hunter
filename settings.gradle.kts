rootProject.name = "ai-job-hunter"

include(":core", ":ingestion", ":processing", ":app")

project(":core").projectDir = file("backend/core")
project(":ingestion").projectDir = file("backend/ingestion")
project(":processing").projectDir = file("backend/processing")
project(":app").projectDir = file("backend/app")
