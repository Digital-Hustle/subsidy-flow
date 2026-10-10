plugins {
    id("subsidy.jooq")
}

dependencies {
    testImplementation(testFixtures(project(":libs:common")))
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation(libs.tika.core)
    implementation(libs.pdfbox)
    implementation(libs.imageio.jpeg)
    implementation(libs.imageio.tiff)
    implementation(libs.poi.ooxml)
    implementation(libs.commons.compress)
}
