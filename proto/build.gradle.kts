plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.wire)
}

wire {
    kotlin {}
    root("openim.sdkws.PushMessages")
    root("openim.sdkws.UserSendMsgResp")
    root("openim.sdkws.PullMessageBySeqsReq")
    root("openim.sdkws.PullMessageBySeqsResp")
    root("openim.msg.GetSeqMessageReq")
    root("openim.msg.GetSeqMessageResp")
}

tasks.withType<JavaCompile>().configureEach {
    enabled = false
}
