SHELL=/bin/bash
buildversion = $(shell cat version)

.PHONY: all
all: build

.PHONY: increment
increment:
	echo $$(( $(buildversion) + 1 )) > version

build:
	docker build --force-rm=true -t yonadev/yona-mariadb-liquibase-update:build-$(buildversion) .

