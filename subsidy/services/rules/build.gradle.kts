plugins {
    id("subsidy.jooq")
}

dependencies {
    testImplementation(testFixtures(project(":libs:common")))
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)
}
