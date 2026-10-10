plugins { `kotlin-dsl` }
dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.dependency.management.plugin)
    implementation(libs.jooq.gradle.plugin)
    implementation(libs.liquibase.gradle.plugin)
    implementation(libs.liquibase.core)
}
