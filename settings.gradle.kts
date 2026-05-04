rootProject.name = "ai-job-hunter"

include(":core", ":ingestion", ":processing", ":matching", ":delivery", ":app")

project(":core").projectDir = file("backend/core")
project(":ingestion").projectDir = file("backend/ingestion")
project(":processing").projectDir = file("backend/processing")
project(":matching").projectDir = file("backend/matching")
project(":delivery").projectDir = file("backend/delivery")
project(":app").projectDir = file("backend/app")
