plugins {
    id("subsidy.jooq")
}

dependencies {
    testImplementation(testFixtures(project(":libs:common")))
    implementation(project(":libs:llm"))
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)
    implementation(libs.tika.core)
}
