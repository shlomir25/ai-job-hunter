rootProject.name = "ai-job-hunter"

include(":core", ":ingestion", ":processing", ":matching", ":app")

project(":core").projectDir = file("backend/core")
project(":ingestion").projectDir = file("backend/ingestion")
project(":processing").projectDir = file("backend/processing")
project(":matching").projectDir = file("backend/matching")
project(":app").projectDir = file("backend/app")
