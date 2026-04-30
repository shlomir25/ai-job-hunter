rootProject.name = "ai-job-hunter"

include(":core", ":ingestion", ":app")

project(":core").projectDir = file("backend/core")
project(":ingestion").projectDir = file("backend/ingestion")
project(":app").projectDir = file("backend/app")
