plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.wire)
}

wire {
    kotlin {}
    root("openim.sdkws.PushMessages")
    root("openim.sdkws.UserSendMsgResp")
}

tasks.withType<JavaCompile>().configureEach {
    enabled = false
}
