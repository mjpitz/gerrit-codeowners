FROM gerritcodereview/gerrit:3.5.0.1-ubuntu20

COPY ./target/codeowners-1.0-SNAPSHOT.jar /var/gerrit/plugins/codeowners.jar


