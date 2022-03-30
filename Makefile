compile:
	mvn package

test:
	mvn test

dev-setup:
	docker compose up -d

sync: compile
	docker cp ./target/codeowners-1.0-SNAPSHOT.jar gerrit-codeowners-gerrit-1:/var/gerrit/plugins
	docker restart gerrit-codeowners-gerrit-1
