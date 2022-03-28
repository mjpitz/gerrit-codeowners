load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "gerrit-github-codeowners",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: gerrit-github-codeowners",
        "Gerrit-Module: com.mjpitz.gerrit.plugins.codeowners.Module",
        "Implementation-Title: GitHub CODEOWNERS for Gerrit",
    ],
    resources = glob(["src/main/resources/**/*"]),
)
