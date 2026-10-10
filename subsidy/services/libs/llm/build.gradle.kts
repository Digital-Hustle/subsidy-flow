plugins { id("subsidy.library") }
dependencies {
    api("com.fasterxml.jackson.core:jackson-databind")
    implementation(libs.json.schema.validator)
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("io.micrometer:micrometer-core")
    implementation("org.slf4j:slf4j-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.archunit)
}
